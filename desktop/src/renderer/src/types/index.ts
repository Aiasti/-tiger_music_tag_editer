/**
 * Tiger Music Tag Editor - 前端契约与数据模型类型定义
 * 依据 PRD (FR-1 ~ FR-9), dev.md 应用层契约草案及 BIZ-001 ~ BIZ-012 业务规范
 */

export type TrackStatus = 'saved' | 'edited' | 'matched' | 'failed'

export interface TrackTagData {
  title: string
  artist: string
  album: string
  albumArtist: string
  trackNo: string
  discNo: string
  year: string
  genre: string
  composer: string
  lyricist: string
  arranger: string
  comment: string
  lyrics: string
}

export interface TrackItem {
  id: number
  filename: string
  ext: string
  filePath: string
  duration: string
  size: string
  hasCover: boolean
  status: TrackStatus
  failureReason?: string
  tags: TrackTagData
  initialSnapshot: TrackTagData // BIZ-006: 首次导入快照，用于提交前回滚
  lastWritten: TrackTagData // DEV-002: 最近成功落盘值；待提交差异与已更改标记均以它为基准
}

export type EditableTagField = keyof Omit<TrackTagData, 'lyrics'>

export interface ColumnDefinition {
  key: keyof TrackTagData | 'id' | 'filename' | 'status' | 'duration'
  label: string
  minWidth: number
  width: number
  visible: boolean
  editable: boolean
}

export type ModalType =
  | 'skip-list'
  | 'renumber-confirm'
  | 'batch-edit'
  | 'online-match'
  | 'lyrics-search'
  | 'cover-edit'
  | 'rename-preview'
  | 'write-confirm'
  | 'settings'

export type LyricsSourceKey = 'netease' | 'qqmusic' | 'apple' | 'lastfm' | 'kugou' | 'lrcdb' | 'skip'

export interface LyricsCandidateItem {
  id: string
  source: string
  title: string
  artist: string
  album: string
  duration: string
  hasTimeline: boolean
  confidence: string
  preview: string
}

export interface MetadataCandidateItem {
  source: string
  title: string
  artist: string
  album: string
  trackNo: string
  discNo: string
  year: string
  genre: string
  score: string
  acoustIdConfidence: string
}

export interface SkipItem {
  filename: string
  path: string
  reason: string
}

export interface RenamePreviewItem {
  id: number
  oldPath: string
  newPath: string
  hasConflict: boolean
}

export interface WriteDiffItem {
  id: number
  filename: string
  diffFields: string[]
  oldPath: string
  newPath: string
}

/** 写回结果状态（DEV-003、BIZ-028）；与后端 `WriteOutcomeStatus` 契约值一致。 */
export type WriteOutcomeStatus =
  | 'written'
  | 'stale'
  | 'blocked'
  | 'failed-restored'
  | 'failed-manual'

/** 单个曲目的写回结果。 */
export interface WriteOutcomeItem {
  id: number
  outcome: WriteOutcomeStatus
  reason?: string | null
  detail?: string | null
  /** 写入后磁盘上的实际标签值（仅 written 时有值）。 */
  tags?: TrackTagData | null
  filePath?: string | null
  hasCover: boolean
}
