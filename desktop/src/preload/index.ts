import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'

/**
 * 暴露给渲染层的受限 API（BIZ-013）。
 *
 * <p>仅提供「选文件 / 选文件夹」与「后端连接配置」；渲染层不接触 Node.js、
 * 文件系统或原始 IPC，全部经主进程代为执行。
 */
const api = {
  selectAudioFiles: (): Promise<string[]> => ipcRenderer.invoke('dialog:selectAudioFiles'),
  selectFolder: (): Promise<string[]> => ipcRenderer.invoke('dialog:selectFolder'),
  getBackendConfig: (): Promise<{ baseUrl: string; token: string; resetWorkspace: boolean }> =>
    ipcRenderer.invoke('backend:config')
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
