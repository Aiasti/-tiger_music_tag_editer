import { ref, computed } from 'vue'
import type { TrackItem, TrackTagData, EditableTagField, ColumnDefinition, SkipItem } from '../types'
import { fetchWorkspace, importPaths, type WorkspaceView } from '../api/client'

// 字段列定义（12 个核心元数据列 + 歌词按钮列 + 行号/状态）
const initialColumns: ColumnDefinition[] = [
  { key: 'id', label: '#', minWidth: 42, visible: true, editable: false },
  { key: 'status', label: '状态', minWidth: 68, visible: true, editable: false },
  { key: 'trackNo', label: '音轨', minWidth: 55, visible: true, editable: true },
  { key: 'discNo', label: '碟号', minWidth: 55, visible: true, editable: true },
  { key: 'title', label: '标题', minWidth: 160, visible: true, editable: true },
  { key: 'artist', label: '艺术家', minWidth: 120, visible: true, editable: true },
  { key: 'album', label: '专辑', minWidth: 140, visible: true, editable: true },
  { key: 'albumArtist', label: '专辑艺术家', minWidth: 110, visible: true, editable: true },
  { key: 'year', label: '年份', minWidth: 65, visible: true, editable: true },
  { key: 'genre', label: '流派', minWidth: 90, visible: true, editable: true },
  { key: 'composer', label: '作曲', minWidth: 95, visible: true, editable: true },
  { key: 'lyricist', label: '作词', minWidth: 95, visible: true, editable: true },
  { key: 'arranger', label: '编曲', minWidth: 95, visible: true, editable: true },
  { key: 'lyricsBtn', label: '内嵌歌词', minWidth: 100, visible: true, editable: false },
  { key: 'comment', label: '注释', minWidth: 130, visible: true, editable: true },
  { key: 'duration', label: '时长', minWidth: 60, visible: true, editable: false }
]

// 工作区数据来自后端（S1：导入与展示；编辑能力仍为内存态，写回属 S2）
const tracks = ref<TrackItem[]>([])
const skipped = ref<SkipItem[]>([])
const importing = ref(false)
const importError = ref<string | null>(null)
const columns = ref<ColumnDefinition[]>(initialColumns)
const selectedTrackId = ref<number | null>(null)

// 行内编辑状态
const editingCell = ref<{
  trackId: number
  field: EditableTagField
} | null>(null)

export function useWorkspace() {
  const selectedTrack = computed(() => {
    return tracks.value.find((t) => t.id === selectedTrackId.value) || null
  })

  const selectedTracks = computed(() => {
    return selectedTrack.value ? [selectedTrack.value] : []
  })

  const uncommittedCount = computed(() => {
    return tracks.value.filter((t) => t.status === 'edited' || t.status === 'matched').length
  })

  const isWorkspaceDirty = computed(() => uncommittedCount.value > 0)

  const applyView = (view: WorkspaceView) => {
    tracks.value = view.tracks
    skipped.value = view.skipped
    if (tracks.value.length > 0 && selectedTrackId.value === null) {
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

  // 选中某行
  const selectTrack = (trackId: number) => {
    selectedTrackId.value = trackId
  }

  // 重新编号与碟号处理 (BIZ-003)
  const renumberTracks = (discNumber: number, startTrack: number, autoFillTotal: boolean) => {
    const list = selectedTracks.value.length > 0 ? selectedTracks.value : tracks.value.filter((t) => t.status !== 'failed')
    const totalCount = list.length
    list.forEach((track, idx) => {
      const curTrackNum = startTrack + idx
      track.tags.trackNo = autoFillTotal ? `${curTrackNum}/${totalCount}` : `${curTrackNum}`
      track.tags.discNo = `${discNumber}`
      track.status = 'edited'
    })
  }

  // 批量更新标签 (FR-4)
  const batchUpdateTags = (updates: Partial<Record<EditableTagField, string>>) => {
    const list = selectedTracks.value.length > 0 ? selectedTracks.value : tracks.value.filter((t) => t.status !== 'failed')
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

  // 批量应用歌词至曲目 (BIZ-012)
  const batchApplyLyrics = (lyricsText: string) => {
    const list = selectedTracks.value.length > 0 ? selectedTracks.value : tracks.value.filter((t) => t.status !== 'failed')
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

  // 批量修改元数据字段 (FR-4)
  const applyBatchTags = (
    fields: Partial<TrackTagData>,
    clearFields: (keyof TrackTagData)[] = []
  ) => {
    tracks.value.forEach((t) => {
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

  // 提交前回滚至首次快照 (BIZ-006 / FR-8)
  const rollbackTrack = (trackId: number) => {
    const track = tracks.value.find((t) => t.id === trackId)
    if (track && track.status !== 'failed') {
      track.tags = JSON.parse(JSON.stringify(track.initialSnapshot))
      track.status = 'saved'
    }
  }

  const rollbackAll = () => {
    tracks.value.forEach((t) => {
      if (t.status !== 'failed') {
        t.tags = JSON.parse(JSON.stringify(t.initialSnapshot))
        t.status = 'saved'
      }
    })
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
    selectedTrack,
    selectedTracks,
    editingCell,
    uncommittedCount,
    isWorkspaceDirty,
    refreshWorkspace,
    importFromDialog,
    selectTrack,
    startInlineEdit,
    commitInlineEdit,
    cancelInlineEdit,
    navigateInlineEdit,
    applyLyricsToTrack,
    applyBatchLyrics,
    applyBatchTags,
    batchUpdateTags,
    batchApplyLyrics,
    renumberTracks,
    rollbackTrack,
    rollbackAll,
    commitDiskWriteAll,
    commitAllWrites: commitDiskWriteAll
  }
}
