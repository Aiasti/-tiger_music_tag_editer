import { app, shell, BrowserWindow, dialog, ipcMain } from 'electron'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { electronApp, optimizer, is } from '@electron-toolkit/utils'
import {
  loadSettings,
  saveSettings,
  syncOnlineSettings,
  toPublicSettings,
  type SaveSettingsPayload
} from './settings'
import { launchPlayer, sendPlayerCommand } from './player'
import { writeM3u8Playlist } from './playlist'
import { getBackendConfig, startBackend, stopBackend } from './backend'

/** 首版支持的音频扩展名（与后端 TagAccessPort.supports 保持一致）。 */
const AUDIO_EXTENSIONS = ['mp3', 'm4a', 'mp4', 'flac', 'ogg', 'wav']

/** 原生标题栏覆盖层的颜色，与渲染层 `--surface-2` 对齐。 */
const TITLEBAR_OVERLAY = {
  dark: { color: '#21252b', symbolColor: '#abb2bf', height: 32 },
  light: { color: '#f0f0f0', symbolColor: '#383a42', height: 32 }
} as const

/**
 * 拉起播放器：可选先把曲目写入固定 M3U8（tiger trial）再交给播放器。
 *
 * <p>foobar2000 原生命令行不支持创建/清空命名列表；固定文件名 M3U8 是唯一
 * 不依赖第三方组件的实现方式。
 */
async function launchPlayback(
  executablePath: string,
  argsTemplate: string,
  filePaths: string[],
  useTrialPlaylist: boolean
): Promise<void> {
  if (filePaths.length === 0) {
    throw new Error('没有可播放的曲目')
  }
  const missing = filePaths.find((path) => !existsSync(path))
  if (missing) {
    throw new Error(`音频文件不存在：${missing}`)
  }
  if (!useTrialPlaylist) {
    await launchPlayer(executablePath, argsTemplate, filePaths)
    return
  }
  const playlistPath = join(app.getPath('userData'), 'playlists', 'tiger trial.m3u8')
  await writeM3u8Playlist(playlistPath, filePaths)
  await launchPlayer(executablePath, argsTemplate, [playlistPath])
}

/**
 * 注册渲染层可用的受限能力（BIZ-013）。
 *
 * <p>渲染层不能直接访问文件系统，文件选择统一由主进程的 dialog 完成；
 * 后端地址与访问 token 也只在主进程注入，避免渲染层散读环境变量。
 */
function registerIpcHandlers(): void {
  ipcMain.handle('dialog:selectAudioFiles', async () => {
    const result = await dialog.showOpenDialog({
      title: '导入音频文件',
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: '音频文件', extensions: AUDIO_EXTENSIONS }]
    })
    return result.canceled ? [] : result.filePaths
  })

  ipcMain.handle('dialog:selectFolder', async () => {
    const result = await dialog.showOpenDialog({
      title: '导入文件夹',
      properties: ['openDirectory']
    })
    return result.canceled ? [] : result.filePaths
  })

  ipcMain.handle('dialog:selectExecutable', async () => {
    const result = await dialog.showOpenDialog({
      title: '选择外部音频播放器',
      properties: ['openFile'],
      filters:
        process.platform === 'win32'
          ? [{ name: '可执行程序', extensions: ['exe'] }]
          : [{ name: '可执行程序', extensions: ['*'] }]
    })
    return result.canceled || result.filePaths.length === 0 ? '' : result.filePaths[0]
  })

  ipcMain.handle('settings:get', async () => toPublicSettings(await loadSettings()))

  ipcMain.handle('settings:save', async (_event, payload: SaveSettingsPayload) => {
    return saveSettings(payload)
  })

  ipcMain.handle('settings:sync-online', async () => syncOnlineSettings())

  ipcMain.handle('app:version', () => app.getVersion())

  ipcMain.handle('player:play', async (_event, payload: { filePaths: string[] }) => {
    const settings = await loadSettings()
    await launchPlayback(
      settings.playerPath,
      settings.playerArgsTemplate,
      payload.filePaths,
      settings.useTrialPlaylist
    )
  })

  ipcMain.handle('player:stop', async () => {
    const settings = await loadSettings()
    await sendPlayerCommand(settings.playerPath, '/stop')
  })

  ipcMain.handle('window:minimize', (event) => {
    BrowserWindow.fromWebContents(event.sender)?.minimize()
  })

  ipcMain.handle('window:toggle-maximize', (event) => {
    const window = BrowserWindow.fromWebContents(event.sender)
    if (!window) {
      return
    }
    if (window.isMaximized()) {
      window.unmaximize()
    } else {
      window.maximize()
    }
  })

  ipcMain.handle('window:is-maximized', (event) => {
    return BrowserWindow.fromWebContents(event.sender)?.isMaximized() ?? false
  })

  ipcMain.handle('window:set-titlebar-overlay', (event, theme: 'dark' | 'light') => {
    const window = BrowserWindow.fromWebContents(event.sender)
    window?.setTitleBarOverlay(TITLEBAR_OVERLAY[theme] ?? TITLEBAR_OVERLAY.dark)
  })

  ipcMain.handle('window:close', (event) => {
    BrowserWindow.fromWebContents(event.sender)?.close()
  })

  ipcMain.handle('backend:config', () => getBackendConfig())
}

function createWindow(): void {
  const mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    show: false,
    icon: is.dev
      ? join(__dirname, '../../resources/icon.ico')
      : join(process.resourcesPath, 'icon.ico'),
    titleBarStyle: 'hidden',
    titleBarOverlay: TITLEBAR_OVERLAY.dark,
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow.show()
  })

  const notifyMaximized = () => {
    mainWindow.webContents.send('window:maximized-changed', mainWindow.isMaximized())
  }
  mainWindow.on('maximize', notifyMaximized)
  mainWindow.on('unmaximize', notifyMaximized)

  mainWindow.webContents.setWindowOpenHandler((details) => {
    void shell.openExternal(details.url)
    return { action: 'deny' }
  })

  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    void mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    void mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

const hasSingleInstanceLock = app.requestSingleInstanceLock()

if (!hasSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    const window = BrowserWindow.getAllWindows()[0]
    if (window) {
      if (window.isMinimized()) {
        window.restore()
      }
      window.focus()
    }
  })

  void app.whenReady().then(async () => {
    electronApp.setAppUserModelId('com.aiasti.tiger')

    registerIpcHandlers()

    app.on('browser-window-created', (_, window) => {
      optimizer.watchWindowShortcuts(window)
    })

    try {
      await startBackend()
    } catch (error) {
      dialog.showErrorBox(
        '后端服务启动失败',
        error instanceof Error ? error.message : String(error)
      )
      app.quit()
      return
    }

    createWindow()

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow()
      }
    })
  })

  app.on('before-quit', () => {
    stopBackend()
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit()
    }
  })
}
