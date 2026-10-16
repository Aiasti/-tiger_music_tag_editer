import { ref, computed } from 'vue'
import type {
  TrackItem,
  TrackTagData,
  EditableTagField,
  ColumnDefinition,
  SkipItem,
  WriteOutcomeItem,
  RenamePlanItem
} from '../types'
import {
  clearWorkspace as clearWorkspaceApi,
  commitWriteBack,
  fetchWorkspace,
  importPaths,
  previewRename,
  removeTracks,
  type RenamePreviewOptions,
  type WorkspaceView
} from '../api/client'

// 字段列定义（12 个核心元数据列 + 状态圆点列 + 序号列 + 文件名列）；width 可拖拽调整（BIZ-015）
const initialColumns: ColumnDefinition[] = [
  { key: 'status', label: '状态', minWidth: 36, width: 44, visible: true, editable: false },
  { key: 'id', label: '#', minWidth: 42, width: 42, visible: true, editable: false },
  { key: 'filename', label: '文件名', minWidth: 140, width: 180, visible: true, editable: false },
  { key: 'discNo', label: '碟号', minWidth: 55, width: 60, visible: true, editable: true },
  { key: 'trackNo', label: '音轨', minWidth: 55, width: 60, visible: true, editable: true },
  { key: 'title', label: '标题', minWidth: 160, width: 200, visible: true, editable: true },
  { key: 'artist', label: '艺术家', minWidth: 120, width: 150, visible: true, editable: true },
  { key: 'album', label: '专辑', minWidth: 140, width: 180, visible: true, editable: true },
  { key: 'albumArtist', label: '专辑艺术家', minWidth: 110, width: 130, visible: true, editable: true },
  { key: 'year', label: '年份', minWidth: 65, width: 70, visible: true, editable: true },
  { key: 'genre', label: '流派', minWidth: 90, width: 100, visible: true, editable: true },
  { key: 'composer', label: '作曲', minWidth: 95, width: 110, visible: true, editable: true },
  { key: 'lyricist', label: '作词', minWidth: 95, width: 110, visible: true, editable: true },
  { key: 'arranger', label: '编曲', minWidth: 95, width: 110, visible: true, editable: true },
  { key: 'comment', label: '注释', minWidth: 130, width: 150, visible: true, editable: true },
  { key: 'duration', label: '时长', minWidth: 60, width: 70, visible: true, editable: false }
]

// 工作区数据来自后端（S1：导入与展示；编辑能力仍为内存态，写回属 S2）
const tracks = ref<TrackItem[]>([])
const skipped = ref<SkipItem[]>([])
const importing = ref(false)
const importError = ref<string | null>(null)
const columns = ref<ColumnDefinition[]>(initialColumns)
/** 主选中（详情面板、歌词编辑等单曲上下文） */
const selectedTrackId = ref<number | null>(null)
/** 完整选区（Ctrl 点选切换、Shift 连选）；批量类操作以它为作用域（BIZ-015） */
const selectedTrackIds = ref<number[]>([])
/** 待写回封面（BIZ-020）：trackId → data URL；空串表示已标记移除封面。仅内存编辑态。 */
const pendingCovers = ref<Record<number, string>>({})
/** 待写回路径（BIZ-030）：trackId → 目标完整路径；点「应用」后写入，随「确认写回」落盘。 */
const pendingPaths = ref<Record<number, string>>({})
/** 最近一次写回结果（BIZ-028）：供结果面板逐项展示。 */
const lastWriteOutcomes = ref<WriteOutcomeItem[]>([])
/** 写回进行中标记。 */
const writing = ref(false)
/** 重命名/归档预览结果（FR-7、BIZ-029）。 */
const renamePreview = ref<RenamePlanItem[]>([])
/** 重命名预览加载中标记。 */
const previewingRename = ref(false)

/** 可写回的 12 个元数据字段（S2-2 不含歌词，歌词帧映射属 DEV-005）。 */
const METADATA_FIELDS: EditableTagField[] = [
  'title',
  'artist',
  'album',
  'albumArtist',
  'trackNo',
  'discNo',
  'year',
  'genre',
  'composer',
  'lyricist',
  'arranger',
  'comment'
]

// 行内编辑状态
const editingCell = ref<{
  trackId: number
  field: EditableTagField
} | null>(null)

export function useWorkspace() {
  const selectedTrack = computed(() => {
    return tracks.value.find((t) => t.id === selectedTrackId.value) || null
  })

  /** 当前选区内的曲目（BIZ-015）；批量类操作以此为准。 */
  const selectedTracks = computed(() => {
    const ids = selectedTrackIds.value
    if (ids.length === 0) {
      return []
    }
    return tracks.value.filter((t) => ids.includes(t.id))
  })

  /** 批量操作的作用域：有选区用选区，否则退回全部非失败曲目。 */
  const operationScope = computed(() => {
    return selectedTracks.value.length > 0
      ? selectedTracks.value
      : tracks.value.filter((t) => t.status !== 'failed')
  })

  /** 选区中是否存在未写回的修改（用于移除前二次确认）。 */
  const hasUncommittedInSelection = computed(() => {
    return selectedTracks.value.some((t) => t.status === 'edited' || t.status === 'matched')
  })

  const uncommittedCount = computed(() => {
    return tracks.value.filter((t) => t.status === 'edited' || t.status === 'matched').length
  })

  const isWorkspaceDirty = computed(() => uncommittedCount.value > 0)

  /** 标签值副本（TrackTagData 全为字符串字段，浅拷贝即满足值隔离，DEV-002）。 */
  const cloneTags = (tags: TrackTagData): TrackTagData => ({ ...tags })

  /** 相对最近落盘值是否存在待提交差异（含待写回封面与路径，BIZ-020、BIZ-030）。 */
  const hasPendingChanges = (track: TrackItem): boolean => {
    if (pendingCovers.value[track.id] !== undefined || pendingPaths.value[track.id] !== undefined) {
      return true
    }
    return (Object.keys(track.lastWritten) as (keyof TrackTagData)[]).some(
      (field) => (track.tags[field] ?? '') !== (track.lastWritten[field] ?? '')
    )
  }

  /**
   * 按与最近落盘值的差异刷新状态（FR-8）：无差异即「已保存」；有差异保留「已匹配」来源标记，
   * 否则为「已编辑」。首次快照与最近落盘值不同时（如成功写回后再回滚），仍会标记为待提交。
   */
  const refreshStatus = (track: TrackItem): void => {
    if (track.status === 'failed') {
      return
    }
    if (!hasPendingChanges(track)) {
      track.status = 'saved'
    } else if (track.status !== 'matched') {
      track.status = 'edited'
    }
  }

  /**
   * 应用后端工作区视图。渲染层是编辑态唯一真源（BIZ-026）：已存在的曲目保留本地编辑值、
   * 首次快照与最近落盘值，只刷新文件相关的只读信息，避免后续导入或刷新丢失未提交编辑。
   */
  const applyView = (view: WorkspaceView) => {
    const localById = new Map(tracks.value.map((t) => [t.id, t]))
    tracks.value = view.tracks.map((raw) => {
      const local = localById.get(raw.id)
      if (!local) {
        // 新导入曲目：导入时读取值即最近落盘值（DEV-002）
        return { ...raw, lastWritten: cloneTags(raw.tags) }
      }
      return {
        ...local,
        filename: raw.filename,
        ext: raw.ext,
        filePath: raw.filePath,
        size: raw.size,
        duration: raw.duration,
        hasCover: raw.hasCover,
        failureReason: raw.failureReason
      }
    })
    skipped.value = view.skipped
    // 选区跟随数据收敛：剔除已不存在（如被移出工作区）的 id
    const existing = new Set(tracks.value.map((t) => t.id))
    selectedTrackIds.value = selectedTrackIds.value.filter((id) => existing.has(id))
    // 待写回封面同样剔除已不存在的曲目（BIZ-020）
    const nextCovers: Record<number, string> = {}
    for (const [key, value] of Object.entries(pendingCovers.value)) {
      if (existing.has(Number(key))) {
        nextCovers[Number(key)] = value
      }
    }
    pendingCovers.value = nextCovers
    // 待写回路径同样收敛（BIZ-030）
    const nextPaths: Record<number, string> = {}
    for (const [key, value] of Object.entries(pendingPaths.value)) {
      if (existing.has(Number(key))) {
        nextPaths[Number(key)] = value
      }
    }
    pendingPaths.value = nextPaths
    if (selectedTrackId.value !== null && !existing.has(selectedTrackId.value)) {
      selectedTrackId.value = null
    }
    if (tracks.value.length > 0 && selectedTrackIds.value.length === 0) {
      selectedTrackIds.value = [tracks.value[0].id]
      selectedTrackId.value = tracks.value[0].id
    }
  }

  // 拉取当前工作区（会话内存态）
  const refreshWorkspace = async (): Promise<void> => {
    try {
      applyView(await fetchWorkspace())
      importError.value = null
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
    }
  }

  // 导入：主进程弹窗选文件/文件夹 → 后端读取标签 → 更新工作区 (FR-1)
  const importFromDialog = async (mode: 'files' | 'folder'): Promise<void> => {
    importing.value = true
    importError.value = null
    try {
      const paths =
        mode === 'files' ? await window.api.selectAudioFiles() : await window.api.selectFolder()
      if (paths.length === 0) {
        return
      }
      applyView(await importPaths(paths, mode === 'folder'))
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
    } finally {
      importing.value = false
    }
  }

  /**
   * 选中某行（BIZ-015）：普通点击单选；Ctrl/Cmd 点选切换；Shift 连选。
   */
  const selectTrack = (
    trackId: number,
    modifiers?: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean }
  ) => {
    const ctrl = Boolean(modifiers?.ctrlKey || modifiers?.metaKey)
    const shift = Boolean(modifiers?.shiftKey)

    if (shift && selectedTrackId.value !== null) {
      const ids = tracks.value.map((t) => t.id)
      const anchor = ids.indexOf(selectedTrackId.value)
      const target = ids.indexOf(trackId)
      if (anchor >= 0 && target >= 0) {
        const [from, to] = anchor <= target ? [anchor, target] : [target, anchor]
        selectedTrackIds.value = ids.slice(from, to + 1)
        return
      }
    }

    if (ctrl) {
      selectedTrackIds.value = selectedTrackIds.value.includes(trackId)
        ? selectedTrackIds.value.filter((id) => id !== trackId)
        : [...selectedTrackIds.value, trackId]
      selectedTrackId.value = trackId
      return
    }

    selectedTrackId.value = trackId
    selectedTrackIds.value = [trackId]
  }

  /** 全选当前工作区内可编辑曲目。 */
  const selectAllTracks = () => {
    const ids = tracks.value.filter((t) => t.status !== 'failed').map((t) => t.id)
    selectedTrackIds.value = ids
    selectedTrackId.value = ids.length > 0 ? ids[0] : null
  }

  /** 将所选曲目移出工作区（BIZ-015）：仅会话内移除，不改磁盘。 */
  const removeSelectedTracks = async (): Promise<number> => {
    const ids = [...selectedTrackIds.value]
    if (ids.length === 0) {
      return 0
    }
    try {
      applyView(await removeTracks(ids))
      importError.value = null
      return ids.length
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
      return 0
    }
  }

  /**
   * 拖拽重排（FR-3 / BIZ-017）：**仅界面内生效**，不写后端、不持久化。
   * 顺序只影响当前界面的按范围编号与后续归档顺序；`#` 列标识曲目本身，不随拖拽变化。
   */
  const reorderTrack = (fromId: number, toIndex: number): void => {
    const ids = tracks.value.map((t) => t.id)
    const fromIndex = ids.indexOf(fromId)
    if (fromIndex < 0 || toIndex < 0 || toIndex >= ids.length || fromIndex === toIndex) {
      return
    }
    const next = [...ids]
    next.splice(fromIndex, 1)
    next.splice(toIndex, 0, fromId)

    const byId = new Map(tracks.value.map((t) => [t.id, t]))
    tracks.value = next.map((id) => byId.get(id)).filter((t): t is TrackItem => t !== undefined)
  }

  /** 清空当前会话工作区（BIZ-017）：应用启动时调用，保证「关闭重开工作区为空」。 */
  const clearWorkspaceSession = async (): Promise<void> => {
    try {
      applyView(await clearWorkspaceApi())
      importError.value = null
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
    }
  }

  /**
   * 按碟号对当前范围分组（FR-3、BIZ-003）：供编号预览；缺少碟号的曲目单列（不参与编号）。
   * 碟号只作分组依据，编号不修改碟号本身。
   */
  const renumberGroups = computed(() => {
    const entries: { disc: string; tracks: TrackItem[] }[] = []
    const index = new Map<string, number>()
    const missing: TrackItem[] = []
    operationScope.value.forEach((track) => {
      const disc = (track.tags.discNo ?? '').trim()
      if (disc === '') {
        missing.push(track)
        return
      }
      const at = index.get(disc)
      if (at === undefined) {
        index.set(disc, entries.length)
        entries.push({ disc, tracks: [track] })
      } else {
        entries[at].tracks.push(track)
      }
    })
    entries.sort((a, b) => a.disc.localeCompare(b.disc, undefined, { numeric: true }))
    return { entries, missing }
  })

  /**
   * 重新分配音轨号（FR-3、BIZ-003）：
   * - 不传 `discNumber`：按各曲目已有碟号分组（升序），每组内自 1 起连续编号，缺碟号者不参与；
   * - 传 `discNumber`：把范围内全部曲目的碟号统一设为该值，整个范围自 1 起连续编号。
   *
   * @returns 未参与编号（缺碟号）的曲目数
   */
  const renumberTracks = (autoFillTotal: boolean, discNumber?: number | null): number => {
    const scope = operationScope.value
    if (discNumber !== undefined && discNumber !== null) {
      const total = scope.length
      scope.forEach((track, idx) => {
        const number = idx + 1
        track.tags.discNo = String(discNumber)
        track.tags.trackNo = autoFillTotal ? `${number}/${total}` : `${number}`
        refreshStatus(track)
      })
      return 0
    }
    const { entries, missing } = renumberGroups.value
    entries.forEach((group) => {
      const total = group.tracks.length
      group.tracks.forEach((track, idx) => {
        const number = idx + 1
        track.tags.trackNo = autoFillTotal ? `${number}/${total}` : `${number}`
        refreshStatus(track)
      })
    })
    return missing.length
  }

  // 批量更新标签 (FR-4)；作用于当前选区（BIZ-015）
  const batchUpdateTags = (updates: Partial<Record<EditableTagField, string>>) => {
    const list = operationScope.value
    list.forEach((track) => {
      let changed = false
      for (const [key, val] of Object.entries(updates)) {
        if (val !== undefined && val !== '') {
          track.tags[key as EditableTagField] = val
          changed = true
        }
      }
      if (changed) {
        refreshStatus(track)
      }
    })
  }

  // 批量应用歌词至曲目 (BIZ-012)；作用于当前选区（BIZ-015）
  const batchApplyLyrics = (lyricsText: string) => {
    const list = operationScope.value
    list.forEach((track) => {
      track.tags.lyrics = lyricsText
      refreshStatus(track)
    })
  }

  // 双击单元格激活行内编辑 (BIZ-011)
  const startInlineEdit = (trackId: number, field: EditableTagField) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (!track || track.status === 'failed') return
    editingCell.value = { trackId, field }
  }

  // 提交行内编辑并转入内存编辑态（未写回）
  const commitInlineEdit = (newValue: string) => {
    if (!editingCell.value) return
    const { trackId, field } = editingCell.value
    const track = tracks.value.find((t) => t.id === trackId)
    if (track) {
      if (track.tags[field] !== newValue) {
        track.tags[field] = newValue
        refreshStatus(track)
      }
    }
    editingCell.value = null
  }

  // 取消行内编辑 (Esc)
  const cancelInlineEdit = () => {
    editingCell.value = null
  }

  // Tab / Shift+Tab 纵向跨行跳转 (BIZ-011)
  const navigateInlineEdit = (direction: 'up' | 'down') => {
    if (!editingCell.value) return
    const { trackId, field } = editingCell.value
    const validTracks = tracks.value.filter((t) => t.status !== 'failed')
    const currentIndex = validTracks.findIndex((t) => t.id === trackId)
    if (currentIndex === -1) return

    const nextIndex = direction === 'down' ? currentIndex + 1 : currentIndex - 1
    if (nextIndex >= 0 && nextIndex < validTracks.length) {
      const nextTrack = validTracks[nextIndex]
      editingCell.value = { trackId: nextTrack.id, field }
      selectedTrackId.value = nextTrack.id
    } else {
      // 达到边界，退出编辑
      editingCell.value = null
    }
  }

  /** 设置待写回封面（BIZ-020）：dataUrl 为空串表示标记移除封面；仅改内存编辑态。 */
  const setPendingCover = (trackId: number, dataUrl: string) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (!track || track.status === 'failed') return
    pendingCovers.value = { ...pendingCovers.value, [trackId]: dataUrl }
    refreshStatus(track)
  }

  // 更新内嵌歌词（进入内存编辑态，严禁直接落盘）
  const applyLyricsToTrack = (trackId: number, lyricsText: string) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (track && track.status !== 'failed') {
      track.tags.lyrics = lyricsText
      refreshStatus(track)
    }
  }

  // 批量更新歌词 (BIZ-012)
  const applyBatchLyrics = (lyricsMap: Record<number, string>) => {
    for (const [idStr, lyricsText] of Object.entries(lyricsMap)) {
      applyLyricsToTrack(Number(idStr), lyricsText)
    }
  }

  // 批量修改元数据字段 (FR-4)；作用于当前选区（BIZ-015）
  const applyBatchTags = (
    fields: Partial<TrackTagData>,
    clearFields: (keyof TrackTagData)[] = []
  ) => {
    operationScope.value.forEach((t) => {
      if (t.status === 'failed') return
      let modified = false
      for (const [k, v] of Object.entries(fields)) {
        const key = k as keyof TrackTagData
        if (v !== undefined && v !== '') {
          t.tags[key] = v
          modified = true
        }
      }
      for (const cf of clearFields) {
        t.tags[cf] = ''
        modified = true
      }
      if (modified) {
        refreshStatus(t)
      }
    })
  }

  /**
   * 应用在线候选标签（FR-5）：应用后标记「已匹配」（待写回落盘）。
   * 不传 `trackIds` 时作用于当前选区（未选中则工作区全部可编辑曲目，BIZ-015）。
   */
  const applyMatchedTags = (fields: Partial<TrackTagData>, trackIds?: number[]) => {
    const list =
      trackIds === undefined
        ? operationScope.value
        : tracks.value.filter((t) => trackIds.includes(t.id))
    list.forEach((t) => {
      if (t.status === 'failed') return
      let modified = false
      for (const [k, v] of Object.entries(fields)) {
        if (v !== undefined && v !== '') {
          t.tags[k as keyof TrackTagData] = v as string
          modified = true
        }
      }
      if (modified) {
        t.status = 'matched'
        refreshStatus(t)
      }
    })
  }

  // 提交前回滚至首次快照 (BIZ-006 / FR-8)：只改编辑态与顺序，不写盘；最近落盘值保持不变
  const rollbackTrack = (trackId: number) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (track && track.status !== 'failed') {
      track.tags = cloneTags(track.initialSnapshot)
    }
    if (pendingCovers.value[trackId] !== undefined) {
      const next = { ...pendingCovers.value }
      delete next[trackId]
      pendingCovers.value = next
    }
    if (pendingPaths.value[trackId] !== undefined) {
      const next = { ...pendingPaths.value }
      delete next[trackId]
      pendingPaths.value = next
    }
    if (track) {
      refreshStatus(track)
    }
  }

  const rollbackAll = () => {
    // 顺序先恢复为导入顺序（曲目 id 按导入递增，升序即导入顺序），再恢复首次快照（BIZ-008 / BIZ-006）
    tracks.value = [...tracks.value].sort((a, b) => a.id - b.id)
    // 封面与路径同属编辑态，回滚一并丢弃（BIZ-020 / BIZ-021 / BIZ-030）
    pendingCovers.value = {}
    pendingPaths.value = {}
    tracks.value.forEach((t) => {
      if (t.status !== 'failed') {
        t.tags = cloneTags(t.initialSnapshot)
      }
      // 首次快照与最近落盘值不同时（如已成功写回后再回滚）仍标记为待提交（DEV-002）
      refreshStatus(t)
    })
  }

  /**
   * 写回标签到磁盘（FR-8、DEV-003、BIZ-028）：提交每首待提交曲目的整份当前值与封面意图，
   * 后端按自己持有的落盘基线自算差异并逐文件执行；结果用于刷新最近落盘值、状态与结果面板。
   */
  const commitAllWrites = async (): Promise<void> => {
    const targets = tracks.value.filter((t) => t.status !== 'failed' && hasPendingChanges(t))
    if (targets.length === 0) {
      lastWriteOutcomes.value = []
      return
    }
    writing.value = true
    try {
      const result = await commitWriteBack(
        targets.map((t) => ({
          id: t.id,
          tags: cloneTags(t.tags),
          cover: pendingCovers.value[t.id] === undefined ? null : pendingCovers.value[t.id],
          targetPath: pendingPaths.value[t.id] ?? null
        }))
      )
      lastWriteOutcomes.value = result.outcomes
      applyWriteOutcomes(result.outcomes)
      importError.value = null
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
      lastWriteOutcomes.value = []
    } finally {
      writing.value = false
    }
  }

  /** 按写回结果收敛状态：成功项刷新最近落盘值并清待提交媒体/路径；其余保留待提交并附原因。 */
  const applyWriteOutcomes = (outcomes: WriteOutcomeItem[]): void => {
    const byId = new Map(outcomes.map((o) => [o.id, o]))
    tracks.value.forEach((t) => {
      const outcome = byId.get(t.id)
      if (!outcome) return
      if (outcome.outcome === 'written') {
        if (outcome.tags) {
          applyWrittenMetadata(t, outcome.tags)
        }
        t.hasCover = outcome.hasCover
        if (outcome.filePath) {
          t.filePath = outcome.filePath
        }
        deletePending(t.id)
        t.failureReason = undefined
        refreshStatus(t)
        return
      }
      if (outcome.outcome === 'partial') {
        // 标签已写、路径未改：刷新标签落盘值，路径仍属待提交差异（DEV-003 结论 2）
        if (outcome.tags) {
          applyWrittenMetadata(t, outcome.tags)
        }
        t.hasCover = outcome.hasCover
        t.failureReason = outcome.reason || '路径变更失败，标签已写'
        refreshStatus(t)
        return
      }
      // stale / blocked / failed-restored：磁盘未变，保持待提交；failed-manual 需人工处理
      t.failureReason = outcome.detail || outcome.reason || '写回失败'
      if (outcome.outcome === 'failed-manual') {
        t.status = 'failed'
      }
    })
  }

  /** 清除某曲目的待写回封面与路径（写回成功后）。 */
  const deletePending = (trackId: number): void => {
    if (pendingCovers.value[trackId] !== undefined) {
      const next = { ...pendingCovers.value }
      delete next[trackId]
      pendingCovers.value = next
    }
    if (pendingPaths.value[trackId] !== undefined) {
      const next = { ...pendingPaths.value }
      delete next[trackId]
      pendingPaths.value = next
    }
  }

  /** 用磁盘实际值刷新最近落盘值的 12 个元数据字段（歌词未写回，保持不变）。 */
  const applyWrittenMetadata = (track: TrackItem, written: TrackTagData): void => {
    const next = cloneTags(track.lastWritten)
    METADATA_FIELDS.forEach((field) => {
      next[field] = written[field] ?? ''
    })
    track.lastWritten = next
  }

  /**
   * 加载重命名/归档预览（FR-7、BIZ-030）：按模板与当前顺序提交所需字段，
   * 由后端规划目标路径与冲突；结果仅用于预览，不写入待写回态。
   */
  const loadRenamePreview = async (
    options: RenamePreviewOptions,
    scope: TrackItem[] = operationScope.value
  ): Promise<RenamePlanItem[]> => {
    previewingRename.value = true
    try {
      const result = await previewRename(
        options,
        scope.map((t) => ({
          id: t.id,
          trackNo: t.tags.trackNo,
          title: t.tags.title,
          artist: t.tags.artist,
          albumArtist: t.tags.albumArtist,
          album: t.tags.album,
          discNo: t.tags.discNo,
          year: t.tags.year
        }))
      )
      renamePreview.value = result.items
      importError.value = null
      return result.items
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
      renamePreview.value = []
      return []
    } finally {
      previewingRename.value = false
    }
  }

  /** 把预览中发生变化的项写入待写回路径态（内存，不落盘）（BIZ-030）。返回变更项数。 */
  const applyRenamePlan = (items: RenamePlanItem[]): number => {
    const next = { ...pendingPaths.value }
    items.forEach((item) => {
      if (item.changed) {
        next[item.id] = item.newPath
      } else {
        delete next[item.id]
      }
    })
    pendingPaths.value = next
    const touched = new Set(items.map((item) => item.id))
    tracks.value.filter((t) => touched.has(t.id)).forEach((t) => refreshStatus(t))
    return items.filter((item) => item.changed).length
  }

  /**
   * 立即执行归档（BIZ-030）：**直接移动文件、不经过待写回态**。
   *
   * <p>只在工作区无未写回内容时调用（归档入口已做禁用与守卫）。提交每首变更曲目的完整当前值与目标路径，
   * 后端按落盘基线自算差异（标签无变化，等同只做移动）；执行后文件**保留在工作区**并更新为新的路径与状态。
   */
  const commitArchive = async (items: RenamePlanItem[]): Promise<WriteOutcomeItem[]> => {
    const changed = items.filter((item) => item.changed)
    if (changed.length === 0) {
      return []
    }
    const byId = new Map(tracks.value.map((t) => [t.id, t]))
    const commands = changed.flatMap((item) => {
      const track = byId.get(item.id)
      if (!track) {
        return []
      }
      return [
        {
          id: track.id,
          tags: cloneTags(track.tags),
          cover: pendingCovers.value[track.id] === undefined ? null : pendingCovers.value[track.id],
          targetPath: item.newPath
        }
      ]
    })
    writing.value = true
    try {
      const result = await commitWriteBack(commands)
      lastWriteOutcomes.value = result.outcomes
      applyWriteOutcomes(result.outcomes)
      importError.value = null
      return result.outcomes
    } catch (error) {
      importError.value = error instanceof Error ? error.message : String(error)
      return []
    } finally {
      writing.value = false
    }
  }

  return {
    tracks,
    skipped,
    importing,
    importError,
    columns,
    selectedTrackId,
    selectedTrackIds,
    pendingCovers,
    selectedTrack,
    selectedTracks,
    operationScope,
    hasUncommittedInSelection,
    editingCell,
    uncommittedCount,
    isWorkspaceDirty,
    refreshWorkspace,
    importFromDialog,
    selectTrack,
    selectAllTracks,
    removeSelectedTracks,
    reorderTrack,
    clearWorkspaceSession,
    startInlineEdit,
    commitInlineEdit,
    cancelInlineEdit,
    navigateInlineEdit,
    applyLyricsToTrack,
    setPendingCover,
    applyBatchLyrics,
    applyBatchTags,
    applyMatchedTags,
    batchUpdateTags,
    batchApplyLyrics,
    renumberGroups,
    renumberTracks,
    rollbackTrack,
    rollbackAll,
    commitAllWrites,
    lastWriteOutcomes,
    writing,
    renamePreview,
    previewingRename,
    loadRenamePreview,
    applyRenamePlan,
    commitArchive,
    pendingPaths
  }
}
