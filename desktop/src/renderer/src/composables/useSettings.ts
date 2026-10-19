import { computed, ref } from 'vue'
import type { PublicSettings, SaveSettingsPayload, SyncSettingsResult } from '../../../main/settings'

/**
 * 首选项与外部播放器状态（S5、BIZ-035）。
 *
 * <p>设置由 Electron 主进程持久化；渲染层只持有脱敏后的公开视图。
 */
const settings = ref<PublicSettings | null>(null)
const loading = ref(false)
const saving = ref(false)
const settingsError = ref<string | null>(null)
let loadingPromise: Promise<void> | null = null

export function useSettings() {
  const playerConfigured = computed(() => Boolean(settings.value?.playerPath?.trim()))

  const loadSettings = async (): Promise<void> => {
    if (loadingPromise) {
      return loadingPromise
    }
    loading.value = true
    settingsError.value = null
    loadingPromise = (async () => {
      try {
        settings.value = await window.api.getSettings()
      } catch (error) {
        settingsError.value = error instanceof Error ? error.message : String(error)
      } finally {
        loading.value = false
        loadingPromise = null
      }
    })()
    return loadingPromise
  }

  const saveSettings = async (
    payload: SaveSettingsPayload
  ): Promise<SyncSettingsResult & { saved: boolean }> => {
    saving.value = true
    settingsError.value = null
    try {
      settings.value = await window.api.saveSettings(payload)
      const sync = await window.api.syncOnlineSettings()
      return { saved: true, ...sync }
    } catch (error) {
      settingsError.value = error instanceof Error ? error.message : String(error)
      return {
        saved: false,
        synced: false,
        error: settingsError.value ?? undefined
      }
    } finally {
      saving.value = false
    }
  }

  /** 应用启动时把本地设置下发给后端；后端未就绪时静默失败，不阻断工作区。 */
  const syncOnlineSettings = async (): Promise<SyncSettingsResult> => {
    try {
      return await window.api.syncOnlineSettings()
    } catch (error) {
      return { synced: false, error: error instanceof Error ? error.message : String(error) }
    }
  }

  const playTrack = async (filePaths: string[]): Promise<void> => {
    await window.api.playTrack(filePaths)
  }

  /** 使用设置弹窗中尚未保存的播放器路径与模板做试听测试。 */
  const testPlayer = async (
    executablePath: string,
    argsTemplate: string,
    filePaths: string[],
    useTrialPlaylist: boolean
  ): Promise<void> => {
    await window.api.testPlayer({ executablePath, argsTemplate, filePaths, useTrialPlaylist })
  }

  /** 向已运行的 foobar2000 发送 /stop（停止播放）。 */
  const stopPlayer = async (): Promise<void> => {
    await window.api.stopPlayer()
  }

  return {
    settings,
    loading,
    saving,
    settingsError,
    playerConfigured,
    loadSettings,
    saveSettings,
    syncOnlineSettings,
    playTrack,
    testPlayer,
    stopPlayer
  }
}
