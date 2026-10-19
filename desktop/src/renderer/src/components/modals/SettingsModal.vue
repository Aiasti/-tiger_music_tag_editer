<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useSettings } from '../../composables/useSettings'
import { useConfirm } from '../../composables/useConfirm'
import { useTheme, type ThemePreference } from '../../composables/useTheme'

const emit = defineEmits<{
  (e: 'close'): void
}>()

/** 设置弹窗只保留「首选项」与「关于」。 */
type SettingsTab = 'preferences' | 'about'
const activeTab = ref<SettingsTab>('preferences')

const { settings, loading, saving, settingsError, loadSettings, saveSettings } = useSettings()
const { confirm } = useConfirm()
const { preference: themePreference, setTheme } = useTheme()

const playerPath = ref('')
const fpcalcPath = ref('')
const appVersion = ref('')
const notice = ref<string | null>(null)

onMounted(async () => {
  appVersion.value = await window.api.getAppVersion()
  await loadSettings()
  const current = settings.value
  if (!current) {
    return
  }
  playerPath.value = current.playerPath
  fpcalcPath.value = current.fpcalcPath
})

/** 主题即时生效并持久化到渲染层本地存储。 */
const handleThemeChange = (event: Event) => {
  setTheme((event.target as HTMLSelectElement).value as ThemePreference)
}

const browsePlayer = async () => {
  try {
    const selected = await window.api.selectExecutable()
    if (selected) {
      playerPath.value = selected
    }
  } catch (error) {
    await confirm({
      title: '选择播放器失败',
      message: error instanceof Error ? error.message : String(error),
      showCancel: false
    })
  }
}

const handleSave = async () => {
  notice.value = null
  const current = settings.value
  const result = await saveSettings({
    playerPath: playerPath.value,
    // 以下字段已从界面移除，保存时沿用现有值，避免被默认值覆盖
    playerArgsTemplate: current?.playerArgsTemplate ?? '"%filepath%"',
    useTrialPlaylist: current?.useTrialPlaylist ?? true,
    musicBrainzUserAgent: current?.musicBrainzUserAgent ?? '',
    fpcalcPath: fpcalcPath.value,
    // 必须转为普通对象：Vue reactive proxy 无法通过 IPC structured clone（BUG-013）
    lyricsSources: (current?.lyricsSources ?? []).map((item) => ({
      id: item.id,
      enabled: item.enabled,
      priority: item.priority
    }))
  })
  if (!result.saved) {
    const message = result.error ?? '保存失败'
    notice.value = message
    await confirm({ title: '保存失败', message, showCancel: false })
    return
  }
  if (!result.synced) {
    await confirm({
      title: '设置已保存',
      message: `本地服务未同步（${result.error ?? '服务未启动'}），下次启动会自动重试。`,
      showCancel: false
    })
  }
  emit('close')
}
</script>

<template>
  <ModalContainer title="⚙️ 系统首选项与配置" max-width="680px" @close="emit('close')">
    <div class="settings-body">
      <!-- 标签卡导航：只保留首选项与关于 -->
      <div class="settings-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'preferences' }"
          @click="activeTab = 'preferences'"
        >
          首选项
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'about' }"
          @click="activeTab = 'about'"
        >
          关于
        </button>
      </div>

      <!-- 首选项：主题、外部播放器路径与 fpcalc 路径 -->
      <div v-if="activeTab === 'preferences'" class="tab-pane">
        <div class="form-group">
          <label class="form-label">主题：</label>
          <select class="form-input" :value="themePreference" @change="handleThemeChange">
            <option value="system">跟随系统</option>
            <option value="dark">Dark</option>
            <option value="light">Light</option>
          </select>
          <span class="form-hint">默认跟随系统；选择后立即生效。</span>
        </div>

        <div class="form-group">
          <label class="form-label">外部播放器路径：</label>
          <div class="inline-row">
            <input
              v-model="playerPath"
              type="text"
              class="form-input"
              placeholder="C:\Program Files\foobar2000\foobar2000.exe"
            />
            <button class="btn" @click="browsePlayer">浏览…</button>
          </div>
          <span class="form-hint">
            触发试听时通过系统进程拉起该播放器打开当前选中曲目。当前版本仅支持 foobar2000
          </span>
        </div>

        <div class="form-group">
          <label class="form-label">fpcalc 路径：</label>
          <input
            v-model="fpcalcPath"
            type="text"
            class="form-input"
            placeholder="留空则使用随包内置或开发本地目录中的 fpcalc"
          />
          <span class="form-hint">
            当前生效路径：<code>{{ settings?.fpcalcEffectivePath ?? '未解析' }}</code>
          </span>
        </div>
      </div>

      <!-- 关于 -->
      <div v-else class="tab-pane about-pane">
        <div class="about-brand">
          <span class="about-icon">🐯</span>
          <div>
            <div class="about-name">Tiger Music Tag Editor</div>
            <div class="about-version">版本 v{{ appVersion || '…' }}</div>
          </div>
        </div>
        <p class="about-desc">
          Windows 桌面端批量音频元数据编辑器：导入本地音频，集中编辑标签，在线匹配元数据、封面与歌词，
          确认后写回文件并按模板重命名归档。
        </p>
        <dl class="about-meta">
          <div>
            <dt>技术栈</dt>
            <dd>Electron + Vue 3 + Spring Boot</dd>
          </div>
          <div>
            <dt>在线数据源</dt>
            <dd>MusicBrainz、AcoustID、LRCLIB、iTunes、lrcapi</dd>
          </div>
        </dl>
      </div>
    </div>

    <template #footer>
      <span v-if="loading" class="form-hint">正在读取设置…</span>
      <span v-else-if="notice" class="form-hint">{{ notice }}</span>
      <span v-else-if="settingsError" class="form-hint form-error">{{ settingsError }}</span>
      <button v-if="activeTab === 'about'" class="btn primary" @click="emit('close')">关闭</button>
      <template v-else>
        <button class="btn" @click="emit('close')">取消</button>
        <button class="btn primary" :disabled="saving || loading" @click="handleSave">
          {{ saving ? '保存中…' : '保存配置' }}
        </button>
      </template>
    </template>
  </ModalContainer>
</template>

<style scoped>
.settings-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 300px;
}

/* 标签卡导航：横排 + 选中下划线 */
.settings-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border);
}
.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  padding: 8px 10px;
  font-family: inherit;
  font-size: var(--font-sm);
  color: var(--text-muted);
  cursor: pointer;
}
.tab-btn:hover {
  color: var(--text-bright);
}
.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-bright);
}
.form-input {
  height: 28px;
  padding: 0 8px;
  background: var(--surface);
  color: var(--text);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: var(--font-sm);
  outline: none;
}
.form-input:focus {
  border-color: var(--accent);
}
.form-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: 1.5;
}
.form-error {
  color: var(--state-failed);
}
.inline-row {
  display: flex;
  gap: 8px;
}
.inline-row .form-input {
  flex: 1;
}

.about-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.about-icon {
  font-size: 32px;
  line-height: 1;
}
.about-name {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-bright);
}
.about-version {
  margin-top: 2px;
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.about-desc {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--text);
  line-height: 1.6;
}
.about-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}
.about-meta div {
  display: flex;
  gap: 12px;
  font-size: var(--font-xs);
}
.about-meta dt {
  flex-shrink: 0;
  width: 72px;
  color: var(--text-muted);
}
.about-meta dd {
  margin: 0;
  color: var(--text);
}
</style>
