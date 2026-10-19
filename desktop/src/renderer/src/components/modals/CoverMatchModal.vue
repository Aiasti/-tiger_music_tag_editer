<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { fetchCoverCandidate, matchCover } from '../../api/client'
import { trackBaseName } from '../../utils/trackText'
import type { CoverCandidateItem } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, selectedTracks, setPendingCover } = useWorkspace()

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
const candidatesByTrack = ref<Record<number, CoverCandidateItem[]>>({})
const warningsByTrack = ref<Record<number, string[]>>({})
const loading = ref(false)
const applying = ref(false)
const stopRequested = ref(false)
const progress = ref({ done: 0, total: 0 })
const applyProgress = ref({ done: 0, total: 0 })
const error = ref<string | null>(null)
const selectedCandidateIndex = ref(0)

interface ArtState {
  objectUrl?: string
  dataUrl?: string
  loading?: boolean
  failed?: boolean
}
const art = ref<Record<string, ArtState>>({})

const artKey = (candidate: CoverCandidateItem): string =>
  candidate.releaseGroupId || candidate.releaseId || candidate.id

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
  void loadArtForActive()
})

/** 加载图片用于预览；同一候选只请求一次。 */
const loadArt = async (candidate: CoverCandidateItem): Promise<ArtState | null> => {
  const key = artKey(candidate)
  const existing = art.value[key]
  if (existing?.dataUrl || existing?.loading) {
    return existing
  }
  art.value = { ...art.value, [key]: { loading: true } }
  try {
    const result = await fetchCoverCandidate(candidate)
    const next: ArtState = result
      ? { objectUrl: result.objectUrl, dataUrl: result.dataUrl }
      : { failed: true }
    art.value = { ...art.value, [key]: next }
    return next
  } catch {
    art.value = { ...art.value, [key]: { failed: true } }
    return null
  }
}

const loadArtForActive = async () => {
  // 所有候选统一自动加载缩略图（iTunes 图片经后端代理下载）
  await Promise.all(candidates.value.map((candidate) => loadArt(candidate)))
}

const selectCandidate = async (index: number) => {
  selectedCandidateIndex.value = index
  const candidate = candidates.value[index]
  if (candidate) {
    await loadArt(candidate)
  }
}

const candidateFor = (track: (typeof scopeTracks.value)[number]): CoverCandidateItem | null => {
  if (track.id === activeTrackId.value && selectedCandidate.value) {
    return selectedCandidate.value
  }
  return (candidatesByTrack.value[track.id] ?? [])[0] ?? null
}

const artOf = (track: (typeof scopeTracks.value)[number]): ArtState | undefined => {
  const candidate = candidateFor(track)
  return candidate ? art.value[artKey(candidate)] : undefined
}

const searchOne = async (track: (typeof scopeTracks.value)[number]) => {
  const result = await matchCover(track, searchKeyword.value.trim())
  candidatesByTrack.value = { ...candidatesByTrack.value, [track.id]: result.candidates }
  warningsByTrack.value = { ...warningsByTrack.value, [track.id]: result.warnings }
  if (track.id === activeTrackId.value) {
    selectedCandidateIndex.value = 0
    await loadArtForActive()
  }
  return result
}

const searchCurrent = async () => {
  const track = activeTrack.value
  if (!track || loading.value || applying.value) {
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

const searchBatch = async () => {
  if (loading.value || applying.value || scopeTracks.value.length === 0) {
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

const stop = () => {
  stopRequested.value = true
}

/** 确保候选图片已下载为 data URL（写回需要 data URL）。 */
const ensureDataUrl = async (candidate: CoverCandidateItem): Promise<string | null> => {
  const key = artKey(candidate)
  if (art.value[key]?.dataUrl) {
    return art.value[key].dataUrl ?? null
  }
  const loaded = await loadArt(candidate)
  return loaded?.dataUrl ?? null
}

const applyToCurrentRow = async () => {
  const track = activeTrack.value
  const candidate = selectedCandidate.value
  if (!track || !candidate || applying.value) {
    return
  }
  applying.value = true
  error.value = null
  try {
    const dataUrl = await ensureDataUrl(candidate)
    if (!dataUrl) {
      error.value = '该候选没有可用的封面图片'
      return
    }
    setPendingCover(track.id, dataUrl)
    emit('close')
  } finally {
    applying.value = false
  }
}

/** 批量应用：每首曲目使用各自最高分候选（当前行使用用户选中的候选）。 */
const applyToScope = async () => {
  if (scopeTracks.value.length === 0 || applying.value) {
    return
  }
  applying.value = true
  stopRequested.value = false
  error.value = null
  applyProgress.value = { done: 0, total: scopeTracks.value.length }
  let applied = 0
  const skipped: string[] = []
  for (const track of scopeTracks.value) {
    if (stopRequested.value) {
      break
    }
    const candidate = candidateFor(track)
    if (!candidate) {
      skipped.push(trackBaseName(track))
    } else {
      const dataUrl = await ensureDataUrl(candidate)
      if (dataUrl) {
        setPendingCover(track.id, dataUrl)
        applied += 1
      } else {
        skipped.push(trackBaseName(track))
      }
    }
    applyProgress.value = { done: applyProgress.value.done + 1, total: applyProgress.value.total }
  }
  applying.value = false
  if (applied === 0) {
    error.value = '当前作用范围内没有可应用的封面候选，请先执行检索'
    return
  }
  if (skipped.length > 0) {
    const names = skipped.slice(0, 5).join('、')
    const more = skipped.length > 5 ? ` 等 ${skipped.length} 首` : ''
    window.alert(`已应用 ${applied} 首封面；以下曲目无可用封面已跳过：${names}${more}`)
  }
  emit('close')
}

/** 批量应用：当前行选中的封面应用到作用范围内全部曲目。 */
const applySelectedToScope = async () => {
  const candidate = selectedCandidate.value
  if (!candidate || scopeTracks.value.length === 0 || applying.value) {
    return
  }
  applying.value = true
  stopRequested.value = false
  error.value = null
  applyProgress.value = { done: 0, total: scopeTracks.value.length }
  let applied = 0
  try {
    const dataUrl = await ensureDataUrl(candidate)
    if (!dataUrl) {
      error.value = '当前行所选封面没有可用图片'
      return
    }
    for (const track of scopeTracks.value) {
      if (stopRequested.value) {
        break
      }
      setPendingCover(track.id, dataUrl)
      applied += 1
      applyProgress.value = { done: applyProgress.value.done + 1, total: applyProgress.value.total }
    }
  } finally {
    applying.value = false
  }
  if (applied === 0) {
    error.value = '没有应用到任何曲目'
    return
  }
  emit('close')
}

onBeforeUnmount(() => {
  Object.values(art.value).forEach((item) => {
    if (item.objectUrl) {
      URL.revokeObjectURL(item.objectUrl)
    }
  })
})
</script>

<template>
  <ModalContainer title="🖼️ 在线封面匹配" max-width="900px" @close="emit('close')">
    <div class="cover-modal-body">
      <div class="scope-strip">
        <span>
          作用于
          <strong>{{ scopeIsSelection ? '当前选区' : '工作区全部可编辑曲目' }}</strong>
          · 共 <strong>{{ scopeTracks.length }}</strong> 首
        </span>
        <span class="scope-tip">只写入封面，不修改标签字段与歌词；应用后进入待写回态</span>
      </div>

      <div class="throttle-bar">
        <span>
          ⏱️ iTunes 封面检索按 3 请求/秒串行；按专辑名、艺术家与标题匹配
          <template v-if="progress.total > 0"> · 检索 {{ progress.done }}/{{ progress.total }}</template>
          <template v-if="applyProgress.total > 0"> · 应用 {{ applyProgress.done }}/{{ applyProgress.total }}</template>
        </span>
        <button class="btn ghost sm-btn" :disabled="!loading && !applying" @click="stop">
          ⏹ 停止
        </button>
      </div>

      <div class="search-row">
        <input
          v-model="searchKeyword"
          type="text"
          class="input search-kw"
          placeholder="可选：输入专辑名/关键词（留空使用专辑名、标题、指纹与艺术家）"
          @keydown.enter="searchCurrent"
        />
        <button class="btn primary" :disabled="loading || applying || !activeTrack" @click="searchCurrent">
          🔍 检索当前曲目
        </button>
        <button class="btn" :disabled="loading || applying || scopeTracks.length === 0" @click="searchBatch">
          ⚡ 批量检索全部 {{ scopeTracks.length }} 首
        </button>
      </div>

      <div v-if="error" class="modal-alert warning">封面匹配失败：{{ error }}</div>
      <div v-for="warning in warnings" :key="warning" class="modal-alert warning">⚠️ {{ warning }}</div>

      <div>
        <div class="pane-label">
          当前曲目「{{ activeTrack ? (activeTrack.tags.title || trackBaseName(activeTrack)) : '—' }}」
          的封面候选（{{ candidates.length }} 个）：
        </div>
        <div v-if="candidates.length === 0" class="empty-cell">
          {{ loading ? '正在检索…' : '尚未检索或没有封面候选，请点击「检索当前曲目」' }}
        </div>
        <div v-else class="candidate-list">
          <div
            v-for="(candidate, index) in candidates"
            :key="candidate.id"
            class="candidate-card"
            :class="{ selected: selectedCandidateIndex === index }"
            @click="selectCandidate(index)"
          >
            <div class="cover-thumb">
              <img
                v-if="art[artKey(candidate)]?.objectUrl"
                :src="art[artKey(candidate)]?.objectUrl"
                alt="封面候选"
              />
              <span v-else-if="art[artKey(candidate)]?.loading" class="thumb-hint">加载中…</span>
              <span v-else class="thumb-hint">无图片</span>
            </div>
            <div class="candidate-info">
              <div class="candidate-title">{{ candidate.album || candidate.title || '未知专辑' }}</div>
              <div class="candidate-sub">
                {{ candidate.artist || '未知艺术家' }}
                <template v-if="candidate.year"> · {{ candidate.year }}</template>
                · {{ candidate.score }}% 匹配
              </div>
              <div class="candidate-detail">{{ candidate.detail }}</div>
            </div>
            <span class="status-dot" :class="index === 0 ? 'ok' : 'edited'"></span>
          </div>
        </div>
      </div>

      <div>
        <div class="pane-label">逐曲封面候选（点击行切换检索对象）：</div>
        <div class="diff-table-box">
          <table class="diff-table">
            <thead>
              <tr>
                <th style="width: 40px;">#</th>
                <th>曲目</th>
                <th>各自候选专辑</th>
                <th style="width: 86px;">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(track, index) in scopeTracks"
                :key="track.id"
                :class="{ active: track.id === activeTrackId }"
                @click="activeTrackId = track.id"
              >
                <td>{{ index + 1 }}</td>
                <td>{{ track.tags.title || trackBaseName(track) }}</td>
                <td>
                  <template v-if="candidateFor(track)">
                    {{ candidateFor(track)?.album || candidateFor(track)?.title || '—' }}
                    · {{ candidateFor(track)?.score }}%
                  </template>
                  <template v-else>
                    {{ track.id in candidatesByTrack ? '无候选' : '未检索' }}
                  </template>
                </td>
                <td>
                  <span v-if="artOf(track)?.dataUrl" class="judge-ok">可应用</span>
                  <span v-else-if="candidateFor(track)" class="judge-matched">待加载</span>
                  <span v-else class="judge-edited">跳过</span>
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
        ℹ️ 封面匹配通过 iTunes Search API 按专辑名、艺术家与标题检索封面；
        批量应用按每首曲目各自最高分候选写入，不使用当前行候选。封面只进入待写回态，不影响任何标签字段或歌词。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button
        class="btn"
        :disabled="!activeTrack || !selectedCandidate || applying"
        @click="applyToCurrentRow"
      >
        仅应用到当前行
      </button>
      <button class="btn" :disabled="loading || applying || scopeTracks.length === 0" @click="applyToScope">
        批量应用各自最佳封面（{{ scopeTracks.length }} 首）
      </button>
      <button
        class="btn primary"
        :disabled="!selectedCandidate || loading || applying || scopeTracks.length === 0"
        title="把当前行选中的封面应用到作用范围内的全部曲目"
        @click="applySelectedToScope"
      >
        批量应用封面到当前选区（{{ scopeTracks.length }} 首）
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.cover-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.scope-strip,
.throttle-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-xs);
  color: var(--text-muted);
  background: var(--bg-hover);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 10px;
}
.scope-tip {
  color: var(--text-faint);
}
.search-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
}
.search-kw {
  flex: 1;
}
.sm-btn {
  height: 24px;
  padding: 0 9px;
  font-size: 11px;
}
.pane-label {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-bottom: 6px;
}
.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 230px;
  overflow-y: auto;
}
.candidate-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  cursor: pointer;
}
.candidate-card.selected {
  border-color: var(--accent);
  background: var(--bg-hover);
}
.cover-thumb {
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-hint {
  font-size: 10px;
  color: var(--text-faint);
}
.candidate-info {
  flex: 1;
  min-width: 0;
}
.candidate-title {
  font-size: var(--font-sm);
  color: var(--text-main);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.candidate-sub,
.candidate-detail {
  font-size: var(--font-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  max-height: 210px;
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
.judge-ok {
  color: var(--state-ok);
}
.judge-matched {
  color: var(--state-matched);
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
