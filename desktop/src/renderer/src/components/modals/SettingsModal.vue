<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import appIcon from '../../assets/tiger-icon.png'
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

/** 关于页展示的在线数据源；地址取官方项目页或 API 文档。 */
const dataSources = [
  {
    name: 'MusicBrainz',
    usage: '元数据',
    owner: 'MetaBrainz Foundation',
    url: 'https://musicbrainz.org/doc/MusicBrainz_API'
  },
  {
    name: 'AcoustID',
    usage: '音频指纹',
    owner: 'AcoustID 开源项目（Lukáš Lalinský 发起）',
    url: 'https://acoustid.org/webservice'
  },
  {
    name: 'LRCLIB',
    usage: '歌词',
    owner: 'LRCLIB（tranxuanthang 维护）',
    url: 'https://lrclib.net/docs'
  },
  {
    name: 'iTunes Search API',
    usage: '封面',
    owner: 'Apple Inc.',
    url: 'https://performance-partners.apple.com/search-api'
  },
  {
    name: 'LrcAPI',
    usage: '歌词 / 封面',
    owner: 'HisAtri（LrcAPI 开源项目）',
    url: 'https://github.com/HisAtri/LrcApi'
  }
]

const mitLicense = `Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`

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
          <img class="about-icon" :src="appIcon" alt="" />
          <div>
            <div class="about-name">Tiger Music Tag Editor</div>
            <div class="about-version">版本 v{{ appVersion || '…' }}</div>
          </div>
        </div>
        <p class="about-desc">
          Windows 桌面端 Music Tag 编辑器：主要功能为批量编辑音频元数据，负责封面、元数据、歌词的多源在线匹配。
        </p>

        <section class="about-section">
          <h3 class="about-section-title">在线数据源</h3>
          <ul class="source-list">
            <li v-for="source in dataSources" :key="source.name" class="source-item">
              <div class="source-head">
                <span class="source-name">{{ source.name }}</span>
                <span class="source-usage">{{ source.usage }}</span>
              </div>
              <div class="source-owner">主体：{{ source.owner }}</div>
              <a
                class="source-link"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
              >
                {{ source.url }}
              </a>
            </li>
          </ul>
        </section>

        <section class="about-section">
          <h3 class="about-section-title">MIT License</h3>
          <p class="license-copyright">Copyright (c) 2026 Boyd Lee</p>
          <pre class="license-text">{{ mitLicense }}</pre>
        </section>
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
  /* 固定高度：切换「首选项 / 关于」时模态窗尺寸不变 */
  height: 560px;
  max-height: calc(88vh - 92px);
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
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
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
  width: 40px;
  height: 40px;
  display: block;
  object-fit: contain;
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
.about-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.about-section-title {
  margin: 0;
  font-size: var(--font-xs);
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}
.source-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.source-item {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}
.source-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.source-name {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-bright);
}
.source-usage {
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 10px;
  color: var(--text-muted);
}
.source-owner {
  margin-top: 2px;
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.source-link {
  display: inline-block;
  margin-top: 2px;
  font-size: var(--font-xs);
  color: var(--accent);
  text-decoration: none;
  word-break: break-all;
}
.source-link:hover {
  text-decoration: underline;
}
.license-copyright {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.license-text {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
