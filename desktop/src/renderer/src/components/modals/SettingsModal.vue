<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useSettings } from '../../composables/useSettings'
import { useWorkspace } from '../../composables/useWorkspace'
import { useConfirm } from '../../composables/useConfirm'
import type { LyricsSourceSetting } from '../../../../main/settings'

const emit = defineEmits<{
  (e: 'close'): void
}>()

/** 标签卡顺序与标题对齐 S0.5 原型（BIZ-017 反馈：改为标签卡形式）。 */
type SettingsTab = 'player' | 'online' | 'lyrics'
const activeTab = ref<SettingsTab>('player')

const { settings, loading, saving, settingsError, loadSettings, saveSettings, testPlayer } =
  useSettings()
const { selectedTrack } = useWorkspace()
const { confirm } = useConfirm()

const playerPath = ref('')
const playerArgs = ref('')
const useTrialPlaylist = ref(true)
const musicBrainzUserAgent = ref('')
const acoustidApiKey = ref('')
const clearApiKey = ref(false)
const fpcalcPath = ref('')
const lyricsSources = ref<LyricsSourceSetting[]>([])
const notice = ref<string | null>(null)

const SOURCE_META: Record<string, { name: string; desc: string }> = {
  lrclib: { name: 'LRCLIB', desc: '首版已接入：支持同步 LRC 与纯文本，免鉴权' },
  netease: { name: '网易云音乐 (Netease Music)', desc: '首版未接入，后续版本评估' },
  qq: { name: 'QQ 音乐 (Tencent QQ Music)', desc: '首版未接入，后续版本评估' },
  applemusic: { name: 'Apple Music', desc: '首版未接入，需要开发者令牌' },
  lastfm: { name: 'Last.fm 元数据映射', desc: '首版未接入，需要 API Key' }
}

const sourceMeta = (id: string): { name: string; desc: string } =>
  SOURCE_META[id] ?? { name: id, desc: '未知歌词源' }

const keyConfigured = computed(() => Boolean(settings.value?.acoustidKeyConfigured))
const keyHint = computed(() => {
  if (clearApiKey.value) {
    return '保存后将清除当前 API Key；后端重启后恢复内置默认 Key。'
  }
  if (keyConfigured.value) {
    return '已配置（留空保持不变）。'
  }
  return '未配置；缺少 Key 时在线匹配自动降级为 MusicBrainz 文本检索。'
})

onMounted(async () => {
  await loadSettings()
  const current = settings.value
  if (!current) {
    return
  }
  playerPath.value = current.playerPath
  playerArgs.value = current.playerArgsTemplate
  useTrialPlaylist.value = current.useTrialPlaylist
  musicBrainzUserAgent.value = current.musicBrainzUserAgent
  fpcalcPath.value = current.fpcalcPath
  lyricsSources.value = current.lyricsSources.map((item) => ({ ...item }))
})

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

const runPlayerTest = async () => {
  const track = selectedTrack.value
  if (!track) {
    await confirm({
      title: '试听测试',
      message: '请先在工作区选中一首曲目，再运行试听测试。',
      showCancel: false
    })
    return
  }
  if (!playerPath.value.trim()) {
    await confirm({
      title: '试听测试',
      message: '请先选择外部播放器可执行程序。',
      showCancel: false
    })
    return
  }
  try {
    await testPlayer(playerPath.value.trim(), playerArgs.value, [track.filePath], useTrialPlaylist.value)
  } catch (error) {
    await confirm({
      title: '试听失败',
      message: error instanceof Error ? error.message : String(error),
      showCancel: false
    })
  }
}

const handleSave = async () => {
  notice.value = null
  const result = await saveSettings({
    playerPath: playerPath.value,
    playerArgsTemplate: playerArgs.value,
    useTrialPlaylist: useTrialPlaylist.value,
    musicBrainzUserAgent: musicBrainzUserAgent.value,
    fpcalcPath: fpcalcPath.value,
    lyricsSources: lyricsSources.value.map((item) => ({
      id: item.id,
      enabled: item.enabled,
      priority: item.priority
    })),
    ...(clearApiKey.value
      ? { acoustidApiKey: '' }
      : acoustidApiKey.value.trim()
        ? { acoustidApiKey: acoustidApiKey.value.trim() }
        : {})
  })
  if (!result.saved) {
    const message = result.error ?? '保存失败'
    notice.value = message
    await confirm({ title: '保存失败', message, showCancel: false })
    return
  }
  acoustidApiKey.value = ''
  clearApiKey.value = false
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
      <!-- 标签卡导航（与原型一致） -->
      <div class="settings-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'player' }"
          @click="activeTab = 'player'"
        >
          🎧 外部播放器试听 (FR-9)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'online' }"
          @click="activeTab = 'online'"
        >
          🌐 在线服务与限流 (FR-5 / BIZ-009)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'lyrics' }"
          @click="activeTab = 'lyrics'"
        >
          🎵 可选歌词源配置 (FR-9 / BIZ-012)
        </button>
      </div>

      <!-- Tab 1：外部播放器 -->
      <div v-if="activeTab === 'player'" class="tab-pane">
        <div class="form-group">
          <label class="form-label">外部音频播放器可执行程序路径（如 foobar2000、VLC 等）：</label>
          <div class="inline-row">
            <input v-model="playerPath" type="text" class="form-input" placeholder="C:\Program Files\foobar2000\foobar2000.exe" />
            <button class="btn" @click="browsePlayer">浏览…</button>
          </div>
          <span class="form-hint">
            应用不内置播放器核心；触发试听时通过系统进程拉起该播放器打开当前选中曲目。
          </span>
        </div>

        <div class="form-group">
          <label class="form-label">命令行启动参数模板：</label>
          <input v-model="playerArgs" type="text" class="form-input" />
          <span class="form-hint">
            使用 %filepath% 代表当前曲目路径（会自动处理空格）；支持双引号包裹，例如 "%filepath%"。
            多选试听时占位符会展开为多个带引号的路径。
          </span>
        </div>

        <div class="form-group">
          <label class="source-checkbox">
            <input v-model="useTrialPlaylist" type="checkbox" />
            <span class="source-name">使用「tiger trial」M3U8 播放列表</span>
          </label>
          <span class="form-hint">
            多选试听时先把选中曲目写入固定路径的 tiger trial.m3u8（每次覆盖），再交给播放器打开；
            关闭此项则把多个音频路径直接传给播放器。
          </span>
        </div>

        <div class="inline-row">
          <button class="btn" :disabled="!selectedTrack" @click="runPlayerTest">▶ 运行试听测试</button>
          <span v-if="!selectedTrack" class="form-hint">先在工作区选中一首曲目后可测试。</span>
        </div>
      </div>

      <!-- Tab 2：在线服务与限流 -->
      <div v-else-if="activeTab === 'online'" class="tab-pane">
        <div class="form-group">
          <label class="form-label">MusicBrainz User-Agent 客户端标识（BIZ-009 要求可识别应用与联系方式）：</label>
          <input
            v-model="musicBrainzUserAgent"
            type="text"
            class="form-input"
            placeholder="留空使用后端默认（应用内置 User-Agent）"
          />
          <span class="form-hint">留空时不下发该字段，保留后端环境变量或默认值。</span>
        </div>

        <div class="form-group">
          <label class="form-label">AcoustID 应用 API Key（用于音频指纹查询）：</label>
          <input
            v-model="acoustidApiKey"
            type="password"
            class="form-input"
            placeholder="默认留空使用后端默认（应用内置AcoustID API Key）"
          />
          <div class="inline-row">
            <span class="form-hint">{{ keyHint }}</span>
            <button
              v-if="keyConfigured && !clearApiKey"
              class="btn"
              type="button"
              @click="clearApiKey = true"
            >
              清除已保存 Key
            </button>
            <button
              v-else-if="clearApiKey"
              class="btn"
              type="button"
              @click="clearApiKey = false"
            >
              取消清除
            </button>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">fpcalc 路径（留空使用默认）：</label>
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

        <div class="info-block">
          ⏱️ <strong>强制客户端节流策略（BIZ-009）</strong>
          <ul>
            <li>MusicBrainz：≤ 1.0 次请求/秒，超额将返回 429/503 并可能封禁。</li>
            <li>AcoustID：≤ 3.0 次请求/秒。</li>
            <li>遇到 429/503 时按 Retry-After 指数退避重试。</li>
          </ul>
          <span class="form-hint">
            保存后立即同步到本地服务，对后续检索生效；进行中的检索不受影响。
          </span>
        </div>
      </div>

      <!-- Tab 3：可选歌词源配置 -->
      <div v-else class="tab-pane">
        <span class="form-hint">
          调整批量在线检索时的歌词源启用状态与优先级；靠前的源优先检索，命中后停止。
        </span>
        <div class="sources-list">
          <div
            v-for="source in lyricsSources"
            :key="source.id"
            class="source-item"
            :class="{ enabled: source.enabled }"
          >
            <label class="source-checkbox">
              <input v-model="source.enabled" type="checkbox" :disabled="source.id !== 'lrclib'" />
              <span class="source-name">{{ sourceMeta(source.id).name }}</span>
            </label>
            <span class="source-desc">{{ sourceMeta(source.id).desc }}</span>
            <span class="priority-badge">优先度 #{{ source.priority }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <span v-if="loading" class="form-hint">正在读取设置…</span>
      <span v-else-if="notice" class="form-hint">{{ notice }}</span>
      <span v-else-if="settingsError" class="form-hint form-error">{{ settingsError }}</span>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" :disabled="saving || loading" @click="handleSave">
        {{ saving ? '保存中…' : '保存配置' }}
      </button>
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

/* 标签卡导航：横排 + 选中下划线（对齐原型） */
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

.info-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--info-dim);
  font-size: var(--font-sm);
  color: var(--text);
}
.info-block ul {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.6;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.source-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}
.source-item.enabled {
  border-color: var(--accent);
}
.source-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.source-name {
  font-size: var(--font-sm);
  font-weight: 500;
  color: var(--text-bright);
}
.source-desc {
  flex: 1;
  min-width: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.priority-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-muted);
  background: var(--surface-2);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
</style>
