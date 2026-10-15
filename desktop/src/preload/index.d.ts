import type { ElectronAPI } from '@electron-toolkit/preload'
import type { DesktopApi } from './index'

declare global {
  interface Window {
    electron: ElectronAPI
    api: DesktopApi
  }
}

export {}
