import type { DesktopApi } from '../../../preload/index'

/**
 * 渲染层可见的受限桌面能力（由 preload 通过 contextBridge 暴露）。
 *
 * <p>单独声明在渲染层的类型图中，避免与 preload 的同名实现文件互相遮蔽。
 */
declare global {
  interface Window {
    api: DesktopApi
  }
}

export {}
