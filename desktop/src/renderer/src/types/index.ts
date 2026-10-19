/**
 * Tiger Music Tag Editor - 前端契约与数据模型类型定义
 * 依据 PRD (FR-1 ~ FR-9), dev.md 应用层契约草案及 BIZ-001 ~ BIZ-012 业务规范
 */

export type TrackStatus = 'saved' | 'edited' | 'failed'

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
  | 'cover-match'
  | 'lyrics-search'
  | 'cover-edit'
  | 'rename'
  | 'archive'
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
  confidence: number
  preview: string
}

/** 在线元数据候选（FR-5、DEV-004）；与后端 `MetadataCandidate` 契约一致。 */
export interface MetadataCandidateItem {
  id: string
  source: string
  score: number
  title: string
  artist: string
  album: string
  albumArtist: string
  trackNo: string
  discNo: string
  year: string
  genre: string
  releaseId?: string | null
  releaseGroupId?: string | null
  detail?: string | null
}

/** 单曲元数据匹配结果；warnings 为可展示的降级/失败原因。 */
export interface MetadataMatchResult {
  trackId: number
  keyword: string
  candidates: MetadataCandidateItem[]
  warnings: string[]
  acoustidAvailable: boolean
  acoustidUsed: boolean
}

/** 单曲歌词检索结果。 */
export interface LyricsMatchResult {
  trackId: number
  keyword: string
  candidates: LyricsCandidateItem[]
  warnings: string[]
}

/** 在线封面候选（FR-6、DEV-004）；只提供封面，不携带歌词。 */
export interface CoverCandidateItem {
  id: string
  source: string
  score: number
  title: string
  artist: string
  album: string
  year: string
  releaseId?: string | null
  releaseGroupId?: string | null
  /** 可直接由后端代理下载的图片地址（iTunes 源）；Cover Art Archive 候选为空。 */
  imageUrl?: string | null
  detail?: string | null
}

/** 单曲封面匹配结果。 */
export interface CoverMatchResult {
  trackId: number
  keyword: string
  candidates: CoverCandidateItem[]
  warnings: string[]
}

export interface SkipItem {
  filename: string
  path: string
  reason: string
}

/** 重命名预览项（FR-7、BIZ-029）；与后端 `RenamePlanItem` 契约一致。 */
export interface RenamePlanItem {
  id: number
  oldPath: string
  newPath: string
  oldName: string
  newName: string
  changed: boolean
  adjusted: boolean
  note?: string | null
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
  | 'partial'
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
