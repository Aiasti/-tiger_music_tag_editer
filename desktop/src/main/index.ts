import { app, shell, BrowserWindow, dialog, ipcMain } from 'electron'
import { join } from 'node:path'
import { electronApp, optimizer, is } from '@electron-toolkit/utils'

/** 首版支持的音频扩展名（与后端 TagAccessPort.supports 保持一致）。 */
const AUDIO_EXTENSIONS = ['mp3', 'm4a', 'mp4', 'flac', 'ogg', 'wav']

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

  ipcMain.handle('backend:config', () => ({
    baseUrl: process.env['TIGER_BACKEND_URL'] ?? 'http://127.0.0.1:8080',
    token: process.env['TIGER_APP_TOKEN'] ?? 'dev-local-token'
  }))
}

function createWindow(): void {
  const mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow.show()
  })

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

void app.whenReady().then(() => {
  electronApp.setAppUserModelId('com.aiasti.tiger')

  registerIpcHandlers()

  app.on('browser-window-created', (_, window) => {
    optimizer.watchWindowShortcuts(window)
  })

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
