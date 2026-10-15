<script setup lang="ts">
import { ref, computed } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, selectedTracks, selectedTrack, applyMatchedTags } = useWorkspace()

const searchKeyword = ref('')
const selectedCandidateIndex = ref(0)

/** 批量作用范围：当前选区，未选中则全部可编辑曲目（BIZ-015） */
const scopeTracks = computed(() => operationScope.value)
const scopeIsSelection = computed(() => selectedTracks.value.length > 0)

/** 基础元数据候选（MusicBrainz / AcoustID）；当前为原型模拟数据，后端接入属 S4 范围。 */
const candidates = ref([
  {
    id: 1,
    source: 'MusicBrainz',
    score: '98%',
    title: 'Tiger Live (Official Japanese Release)',
    sub: '发布日期: 2026-05-18 · 地区: 日本 (JP) · 包含 5 首曲目 · 媒体: CD / 44.1kHz FLAC',
    album: 'Tiger Live',
    artist: 'Tiger Band',
    year: '2026'
  },
  {
    id: 2,
    source: 'MusicBrainz',
    score: '85%',
    title: 'Tiger Live [Deluxe 2CD Edition]',
    sub: '发布日期: 2026-06-01 · 地区: 美国 (US) · 包含 8 首曲目 · 双碟装 (Disc 1/2)',
    album: 'Tiger Live (Deluxe)',
    artist: 'Tiger Band',
    year: '2026'
  },
  {
    id: 3,
    source: 'AcoustID',
    score: '74%',
    title: 'Tiger Acoustic Stage · AcoustID 声学指纹库',
    sub: '基于本地 Chromaprint 音频特征指纹比对 · 匹配到单曲指纹簇',
    album: 'Tiger Acoustic Stage',
    artist: 'Tiger Band',
    year: '2025'
  }
])

const selectedCandidate = computed(() => candidates.value[selectedCandidateIndex.value])

const candidateFields = () => ({
  title: selectedCandidate.value.title,
  artist: selectedCandidate.value.artist,
  album: selectedCandidate.value.album,
  year: selectedCandidate.value.year
})

/** 候选源匹配值展示（按当前选中候选与曲目音轨号拼装） */
const candidateValue = (trackNo: string, discNo: string) =>
  `${selectedCandidate.value.title} · 音轨 ${trackNo || '—'} · 碟 ${discNo || '1'}`

const judgementOf = (trackNo: string, status: string) => {
  if (!trackNo) return { text: '字段补齐', cls: 'matched' }
  if (status === 'failed') return { text: '可修复补齐', cls: 'edited' }
  return { text: '完全匹配', cls: 'ok' }
}

const resetSearch = () => {
  // 候选为原型模拟数据，重新检索仅复位到首个候选（真实检索属 S4 范围）
  selectedCandidateIndex.value = 0
}

/** 仅应用到当前选中行 (FR-5)：应用后标记「已匹配」 */
const applyToCurrentRow = () => {
  if (selectedTrack.value) {
    applyMatchedTags(candidateFields(), [selectedTrack.value.id])
  }
  emit('close')
}

/** 批量应用到作用范围内的全部曲目 (FR-5)：应用后标记「已匹配」 */
const applyToScope = () => {
  applyMatchedTags(candidateFields())
  emit('close')
}
</script>

<template>
  <ModalContainer
    title="🌐 在线元数据匹配候选比较 (MusicBrainz / AcoustID)"
    max-width="860px"
    @close="emit('close')"
  >
    <div class="online-modal-body">
      <!-- 作用范围提示 -->
      <div class="scope-strip">
        <span>
          作用于
          <strong>{{ scopeIsSelection ? '当前选区' : '工作区全部可编辑曲目' }}</strong>
          · 共 <strong>{{ scopeTracks.length }}</strong> 首
        </span>
        <span class="scope-tip">应用后以「已匹配」进入内存编辑态，需「💾 确认写回」才落盘</span>
      </div>

      <!-- 客户端限流状态 (BIZ-009) -->
      <div class="throttle-bar">
        <span>
          ⏱️ 客户端限流保护：MusicBrainz 严格遵守 ≤1.0 请求/秒，AcoustID ≤3 请求/秒
        </span>
        <button
          class="btn ghost sm-btn"
          disabled
          title="检索调度将在 S4 接入后端后可用"
        >
          ⏹ 停止查询
        </button>
      </div>

      <!-- 检索关键词栏 -->
      <div class="search-row">
        <input
          v-model="searchKeyword"
          type="text"
          class="input search-kw"
          placeholder="输入专辑名或艺术家搜索…"
        />
        <button class="btn primary" title="按关键词重新检索候选" @click="resetSearch">🔍 重新检索</button>
      </div>

      <!-- 候选结果列表 -->
      <div>
        <div class="pane-label">
          检索到 {{ candidates.length }} 个基础元数据匹配源（点击卡片查看与工作区差异）：
        </div>
        <div class="candidate-list">
          <div
            v-for="(cand, idx) in candidates"
            :key="cand.id"
            class="candidate-card"
            :class="{ selected: selectedCandidateIndex === idx }"
            @click="selectedCandidateIndex = idx"
          >
            <div class="cand-score">{{ cand.score }}</div>
            <div class="cand-info">
              <div class="cand-title">{{ cand.title }} · {{ cand.source }}</div>
              <div class="cand-sub">{{ cand.sub }}</div>
            </div>
            <span class="status-dot" :class="idx === 0 ? 'ok' : 'edited'"></span>
          </div>
        </div>
      </div>

      <!-- 候选曲目清单与工作区标签差异比对 -->
      <div>
        <div class="pane-label">候选曲目清单与当前工作区标签差异比对：</div>
        <div class="diff-table-box">
          <table class="diff-table">
            <thead>
              <tr>
                <th style="width: 40px;">#</th>
                <th>当前工作区字段值</th>
                <th>候选源匹配值</th>
                <th style="width: 96px;">匹配判定</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="track in scopeTracks" :key="track.id">
                <td>{{ track.id }}</td>
                <td>
                  {{ track.tags.title || track.filename }}
                  <span v-if="!track.tags.trackNo" class="badge-old">(缺少音轨号)</span>
                </td>
                <td>
                  <span class="val-new">{{ candidateValue(track.tags.trackNo, track.tags.discNo) }}</span>
                </td>
                <td>
                  <span :class="`judge-${judgementOf(track.tags.trackNo, track.status).cls}`">
                    {{ judgementOf(track.tags.trackNo, track.status).text }}
                  </span>
                </td>
              </tr>
              <tr v-if="scopeTracks.length === 0">
                <td colspan="4" class="empty-cell">工作区暂无可操作曲目</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="modal-alert info">
        ℹ️ 提示：基础元数据（标题、艺术家、专辑、曲目号等）点击「应用」后将以「已匹配」状态进入内存编辑态。
        内嵌歌词请使用独立的「🎵 歌词搜索」按钮进行专属检索与匹配。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn" :disabled="!selectedTrack" @click="applyToCurrentRow">
        仅应用到当前选中行
      </button>
      <button class="btn primary" :disabled="scopeTracks.length === 0" @click="applyToScope">
        批量应用到所有 {{ scopeTracks.length }} 首曲目
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.online-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.scope-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-xs);
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
}
.scope-strip strong {
  color: var(--text-bright);
}

.throttle-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-xs);
  color: var(--success);
  background: var(--success-dim);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
}
.sm-btn {
  height: 24px;
  padding: 0 9px;
  font-size: 11px;
}

.search-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
}
.search-kw {
  flex: 1;
}

.pane-label {
  font-size: var(--font-xs);
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}
.candidate-card {
  display: flex;
  align-items: center;
  gap: 10px;
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
.cand-score {
  width: 40px;
  flex-shrink: 0;
  font-family: var(--font-mono);
  font-size: var(--font-sm);
  font-weight: 700;
  color: var(--success);
  text-align: center;
}
.cand-info {
  min-width: 0;
  flex: 1;
}
.cand-title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-main);
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
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.ok {
  background: var(--state-ok);
}
.status-dot.edited {
  background: var(--state-edited);
}

.diff-table-box {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  overflow: hidden;
  max-height: 200px;
  overflow-y: auto;
}
.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-xs);
}
.diff-table th,
.diff-table td {
  padding: 7px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle);
}
.diff-table th {
  position: sticky;
  top: 0;
  background: var(--bg-hover);
  color: var(--text-muted);
  font-weight: 600;
}
.badge-old {
  color: var(--text-faint);
  margin-left: 4px;
}
.val-new {
  color: var(--state-matched);
  font-weight: 600;
}
.judge-matched {
  color: var(--state-matched);
}
.judge-ok {
  color: var(--state-ok);
}
.judge-edited {
  color: var(--state-edited);
}
.empty-cell {
  text-align: center;
  color: var(--text-muted);
  padding: var(--space-md);
}

.modal-alert {
  font-size: var(--font-xs);
  line-height: 1.5;
}
.modal-alert.info {
  color: var(--text-muted);
  background: var(--info-dim);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
}
</style>
