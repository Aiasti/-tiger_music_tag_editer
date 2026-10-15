import { ref, computed } from 'vue'
import type { TrackItem, TrackTagData, EditableTagField, ColumnDefinition, SkipItem } from '../types'
import {
  clearWorkspace as clearWorkspaceApi,
  fetchWorkspace,
  importPaths,
  removeTracks,
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

  const applyView = (view: WorkspaceView) => {
    tracks.value = view.tracks
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

  // 重新编号与碟号处理 (BIZ-003)；作用于当前选区（BIZ-015）
  const renumberTracks = (discNumber: number, startTrack: number, autoFillTotal: boolean) => {
    const list = operationScope.value
    const totalCount = list.length
    list.forEach((track, idx) => {
      const curTrackNum = startTrack + idx
      track.tags.trackNo = autoFillTotal ? `${curTrackNum}/${totalCount}` : `${curTrackNum}`
      track.tags.discNo = `${discNumber}`
      track.status = 'edited'
    })
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
        track.status = 'edited'
      }
    })
  }

  // 批量应用歌词至曲目 (BIZ-012)；作用于当前选区（BIZ-015）
  const batchApplyLyrics = (lyricsText: string) => {
    const list = operationScope.value
    list.forEach((track) => {
      track.tags.lyrics = lyricsText
      track.status = 'edited'
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
        if (track.status !== 'failed') {
          track.status = 'edited'
        }
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
    track.status = 'edited'
  }

  // 更新内嵌歌词（进入内存编辑态，严禁直接落盘）
  const applyLyricsToTrack = (trackId: number, lyricsText: string) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (track && track.status !== 'failed') {
      track.tags.lyrics = lyricsText
      track.status = 'edited'
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
        t.status = 'edited'
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
      }
    })
  }

  // 提交前回滚至首次快照 (BIZ-006 / FR-8)
  const rollbackTrack = (trackId: number) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (track && track.status !== 'failed') {
      track.tags = JSON.parse(JSON.stringify(track.initialSnapshot))
      track.status = 'saved'
    }
    if (pendingCovers.value[trackId] !== undefined) {
      const next = { ...pendingCovers.value }
      delete next[trackId]
      pendingCovers.value = next
    }
  }

  const rollbackAll = () => {
    // 顺序先恢复为导入顺序（曲目 id 按导入递增，升序即导入顺序），再恢复首次快照（BIZ-008 / BIZ-006）
    tracks.value = [...tracks.value].sort((a, b) => a.id - b.id)
    tracks.value.forEach((t) => {
      if (t.status !== 'failed') {
        t.tags = JSON.parse(JSON.stringify(t.initialSnapshot))
        t.status = 'saved'
      }
    })
    // 封面同属媒体字段编辑态，回滚一并丢弃待写回值（BIZ-020 / BIZ-021）
    pendingCovers.value = {}
  }

  // 确认写回落盘（将当前编辑值置为已保存）
  const commitDiskWriteAll = () => {
    tracks.value.forEach((t) => {
      if (t.status === 'edited' || t.status === 'matched') {
        t.initialSnapshot = JSON.parse(JSON.stringify(t.tags))
        t.status = 'saved'
      }
    })
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
    renumberTracks,
    rollbackTrack,
    rollbackAll,
    commitDiskWriteAll,
    commitAllWrites: commitDiskWriteAll
  }
}
