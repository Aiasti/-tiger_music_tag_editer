import { ref, onMounted } from 'vue'

export type ThemeMode = 'dark' | 'light'
export type ThemePreference = 'system' | ThemeMode

const STORAGE_KEY = 'tiger_theme_preference'

/** 用户选择（可能为跟随系统）；默认跟随系统。 */
const preference = ref<ThemePreference>('system')
/** 实际生效的主题（系统模式下随系统外观变化）。 */
const currentTheme = ref<ThemeMode>('dark')

const systemLightQuery = window.matchMedia('(prefers-color-scheme: light)')
let systemListenerAttached = false

const applyTheme = (theme: ThemeMode) => {
  currentTheme.value = theme
  document.documentElement.setAttribute('data-theme', theme)
  // 原生窗口控件覆盖层跟随主题（Windows：titleBarOverlay）
  void window.api.setTitleBarOverlay?.(theme)
}

const resolvedSystemTheme = (): ThemeMode => (systemLightQuery.matches ? 'light' : 'dark')

const syncSystemTheme = () => {
  if (preference.value === 'system') {
    applyTheme(resolvedSystemTheme())
  }
}

const ensureSystemListener = () => {
  if (systemListenerAttached) {
    return
  }
  systemLightQuery.addEventListener('change', syncSystemTheme)
  systemListenerAttached = true
}

export function useTheme() {
  const setTheme = (theme: ThemePreference) => {
    preference.value = theme
    applyTheme(theme === 'system' ? resolvedSystemTheme() : theme)
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // 忽略存储异常
    }
  }

  const initTheme = () => {
    ensureSystemListener()
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      setTheme(saved === 'dark' || saved === 'light' || saved === 'system' ? saved : 'system')
    } catch {
      setTheme('system')
    }
  }

  onMounted(() => {
    initTheme()
  })

  return {
    theme: currentTheme,
    preference,
    setTheme,
    initTheme
  }
}
