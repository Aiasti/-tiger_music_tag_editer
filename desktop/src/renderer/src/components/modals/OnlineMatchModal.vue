<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { matchMetadata } from '../../api/client'
import { trackBaseName } from '../../utils/trackText'
import type { MetadataCandidateItem, TrackTagData } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, selectedTracks, applyMatchedTags } = useWorkspace()

/** 批量作用范围：当前选区，未选中则全部可编辑曲目（BIZ-015） */
const scopeTracks = computed(() => operationScope.value)
const scopeIsSelection = computed(() => selectedTracks.value.length > 0)

/** 当前查看候选的曲目（点击差异表行切换）。 */
const activeTrackId = ref<number | null>(null)
watch(
  scopeTracks,
  (list) => {
    if (list.length === 0) {
      activeTrackId.value = null
      return
    }
    if (activeTrackId.value === null || !list.some((track) => track.id === activeTrackId.value)) {
      activeTrackId.value = list[0].id
    }
  },
  { immediate: true }
)
const activeTrack = computed(
  () => scopeTracks.value.find((track) => track.id === activeTrackId.value) || null
)

const searchKeyword = ref('')
const selectedCandidateIndex = ref(0)
const candidatesByTrack = ref<Record<number, MetadataCandidateItem[]>>({})
const warningsByTrack = ref<Record<number, string[]>>({})
const acoustidStatus = ref<{ available: boolean; used: boolean } | null>(null)
const loading = ref(false)
const stopRequested = ref(false)
const progress = ref({ done: 0, total: 0 })
const error = ref<string | null>(null)

const candidates = computed(() => {
  const id = activeTrackId.value
  return id === null ? [] : candidatesByTrack.value[id] ?? []
})
const warnings = computed(() => {
  const id = activeTrackId.value
  return id === null ? [] : warningsByTrack.value[id] ?? []
})
const selectedCandidate = computed(() => candidates.value[selectedCandidateIndex.value])

watch([activeTrackId, candidates], () => {
  selectedCandidateIndex.value = 0
})

/** 某首曲目用于对比/批量应用的候选：当前行用用户选中的候选，其他行用各自最高分候选。 */
const candidateFor = (track: (typeof scopeTracks.value)[number]): MetadataCandidateItem | null => {
  if (track.id === activeTrackId.value && selectedCandidate.value) {
    return selectedCandidate.value
  }
  return (candidatesByTrack.value[track.id] ?? [])[0] ?? null
}

/** 把候选字段转为可应用的工作区字段（空值不覆盖现值）。 */
const fieldsOf = (candidate: MetadataCandidateItem | null): Partial<TrackTagData> => {
  if (!candidate) {
    return {}
  }
  return {
    title: candidate.title,
    artist: candidate.artist,
    album: candidate.album,
    albumArtist: candidate.albumArtist,
    trackNo: candidate.trackNo,
    discNo: candidate.discNo,
    year: candidate.year,
    genre: candidate.genre
  }
}

/** 当前行选中候选的字段。 */
const candidateFields = (): Partial<TrackTagData> => fieldsOf(selectedCandidate.value ?? null)

/** 差异表展示每首曲目自己的候选匹配值。 */
const candidateValue = (track: (typeof scopeTracks.value)[number]) => {
  if (!(track.id in candidatesByTrack.value)) {
    return '未检索'
  }
  const candidate = candidateFor(track)
  if (!candidate) {
    return '无候选'
  }
  const trackNo = candidate.trackNo || track.tags.trackNo || '—'
  const discNo = candidate.discNo || track.tags.discNo || '1'
  return `[${candidate.source}] ${candidate.title || '—'} · 音轨 ${trackNo} · 碟 ${discNo}`
}

/** 对比每首曲目自己的候选与当前工作区字段，给出可更新项数。 */
const judgementOf = (track: (typeof scopeTracks.value)[number]) => {
  const candidate = candidateFor(track)
  if (!candidate) {
    return {
      text: track.id in candidatesByTrack.value ? '无候选' : '未检索',
      cls: 'edited'
    }
  }
  const fields = fieldsOf(candidate)
  const changes = (Object.keys(fields) as (keyof TrackTagData)[]).filter((key) => {
    const value = fields[key]
    return value && value !== (track.tags[key] ?? '')
  }).length
  if (changes === 0) {
    return { text: '已一致', cls: 'ok' }
  }
  return { text: `${changes} 项可更新`, cls: 'matched' }
}

const searchOne = async (track: (typeof scopeTracks.value)[number]) => {
  const result = await matchMetadata(track, searchKeyword.value.trim())
  candidatesByTrack.value = { ...candidatesByTrack.value, [track.id]: result.candidates }
  warningsByTrack.value = { ...warningsByTrack.value, [track.id]: result.warnings }
  acoustidStatus.value = { available: result.acoustidAvailable, used: result.acoustidUsed }
  return result
}

/** 检索当前曲目（或差异表当前激活行）。 */
const searchCurrent = async () => {
  const track = activeTrack.value
  if (!track || loading.value) {
    return
  }
  loading.value = true
  stopRequested.value = false
  error.value = null
  progress.value = { done: 0, total: 1 }
  try {
    await searchOne(track)
    progress.value = { done: 1, total: 1 }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 逐曲批量检索；后端已按服务条款限流，前端负责进度与停止。 */
const searchBatch = async () => {
  if (loading.value || scopeTracks.value.length === 0) {
    return
  }
  loading.value = true
  stopRequested.value = false
  error.value = null
  progress.value = { done: 0, total: scopeTracks.value.length }
  for (const track of scopeTracks.value) {
    if (stopRequested.value) {
      break
    }
    try {
      await searchOne(track)
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      warningsByTrack.value = { ...warningsByTrack.value, [track.id]: [message] }
    }
    progress.value = { done: progress.value.done + 1, total: progress.value.total }
  }
  loading.value = false
}

const stopSearch = () => {
  stopRequested.value = true
}

/** 仅应用到当前行 (FR-5)：应用后标记「已匹配」。 */
const applyToCurrentRow = () => {
  const track = activeTrack.value
  if (!track || !selectedCandidate.value) {
    return
  }
  applyMatchedTags(candidateFields(), [track.id])
  emit('close')
}

/** 批量应用：每首曲目使用各自最高分候选（当前行使用用户选中的候选）。 */
const applyToScope = () => {
  if (scopeTracks.value.length === 0) {
    return
  }
  let applied = 0
  const skipped: string[] = []
  for (const track of scopeTracks.value) {
    const candidate = candidateFor(track)
    if (!candidate) {
      skipped.push(trackBaseName(track))
      continue
    }
    const fields = fieldsOf(candidate)
    if (Object.values(fields).some((value) => value)) {
      applyMatchedTags(fields, [track.id])
      applied += 1
    }
  }
  if (applied === 0) {
    error.value = '当前作用范围内没有可应用的候选，请先执行检索'
    return
  }
  if (skipped.length > 0) {
    const names = skipped.slice(0, 5).join('、')
    const more = skipped.length > 5 ? ` 等 ${skipped.length} 首` : ''
    window.alert(`已应用 ${applied} 首各自最佳候选；以下曲目无候选已跳过：${names}${more}`)
  }
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
          ⏱️ 客户端限流保护：MusicBrainz ≤1.0 请求/秒，AcoustID ≤3 请求/秒
          <template v-if="acoustidStatus">
            · AcoustID：{{ acoustidStatus.available ? (acoustidStatus.used ? '已用于本次匹配' : '已启用，本次无指纹结果') : '未启用（仅 MusicBrainz）' }}
          </template>
          <template v-if="progress.total > 0"> · 进度 {{ progress.done }}/{{ progress.total }}</template>
        </span>
        <button
          class="btn ghost sm-btn"
          :disabled="!loading"
          title="停止后续曲目的检索（进行中的请求会先完成）"
          @click="stopSearch"
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
          placeholder="可选：输入检索关键词（留空使用标题/艺术家/文件名）"
          @keydown.enter="searchCurrent"
        />
        <button class="btn primary" :disabled="loading || !activeTrack" @click="searchCurrent">
          🔍 检索当前曲目
        </button>
        <button class="btn" :disabled="loading || scopeTracks.length === 0" @click="searchBatch">
          ⚡ 批量检索全部 {{ scopeTracks.length }} 首
        </button>
      </div>

      <div v-if="error" class="modal-alert warning">检索失败：{{ error }}</div>
      <div v-for="warning in warnings" :key="warning" class="modal-alert warning">⚠️ {{ warning }}</div>

      <!-- 候选结果列表 -->
      <div>
        <div class="pane-label">
          当前曲目「{{ activeTrack ? (activeTrack.tags.title || trackBaseName(activeTrack)) : '—' }}」
          检索到 {{ candidates.length }} 个基础元数据候选（点击卡片查看与工作区差异）：
        </div>
        <div v-if="candidates.length === 0" class="empty-cell">
          {{ loading ? '正在检索…' : '尚未检索或没有候选，请点击「检索当前曲目」' }}
        </div>
        <div v-else class="candidate-list">
          <div
            v-for="(cand, idx) in candidates"
            :key="cand.id"
            class="candidate-card"
            :class="{ selected: selectedCandidateIndex === idx }"
            @click="selectedCandidateIndex = idx"
          >
            <div class="cand-score">{{ cand.score }}%</div>
            <div class="cand-info">
              <div class="cand-title">{{ cand.title }} · {{ cand.source }}</div>
              <div class="cand-sub">
                {{ cand.detail || [cand.artist, cand.album, cand.year].filter(Boolean).join(' · ') }}
              </div>
            </div>
            <span class="status-dot" :class="idx === 0 ? 'ok' : 'edited'"></span>
          </div>
        </div>
      </div>

      <!-- 候选曲目清单与工作区标签差异比对 -->
      <div>
        <div class="pane-label">候选曲目清单与当前工作区标签差异比对（点击行切换检索对象）：</div>
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
              <tr
                v-for="(track, i) in scopeTracks"
                :key="track.id"
                :class="{ active: track.id === activeTrackId }"
                @click="activeTrackId = track.id"
              >
                <td>{{ i + 1 }}</td>
                <td>
                  {{ track.tags.title || trackBaseName(track) }}
                  <span v-if="!track.tags.trackNo" class="badge-old">(缺少音轨号)</span>
                </td>
                <td>
                  <span class="val-new">{{ candidateValue(track) }}</span>
                </td>
                <td>
                  <span :class="`judge-${judgementOf(track).cls}`">
                    {{ judgementOf(track).text }}
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
        批量应用时每首曲目使用各自最高分候选（当前行使用你选中的候选），无候选的曲目会被跳过；内嵌歌词请使用独立的「🎵 歌词搜索」按钮。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn" :disabled="!activeTrack || !selectedCandidate" @click="applyToCurrentRow">
        仅应用到当前行
      </button>
      <button class="btn primary" :disabled="loading || scopeTracks.length === 0" @click="applyToScope">
        批量应用各自最佳候选（{{ scopeTracks.length }} 首）
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
.diff-table tbody tr {
  cursor: pointer;
}
.diff-table tbody tr.active {
  background: var(--bg-hover);
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
.modal-alert.warning {
  color: var(--state-edited);
  background: var(--warning-dim);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
}
</style>
