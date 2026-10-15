<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { useModals } from '../../composables/useModals'
import type { LyricsCandidateItem } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const SKIP_ID = 'skip'

const { openModal } = useModals()
const { operationScope, selectedTracks, applyLyricsToTrack, batchApplyLyrics } = useWorkspace()

/** 批量歌词源（启停 + 优先级展示），与设置弹窗的歌词源配置保持一致（BIZ-012） */
const sources = ref([
  { key: 'netease', label: '网易云音乐 (Netease)', latency: '28ms', enabled: true },
  { key: 'qqmusic', label: 'QQ 音乐 (QQMusic)', latency: '42ms', enabled: true },
  { key: 'apple', label: 'Apple Music', latency: '65ms', enabled: true },
  { key: 'lastfm', label: 'Last.fm (纯文本)', latency: '110ms', enabled: false }
])

const searchKeyword = ref('')
const diffMode = ref<'split' | 'candidate'>('split')
const selectedCandidateId = ref<string>('netease')
/** 已执行过检索的曲目 id（用于「待匹配曲目状态」的检索态表达） */
const searchedIds = ref<number[]>([])

/** 批量作用范围：当前选区，未选中则全部可编辑曲目（BIZ-015 / BIZ-019） */
const scopeTracks = computed(() => operationScope.value)
const scopeIsSelection = computed(() => selectedTracks.value.length > 0)

const activeTrackId = ref<number | null>(null)

watch(
  scopeTracks,
  (list) => {
    if (list.length === 0) {
      activeTrackId.value = null
      return
    }
    if (activeTrackId.value === null || !list.some((t) => t.id === activeTrackId.value)) {
      activeTrackId.value = list[0].id
    }
  },
  { immediate: true }
)

const activeTrack = computed(
  () => scopeTracks.value.find((t) => t.id === activeTrackId.value) || scopeTracks.value[0] || null
)
const activeTitle = computed(
  () => activeTrack.value?.tags.title || activeTrack.value?.filename || '当前曲目'
)
const activeArtist = computed(() => activeTrack.value?.tags.artist || '未知艺术家')
const currentLyrics = computed(() => activeTrack.value?.tags.lyrics || '')

const buildPreview = (header: string, lines: string[]): string =>
  [`[00:00.00]${header}`, ...lines].join('\n')

/** 多源歌词候选（当前为原型模拟数据，后端多源接入属 S4 范围） */
const candidates = computed<LyricsCandidateItem[]>(() => {
  const header = `${activeArtist.value} - ${activeTitle.value}`
  return [
    {
      id: 'netease',
      source: '网易云音乐 (Netease)',
      title: `${activeTitle.value} (Live 2026)`,
      artist: activeArtist.value,
      album: 'Live Album',
      duration: '07:12',
      hasTimeline: true,
      confidence: '98%',
      preview: buildPreview(header, [
        '[00:15.20]The spotlight turns on the stage tonight',
        '[00:19.80]Feel the heartbeat, feel the burning light',
        '[00:24.40]Electric roar, echoing through the hall',
        '[00:29.10]Together we stand, we will never fall'
      ])
    },
    {
      id: 'qqmusic',
      source: 'QQ 音乐 (QQMusic)',
      title: activeTitle.value,
      artist: activeArtist.value,
      album: 'Live Album',
      duration: '07:05',
      hasTimeline: true,
      confidence: '92%',
      preview: buildPreview(header, [
        '[00:18.00]The spotlight turns on the stage tonight',
        '[00:22.50]Feel the heartbeat, feel the burning light',
        '[00:27.10]Electric roar, echoing through the hall'
      ])
    },
    {
      id: 'apple',
      source: 'Apple Music',
      title: `${activeTitle.value} (Live)`,
      artist: activeArtist.value,
      album: 'Live Album',
      duration: '07:08',
      hasTimeline: true,
      confidence: '88%',
      preview: buildPreview(header, [
        '[00:16.40]The spotlight turns on the stage tonight',
        '[00:21.00]Feel the heartbeat, feel the burning light'
      ])
    },
    {
      id: SKIP_ID,
      source: '跳过',
      title: '不应用（跳过此曲目）',
      artist: '—',
      album: '—',
      duration: '—',
      hasTimeline: false,
      confidence: '—',
      preview: ''
    }
  ]
})

/** 候选数量只统计真实在线候选，不含「不应用」（BIZ-012） */
const candidateCount = computed(
  () => candidates.value.filter((c) => c.id !== SKIP_ID).length
)

const selectedCandidate = computed(() =>
  candidates.value.find((c) => c.id === selectedCandidateId.value)
)
const isSkipSelected = computed(() => selectedCandidateId.value === SKIP_ID)

/** 曲目在多源检索后的状态表达（原型模拟数据，后端接入前保持占位） */
const trackStatusText = (trackId: number): string => {
  if (!searchedIds.value.includes(trackId)) {
    return '待检索'
  }
  const track = scopeTracks.value.find((t) => t.id === trackId)
  return track?.tags.lyrics ? '✓ 命中多源候选' : `✓ 命中 ${candidateCount.value} 个源`
}

const switchTrack = (trackId: number) => {
  activeTrackId.value = trackId
}

const searchCurrent = () => {
  if (activeTrackId.value !== null && !searchedIds.value.includes(activeTrackId.value)) {
    searchedIds.value = [...searchedIds.value, activeTrackId.value]
  }
}

const searchBatch = () => {
  searchedIds.value = scopeTracks.value.map((t) => t.id)
}

const handleApplySingle = () => {
  if (isSkipSelected.value) {
    emit('close')
    return
  }
  if (activeTrackId.value !== null && selectedCandidate.value) {
    applyLyricsToTrack(activeTrackId.value, selectedCandidate.value.preview)
  }
  emit('close')
}

/** 一键批量应用全部高匹配歌词：装配至当前选区（未选则全部），仅进内存编辑态 */
const handleApplyBatch = () => {
  if (isSkipSelected.value) {
    emit('close')
    return
  }
  const lyricsText = selectedCandidate.value?.preview
  if (lyricsText !== undefined) {
    batchApplyLyrics(lyricsText)
  }
  emit('close')
}
</script>

<template>
  <ModalContainer
    title="🎵 批量在线歌词搜索与匹配 (FR-6 / BIZ-012)"
    max-width="980px"
    @close="emit('close')"
  >
    <div class="lyrics-batch-modal">
      <!-- 作用范围提示 -->
      <div class="scope-strip">
        <span>
          作用于
          <strong>{{ scopeIsSelection ? '当前选区' : '工作区全部可编辑曲目' }}</strong>
          · 共 <strong>{{ scopeTracks.length }}</strong> 首
        </span>
        <span class="scope-tip">批量应用仅装配至内存编辑态，需点击「💾 确认写回」才落盘</span>
      </div>

      <!-- 歌词源启停与优先级 -->
      <div class="sources-bar">
        <div class="sources-left">
          <strong class="sources-label">歌词检索来源：</strong>
          <div class="source-pill-group">
            <label
              v-for="src in sources"
              :key="src.key"
              class="source-pill"
              :class="{ active: src.enabled }"
            >
              <input v-model="src.enabled" type="checkbox" />
              <span class="source-dot"></span>
              <span>{{ src.label }}</span>
              <span class="source-latency">{{ src.latency }}</span>
            </label>
          </div>
        </div>
        <button
          class="btn ghost sm-btn"
          title="前往设置面板调整歌词源检索顺序与超时"
          @click="openModal('settings')"
        >
          ⚙️ 调整优先级与超时
        </button>
      </div>

      <!-- 搜索控制栏 -->
      <div class="search-row">
        <input
          v-model="searchKeyword"
          type="text"
          class="input search-kw"
          :placeholder="`输入歌曲名、艺术家或专辑搜索…（当前：${activeArtist} - ${activeTitle}）`"
        />
        <button class="btn primary" @click="searchCurrent">🔍 检索当前曲目</button>
        <button class="btn" @click="searchBatch">⚡ 批量检索全部 {{ scopeTracks.length }} 首</button>
      </div>

      <!-- 左右分栏：曲目队列与候选 / 歌词对照 -->
      <div class="batch-grid">
        <div class="left-pane">
          <div class="pane-block">
            <div class="pane-label">
              <span>待匹配曲目状态 (点击切换曲目)：</span>
              <span class="pane-summary">共 {{ scopeTracks.length }} 首</span>
            </div>
            <div class="track-list">
              <div
                v-for="track in scopeTracks"
                :key="track.id"
                class="track-row"
                :class="{ active: track.id === activeTrackId }"
                @click="switchTrack(track.id)"
              >
                <span class="track-name">#{{ track.id }} {{ track.tags.title || track.filename }}</span>
                <span
                  class="track-status"
                  :class="{
                    'is-matched': searchedIds.includes(track.id),
                    'is-idle': !searchedIds.includes(track.id)
                  }"
                >
                  {{ trackStatusText(track.id) }}
                </span>
              </div>
              <div v-if="scopeTracks.length === 0" class="empty-hint">工作区暂无可操作曲目</div>
            </div>
          </div>

          <div class="pane-block">
            <div class="pane-label">
              <span>当前曲目多源歌词候选：</span>
              <span class="pane-summary">按来源优先级排序</span>
            </div>
            <div class="candidate-list">
              <div
                v-for="cand in candidates"
                :key="cand.id"
                class="candidate-card"
                :class="{
                  selected: selectedCandidateId === cand.id,
                  'skip-card': cand.id === SKIP_ID
                }"
                @click="selectedCandidateId = cand.id"
              >
                <template v-if="cand.id === SKIP_ID">
                  <div class="cand-top">
                    <span class="cand-source skip">🚫 {{ cand.title }}</span>
                    <span class="cand-skip-tag">跳过装配</span>
                  </div>
                  <div class="cand-sub">保持本地现有歌词不变，不将在线候选歌词装配至本曲目</div>
                </template>
                <template v-else>
                  <div class="cand-top">
                    <span class="cand-source">{{ cand.source }}</span>
                    <span class="cand-confidence">{{ cand.confidence }} 匹配</span>
                  </div>
                  <div class="cand-title">{{ cand.title }}</div>
                  <div class="cand-sub">{{ cand.artist }} · {{ cand.album }}</div>
                  <div class="cand-tags">
                    <span class="tag" :class="{ green: cand.hasTimeline }">
                      {{ cand.hasTimeline ? '⏱️ LRC 时间轴' : '纯文本' }}
                    </span>
                    <span class="tag">{{ cand.preview.split('\n').length }} 行</span>
                  </div>
                </template>
              </div>
            </div>
            <div class="candidate-count">候选歌词（共 {{ candidateCount }} 条候选）</div>
          </div>
        </div>

        <div class="right-pane">
          <div class="pane-label">
            <span>歌词对照与时间轴校验：</span>
            <div class="diff-mode-group">
              <button
                class="btn ghost sm-btn"
                :class="{ active: diffMode === 'split' }"
                @click="diffMode = 'split'"
              >
                左右对比
              </button>
              <button
                class="btn ghost sm-btn"
                :class="{ active: diffMode === 'candidate' }"
                @click="diffMode = 'candidate'"
              >
                仅看候选
              </button>
            </div>
          </div>

          <div v-if="isSkipSelected" class="skip-placeholder">
            <div class="skip-big">🚫</div>
            <div class="skip-headline">已选择「不应用（跳过此曲目）」</div>
            <p class="skip-sub">
              应用时将跳过该曲目，完整保留其当前本地内嵌歌词，不打脏标记。
            </p>
          </div>

          <div v-else class="diff-viewer" :class="{ 'candidate-only': diffMode === 'candidate' }">
            <div v-if="diffMode === 'split'" class="diff-col local">
              <div class="diff-col-header">
                <span>📁 当前曲目本地歌词</span>
                <span class="diff-badge">{{ currentLyrics ? `${currentLyrics.split('\n').length} 行` : '无内嵌歌词' }}</span>
              </div>
              <div class="diff-content">
                <pre v-if="currentLyrics">{{ currentLyrics }}</pre>
                <div v-else class="empty-tip">（当前音轨文件无内嵌歌词）</div>
              </div>
            </div>

            <div class="diff-col candidate">
              <div class="diff-col-header">
                <span class="accent">✨ 候选 {{ selectedCandidate?.source }} 歌词</span>
                <span class="diff-badge ok">{{ selectedCandidate?.preview.split('\n').length || 0 }} 行</span>
              </div>
              <div class="diff-content">
                <pre>{{ selectedCandidate?.preview }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="persist-alert">
        🛡️ <strong>持久化隔离 (FR-4 / FR-6 / BIZ-012)</strong>：应用在线候选歌词仅暂存至内存编辑态并标记为「已编辑」，绝不直接写入磁盘；必须在主工具栏点击「💾 确认写回」后才最终落盘。
      </div>
    </div>

    <template #footer>
      <div class="modal-footer-between">
        <span class="footer-tip">
          当前选定候选：<strong>{{ selectedCandidate?.source }}</strong>（可点击左侧卡片更换）
        </span>
        <div class="footer-btn-group">
          <button class="btn" @click="emit('close')">取消</button>
          <button class="btn" :disabled="isSkipSelected" @click="handleApplySingle">
            应用至当前曲目 (待写回)
          </button>
          <button class="btn primary" :disabled="isSkipSelected" @click="handleApplyBatch">
            ✨ 一键批量应用全部高匹配歌词 (≥85%)
          </button>
        </div>
      </div>
    </template>
  </ModalContainer>
</template>

<style scoped>
.lyrics-batch-modal {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.scope-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
}
.scope-strip strong {
  color: var(--text-bright);
}
.scope-tip {
  color: var(--text-muted);
}

.sources-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
}
.sources-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.sources-label {
  font-size: var(--font-xs);
  color: var(--text-bright);
}
.source-pill-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.source-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: var(--bg-surface);
  font-size: var(--font-xs);
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
}
.source-pill.active {
  border-color: var(--accent);
  color: var(--text-main);
}
.source-pill input {
  display: none;
}
.source-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--border-strong);
}
.source-pill.active .source-dot {
  background: var(--success);
}
.source-latency {
  color: var(--text-faint);
  font-size: 10px;
  font-family: var(--font-mono);
}

.sm-btn {
  height: 24px;
  padding: 0 9px;
  font-size: 11px;
}
.sm-btn.active {
  color: var(--accent);
  border-color: var(--accent);
}

.search-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
}
.search-kw {
  flex: 1;
}

.batch-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--space-md);
  height: 420px;
}

.left-pane {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  min-height: 0;
}
.pane-block {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.pane-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.pane-summary {
  font-weight: 400;
  color: var(--text-faint);
}

.track-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
}
.track-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: var(--font-xs);
  color: var(--text-main);
  cursor: pointer;
  border-bottom: 1px solid var(--border-subtle);
}
.track-row:last-child {
  border-bottom: none;
}
.track-row:hover {
  background: var(--bg-hover);
}
.track-row.active {
  background: var(--bg-active);
  box-shadow: inset 2px 0 0 var(--accent);
}
.track-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.track-status.is-matched {
  color: var(--state-matched);
  white-space: nowrap;
}
.track-status.is-idle {
  color: var(--text-muted);
  white-space: nowrap;
}
.empty-hint {
  padding: var(--space-md);
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-xs);
}

.candidate-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 4px;
}
.candidate-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  background: var(--bg-surface);
  cursor: pointer;
  transition: all 0.12s ease;
}
.candidate-card:hover {
  background: var(--bg-hover);
  border-color: var(--border-strong);
}
.candidate-card.selected {
  border-color: var(--accent);
  background: var(--bg-active);
}
.candidate-card.skip-card {
  border-style: dashed;
}
.candidate-card.skip-card.selected {
  border-color: var(--warning);
  background: var(--warning-dim);
}
.cand-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
}
.cand-source {
  font-weight: 600;
  color: var(--accent);
}
.cand-source.skip {
  color: var(--warning);
}
.cand-skip-tag {
  color: var(--text-muted);
  font-size: 11px;
}
.cand-confidence {
  color: var(--success);
  font-family: var(--font-mono);
}
.cand-title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-main);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cand-sub {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cand-tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
.tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--bg-hover);
  color: var(--text-muted);
}
.tag.green {
  color: var(--success);
}
.candidate-count {
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--text-faint);
  text-align: right;
}

.right-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.diff-mode-group {
  display: flex;
  gap: 6px;
}

.diff-viewer {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-xs);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  overflow: hidden;
}
.diff-viewer.candidate-only {
  grid-template-columns: 1fr;
}
.diff-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.diff-col.local {
  border-right: 1px solid var(--border-subtle);
}
.diff-col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--font-xs);
  font-weight: 600;
  color: var(--text-muted);
}
.diff-col-header .accent {
  color: var(--accent);
}
.diff-badge {
  font-size: 10px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}
.diff-badge.ok {
  color: var(--success);
}
.diff-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px;
  font-family: var(--font-mono);
  font-size: var(--font-xs);
  line-height: 1.6;
}
.diff-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-main);
}
.empty-tip {
  color: var(--text-muted);
  font-style: italic;
  text-align: center;
  padding: var(--space-md);
}

.skip-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--warning);
  border-radius: var(--radius-sm);
  background: var(--warning-dim);
  padding: var(--space-xl);
  text-align: center;
}
.skip-big {
  font-size: 34px;
  margin-bottom: 8px;
}
.skip-headline {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}
.skip-sub {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--text-muted);
  max-width: 420px;
  line-height: 1.5;
}

.persist-alert {
  font-size: 11.5px;
  color: var(--text-muted);
  background: var(--info-dim);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
  line-height: 1.5;
}
.persist-alert strong {
  color: var(--text-main);
}

.modal-footer-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: var(--space-md);
}
.footer-tip {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.footer-tip strong {
  color: var(--accent);
}
.footer-btn-group {
  display: flex;
  gap: var(--space-xs);
}
</style>
