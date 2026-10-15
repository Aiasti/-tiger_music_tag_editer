import { ref, onMounted } from 'vue'

export type ThemeMode = 'dark' | 'light'

const currentTheme = ref<ThemeMode>('dark')

export function useTheme() {
  const setTheme = (theme: ThemeMode) => {
    currentTheme.value = theme
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem('tiger_theme', theme)
    } catch {
      // 忽略存储异常
    }
  }

  const toggleTheme = () => {
    setTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
  }

  const initTheme = () => {
    try {
      const saved = localStorage.getItem('tiger_theme') as ThemeMode | null
      if (saved === 'dark' || saved === 'light') {
        setTheme(saved)
      } else {
        setTheme('dark')
      }
    } catch {
      setTheme('dark')
    }
  }

  onMounted(() => {
    initTheme()
  })

  return {
    theme: currentTheme,
    setTheme,
    toggleTheme,
    initTheme
  }
}
