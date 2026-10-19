import { contextBridge, ipcRenderer } from 'electron'
import type { IpcRendererEvent } from 'electron'
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
  getAppVersion: (): Promise<string> => ipcRenderer.invoke('app:version'),
  saveSettings: (payload: SaveSettingsPayload): Promise<PublicSettings> =>
    ipcRenderer.invoke('settings:save', payload),
  syncOnlineSettings: (): Promise<SyncSettingsResult> =>
    ipcRenderer.invoke('settings:sync-online'),
  playTrack: (filePaths: string[]): Promise<void> =>
    ipcRenderer.invoke('player:play', { filePaths }),
  stopPlayer: (): Promise<void> => ipcRenderer.invoke('player:stop'),
  minimizeWindow: (): Promise<void> => ipcRenderer.invoke('window:minimize'),
  toggleMaximizeWindow: (): Promise<void> => ipcRenderer.invoke('window:toggle-maximize'),
  isWindowMaximized: (): Promise<boolean> => ipcRenderer.invoke('window:is-maximized'),
  setTitleBarOverlay: (theme: 'dark' | 'light'): Promise<void> =>
    ipcRenderer.invoke('window:set-titlebar-overlay', theme),
  onWindowMaximized: (callback: (maximized: boolean) => void): (() => void) => {
    const listener = (_event: IpcRendererEvent, maximized: boolean) => callback(maximized)
    ipcRenderer.on('window:maximized-changed', listener)
    return () => ipcRenderer.removeListener('window:maximized-changed', listener)
  },
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
