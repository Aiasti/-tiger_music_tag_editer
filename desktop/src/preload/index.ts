import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'
import type {
  PublicSettings,
  SaveSettingsPayload,
  SyncSettingsResult
} from '../main/settings'

/**
 * 暴露给渲染层的受限 API（BIZ-013、S5）。
 *
 * <p>仅提供「选文件 / 选文件夹 / 选播放器」「后端连接配置」与「设置读写、试听」；
 * 渲染层不接触 Node.js、文件系统或原始 IPC，全部经主进程代为执行。
 */
const api = {
  selectAudioFiles: (): Promise<string[]> => ipcRenderer.invoke('dialog:selectAudioFiles'),
  selectFolder: (): Promise<string[]> => ipcRenderer.invoke('dialog:selectFolder'),
  selectExecutable: (): Promise<string> => ipcRenderer.invoke('dialog:selectExecutable'),
  getBackendConfig: (): Promise<{ baseUrl: string; token: string }> =>
    ipcRenderer.invoke('backend:config'),
  getSettings: (): Promise<PublicSettings> => ipcRenderer.invoke('settings:get'),
  saveSettings: (payload: SaveSettingsPayload): Promise<PublicSettings> =>
    ipcRenderer.invoke('settings:save', payload),
  syncOnlineSettings: (): Promise<SyncSettingsResult> =>
    ipcRenderer.invoke('settings:sync-online'),
  playTrack: (filePaths: string[]): Promise<void> =>
    ipcRenderer.invoke('player:play', { filePaths }),
  testPlayer: (payload: {
    filePaths: string[]
    executablePath: string
    argsTemplate: string
    useTrialPlaylist: boolean
  }): Promise<void> => ipcRenderer.invoke('player:test', payload),
  stopPlayer: (): Promise<void> => ipcRenderer.invoke('player:stop'),
  minimizeWindow: (): Promise<void> => ipcRenderer.invoke('window:minimize'),
  toggleMaximizeWindow: (): Promise<void> => ipcRenderer.invoke('window:toggle-maximize'),
  closeWindow: (): Promise<void> => ipcRenderer.invoke('window:close')
}

export type DesktopApi = typeof api

if (process.contextIsolated) {
  try {
    contextBridge.exposeInMainWorld('electron', electronAPI)
    contextBridge.exposeInMainWorld('api', api)
  } catch (error) {
    console.error(error)
  }
} else {
  // @ts-ignore (define in index.d.ts)
  window.electron = electronAPI
  // @ts-ignore (define in index.d.ts)
  window.api = api
}
