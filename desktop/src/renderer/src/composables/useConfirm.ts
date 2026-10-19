import { ref } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 仅显示一个「确定」按钮（用于纯提示）。 */
  showCancel?: boolean
}

const confirmState = ref<ConfirmOptions | null>(null)
let resolver: ((value: boolean) => void) | null = null

/**
 * 应用内确认框（与 UI 风格一致）。
 *
 * <p>替代原生 `confirm`/`alert`：按钮位于右下角，顺序为「取消 / 确定」，确定为蓝色主按钮。
 */
export function useConfirm() {
  const confirm = (options: ConfirmOptions): Promise<boolean> => {
    // 前一个确认框未结束时按取消处理，避免 Promise 悬挂
    resolver?.(false)
    confirmState.value = { ...options }
    return new Promise<boolean>((resolve) => {
      resolver = resolve
    })
  }

  const settle = (value: boolean) => {
    const done = resolver
    resolver = null
    confirmState.value = null
    done?.(value)
  }

  const accept = () => settle(true)
  const cancel = () => settle(false)

  return {
    confirmState,
    confirm,
    accept,
    cancel
  }
}
