<script setup lang="ts">
import { ref, computed } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import type { LyricsCandidateItem } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, applyLyricsToTrack, batchApplyLyrics } = useWorkspace()

const activeSource = ref<'all' | 'netease' | 'qq' | 'lrcdb' | 'kugou'>('all')
const searchKeyword = ref('')
const selectedCandidateId = ref<string>('c1')

const candidates = ref<LyricsCandidateItem[]>([
  {
    id: 'c1',
    source: '网易云音乐',
    title: 'Hotel California (Live MTV 1994)',
    artist: 'Eagles',
    album: 'Hell Freezes Over',
    duration: '07:12',
    hasTimeline: true,
    confidence: '98%',
    preview: `[00:00.00]Hotel California (Live) - Eagles\n[00:30.25]On a dark desert highway, cool wind in my hair\n[00:37.40]Warm smell of colitas, rising up through the air\n[00:44.80]Up ahead in the distance, I saw a shimmering light\n[00:51.60]My head grew heavy and my sight grew dim\n[00:55.20]I had to stop for the night`
  },
  {
    id: 'c2',
    source: 'QQ音乐',
    title: 'Hotel California (2013 Remaster)',
    artist: 'Eagles',
    album: 'Hotel California',
    duration: '06:30',
    hasTimeline: true,
    confidence: '91%',
    preview: `[00:00.00]Hotel California - Eagles\n[00:20.10]On a dark desert highway, cool wind in my hair\n[00:26.50]Warm smell of colitas, rising up through the air\n[00:33.20]Up ahead in the distance, I saw a shimmering light`
  },
  {
    id: 'c3',
    source: 'LRCDB',
    title: 'Hotel California (Acoustic)',
    artist: 'Eagles',
    album: 'Greatest Hits',
    duration: '06:45',
    hasTimeline: true,
    confidence: '82%',
    preview: `[00:00.00]Hotel California\n[00:15.00](Intro Instrumental)\n[00:32.00]On a dark desert highway`
  },
  {
    id: 'skip',
    source: '跳过',
    title: '不应用（跳过此曲目）',
    artist: '—',
    album: '—',
    duration: '—',
    hasTimeline: false,
    confidence: '—',
    preview: ''
  }
])

const currentTrack = computed(() => selectedTracks.value[0])
const currentLyrics = computed(() => currentTrack.value?.tags.lyrics || '')

const selectedCandidate = computed(() => {
  return candidates.value.find((c) => c.id === selectedCandidateId.value)
})

const isSkipSelected = computed(() => selectedCandidateId.value === 'skip')

const handleApplySingle = () => {
  if (isSkipSelected.value) {
    // 显式跳过，不修改
    emit('close')
    return
  }
  if (currentTrack.value && selectedCandidate.value) {
    applyLyricsToTrack(currentTrack.value.id, selectedCandidate.value.preview)
  }
  emit('close')
}

const handleApplyBatch = () => {
  if (isSkipSelected.value) {
    emit('close')
    return
  }
  if (selectedCandidate.value) {
    batchApplyLyrics(selectedCandidate.value.preview)
  }
  emit('close')
}
</script>

<template>
  <ModalContainer title="🎵 多源歌词检索与智能匹配 (BIZ-012)" max-width="960px" @close="emit('close')">
    <div class="lyrics-search-modal">
      <!-- 顶部搜索工具栏 -->
      <div class="search-bar-row">
        <div class="source-filter-tabs">
          <button class="source-tab" :class="{ active: activeSource === 'all' }" @click="activeSource = 'all'">全部源</button>
          <button class="source-tab" :class="{ active: activeSource === 'netease' }" @click="activeSource = 'netease'">网易云音乐</button>
          <button class="source-tab" :class="{ active: activeSource === 'qq' }" @click="activeSource = 'qq'">QQ音乐</button>
          <button class="source-tab" :class="{ active: activeSource === 'lrcdb' }" @click="activeSource = 'lrcdb'">LRCDB</button>
          <button class="source-tab" :class="{ active: activeSource === 'kugou' }" @click="activeSource = 'kugou'">酷狗音乐</button>
        </div>
        <div class="search-input-group">
          <input
            v-model="searchKeyword"
            type="text"
            class="input search-kw-input"
            :placeholder="`当前曲目：${currentTrack?.tags.title || ''} - ${currentTrack?.tags.artist || ''}`"
          />
          <button class="btn primary search-btn">重新搜索</button>
        </div>
      </div>

      <!-- 主体分栏：左候选列表，右 Diff 对比 -->
      <div class="lyrics-layout">
        <div class="candidate-pane">
          <div class="pane-title">候选歌词 (共 {{ candidates.length }} 条候选)</div>
          <div class="cards-scroller">
            <div
              v-for="cand in candidates"
              :key="cand.id"
              class="lyrics-card"
              :class="{
                selected: selectedCandidateId === cand.id,
                'skip-card': cand.id === 'skip'
              }"
              @click="selectedCandidateId = cand.id"
            >
              <template v-if="cand.id === 'skip'">
                <div class="skip-header">
                  <span class="skip-icon">🚫</span>
                  <strong>{{ cand.title }}</strong>
                </div>
                <div class="skip-desc">
                  保留曲目当前本地歌词状态不变，不执行装配与覆盖。
                </div>
              </template>
              <template v-else>
                <div class="card-meta-top">
                  <span class="tag-source">{{ cand.source }}</span>
                  <span class="confidence-tag">{{ cand.confidence }} 匹配</span>
                </div>
                <div class="card-title">{{ cand.title }}</div>
                <div class="card-artist">{{ cand.artist }} · {{ cand.album }}</div>
                <div class="card-footer-info">
                  <span>时长：{{ cand.duration }}</span>
                  <span class="badge-lrc" :class="{ green: cand.hasTimeline }">
                    {{ cand.hasTimeline ? '✓ 带时间轴 LRC' : '纯文本' }}
                  </span>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="preview-diff-pane">
          <div class="pane-title">
            <span>歌词对照对比视窗 (Diff)</span>
            <span v-if="isSkipSelected" class="status-tip skip">已选择「跳过」此曲目</span>
            <span v-else class="status-tip">双击行可在后续编辑器中精修</span>
          </div>

          <div v-if="isSkipSelected" class="skip-placeholder-box">
            <div class="skip-big-icon">🚫</div>
            <div class="skip-headline">已选择「不应用（跳过此曲目）」</div>
            <p class="skip-subtext">
              点击下方「应用至当前曲目」时将跳过此曲目，完整保留该曲目当前的本地内嵌歌词（无论为空还是已有歌词），不给曲目打上脏标记。
            </p>
          </div>

          <div v-else class="diff-split-container">
            <!-- 左栏：本地现有歌词 -->
            <div class="diff-side local">
              <div class="side-header">
                <span>本地原始歌词</span>
                <span class="byte-badge">{{ currentLyrics ? `${currentLyrics.length} 字符` : '无内嵌歌词' }}</span>
              </div>
              <div class="lyrics-text-box">
                <pre v-if="currentLyrics">{{ currentLyrics }}</pre>
                <div v-else class="empty-lyrics-tip">
                  （当前音轨文件无内嵌歌词）
                </div>
              </div>
            </div>

            <!-- 右栏：待装配候选歌词 -->
            <div class="diff-side incoming">
              <div class="side-header">
                <span>{{ selectedCandidate?.source }} 候选歌词</span>
                <span class="byte-badge highlight">{{ selectedCandidate?.preview.length || 0 }} 字符</span>
              </div>
              <div class="lyrics-text-box">
                <pre>{{ selectedCandidate?.preview }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="modal-footer-between">
        <span class="footer-tip">
          * 歌词将装配至当前工作区内存编辑态，需点击「💾 确认写回」后才持久化落盘。
        </span>
        <div class="footer-btn-group">
          <button class="btn" @click="emit('close')">取消</button>
          <button
            class="btn"
            :disabled="isSkipSelected"
            title="将当前选中的歌词匹配方案批量装配到所有已勾选的曲目中"
            @click="handleApplyBatch"
          >
            批量应用至全部选中 ({{ selectedTracks.length }})
          </button>
          <button
            class="btn primary"
            @click="handleApplySingle"
          >
            {{ isSkipSelected ? '确认跳过当前曲目' : '应用至当前曲目' }}
          </button>
        </div>
      </div>
    </template>
  </ModalContainer>
</template>

<style scoped>
.lyrics-search-modal {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.search-bar-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-xs) var(--space-sm);
}

.source-filter-tabs {
  display: flex;
  gap: 4px;
}

.source-tab {
  background: none;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  font-size: var(--font-xs);
  color: var(--text-muted);
  cursor: pointer;
  font-family: inherit;
}

.source-tab.active {
  background: var(--bg-hover);
  border-color: var(--border-subtle);
  color: var(--accent);
  font-weight: 600;
}

.search-input-group {
  display: flex;
  gap: var(--space-xs);
}

.search-kw-input {
  flex: 1;
}

.lyrics-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--space-md);
  height: 440px;
}

.pane-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: var(--space-xs);
}

.status-tip {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.status-tip.skip {
  color: var(--warning);
  font-weight: 600;
}

.cards-scroller {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: calc(100% - 24px);
  overflow-y: auto;
  padding-right: 4px;
}

.lyrics-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px;
  cursor: pointer;
  background: var(--bg-surface);
  transition: all 0.15s ease;
}

.lyrics-card:hover {
  background: var(--bg-hover);
  border-color: var(--border-strong);
}

.lyrics-card.selected {
  border-color: var(--accent);
  background: var(--bg-active);
}

.lyrics-card.skip-card {
  border-style: dashed;
  background: var(--bg-hover);
}

.lyrics-card.skip-card.selected {
  border-color: var(--warning);
  background: var(--warning-dim);
}

.skip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--warning);
  font-size: var(--font-sm);
}

.skip-desc {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.4;
}

.card-meta-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  margin-bottom: 4px;
}

.tag-source {
  font-weight: 600;
  color: var(--accent);
}

.confidence-tag {
  color: var(--success);
  font-family: var(--font-mono);
}

.card-title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-artist {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-footer-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 6px;
}

.badge-lrc {
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--bg-hover);
  font-size: 10px;
}

.badge-lrc.green {
  color: var(--success);
  background: var(--success-dim);
}

.preview-diff-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.skip-placeholder-box {
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

.skip-big-icon {
  font-size: 36px;
  margin-bottom: 8px;
}

.skip-headline {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}

.skip-subtext {
  font-size: var(--font-sm);
  color: var(--text-muted);
  max-width: 420px;
  line-height: 1.5;
  margin: 0;
}

.diff-split-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-xs);
  flex: 1;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  overflow: hidden;
}

.diff-side {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.diff-side.local {
  border-right: 1px solid var(--border-subtle);
}

.side-header {
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

.byte-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-muted);
}

.byte-badge.highlight {
  background: var(--accent-dim);
  color: var(--accent);
}

.lyrics-text-box {
  flex: 1;
  padding: 10px;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: var(--font-xs);
  line-height: 1.6;
}

.lyrics-text-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-main);
}

.empty-lyrics-tip {
  color: var(--text-muted);
  font-style: italic;
  padding: var(--space-md);
  text-align: center;
}

.modal-footer-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.footer-tip {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.footer-btn-group {
  display: flex;
  gap: var(--space-xs);
}
</style>
