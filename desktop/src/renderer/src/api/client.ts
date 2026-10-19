import type {
  TrackItem,
  SkipItem,
  TrackTagData,
  WriteOutcomeItem,
  RenamePlanItem,
  MetadataMatchResult,
  LyricsMatchResult,
  CoverMatchResult,
  CoverCandidateItem
} from '../types'

/** 后端返回的曲目视图：不含渲染层独有的「最近成功落盘值」（DEV-002、BIZ-026）。 */
export type TrackApiItem = Omit<TrackItem, 'lastWritten'>

/** 后端工作区视图（与 Spring Boot 返回契约一致）。 */
export interface WorkspaceView {
  tracks: TrackApiItem[]
  skipped: SkipItem[]
}

interface BackendConfig {
  baseUrl: string
  token: string
}

let cachedConfig: Promise<BackendConfig> | null = null

/** 后端地址与访问 token 由主进程注入（BIZ-013），不在渲染层散读环境变量。 */
async function backendConfig(): Promise<BackendConfig> {
  if (!cachedConfig) {
    cachedConfig = window.api.getBackendConfig()
  }
  return cachedConfig
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const { baseUrl, token } = await backendConfig()
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-App-Token': token,
      ...(init?.headers ?? {})
    }
  })
  if (!response.ok) {
    throw new Error(`后端请求失败：HTTP ${response.status} ${response.statusText}`)
  }
  return (await response.json()) as T
}

export function fetchWorkspace(): Promise<WorkspaceView> {
  return request<WorkspaceView>('/api/workspace')
}

export function importPaths(paths: string[], recursive: boolean): Promise<WorkspaceView> {
  return request<WorkspaceView>('/api/workspace/import', {
    method: 'POST',
    body: JSON.stringify({ paths, recursive })
  })
}

/** 将曲目移出工作区（仅会话内，不改磁盘，BIZ-015）。 */
export function removeTracks(ids: number[]): Promise<WorkspaceView> {
  return request<WorkspaceView>('/api/workspace/remove', {
    method: 'POST',
    body: JSON.stringify({ ids })
  })
}

/** 清空工作区（会话内状态；每次渲染层加载都视为新会话，BIZ-017、BIZ-027）。 */
export function clearWorkspace(): Promise<WorkspaceView> {
  return request<WorkspaceView>('/api/workspace/clear', { method: 'POST' })
}

/** 单首曲目的写回提交内容（BIZ-028、BIZ-030）：整份当前标签值 + 封面意图 + 可选目标路径。 */
export interface WriteCommandItem {
  id: number
  tags: TrackTagData
  /** null=保持现状、""=移除封面、否则为 data URL。 */
  cover: string | null
  /** 重命名/归档产生的目标完整路径（后端字段名 `targetPath`）；null 表示不改路径。 */
  targetPath: string | null
}

/** 写回结果（与提交清单同序）。 */
export interface WriteBackResult {
  outcomes: WriteOutcomeItem[]
}

/** 写回标签到磁盘（FR-8、DEV-003、BIZ-028、BIZ-030）：后端按落盘基线自算差异并逐文件执行。 */
export function commitWriteBack(tracks: WriteCommandItem[]): Promise<WriteBackResult> {
  return request<WriteBackResult>('/api/workspace/commit', {
    method: 'POST',
    body: JSON.stringify({ tracks })
  })
}

/** 重命名/归档预览的入参（BIZ-030）。 */
export interface RenamePreviewOptions {
  mode: 'rename' | 'archive'
  nameTemplate?: string | null
  dirTemplate?: string | null
  rootDir?: string | null
  archiveMode?: 'keep' | 'flat' | 'template' | null
}

/** 模板渲染所需字段。 */
export interface RenamePreviewTrack {
  id: number
  trackNo?: string
  title?: string
  artist?: string
  albumArtist?: string
  album?: string
  discNo?: string
  year?: string
}

/** 重命名/归档预览（FR-7、BIZ-030）：按当前编辑值与模板规划目标路径，不移动文件。 */
export function previewRename(
  options: RenamePreviewOptions,
  tracks: RenamePreviewTrack[]
): Promise<{ items: RenamePlanItem[] }> {
  return request<{ items: RenamePlanItem[] }>('/api/workspace/rename-preview', {
    method: 'POST',
    body: JSON.stringify({ ...options, tracks })
  })
}

/** 单曲元数据匹配（FR-5、DEV-004）：把当前编辑值交给后端，由后端访问 MusicBrainz/AcoustID。 */
export function matchMetadata(track: TrackItem, keyword = ''): Promise<MetadataMatchResult> {
  return request<MetadataMatchResult>('/api/match/metadata', {
    method: 'POST',
    body: JSON.stringify({
      trackId: track.id,
      title: track.tags.title,
      artist: track.tags.artist,
      album: track.tags.album,
      albumArtist: track.tags.albumArtist,
      trackNo: track.tags.trackNo,
      discNo: track.tags.discNo,
      year: track.tags.year,
      keyword
    })
  })
}

/** 单曲歌词检索（FR-6、DEV-004）：首版由后端查询 LRCLIB。 */
export function matchLyrics(track: TrackItem, keyword = ''): Promise<LyricsMatchResult> {
  return request<LyricsMatchResult>('/api/match/lyrics', {
    method: 'POST',
    body: JSON.stringify({
      trackId: track.id,
      title: track.tags.title,
      artist: track.tags.artist,
      album: track.tags.album,
      keyword
    })
  })
}

/** 单曲封面匹配（FR-6、DEV-004）：复用元数据/指纹结果，由后端查询 Cover Art Archive。 */
export function matchCover(track: TrackItem, keyword = ''): Promise<CoverMatchResult> {
  return request<CoverMatchResult>('/api/match/cover', {
    method: 'POST',
    body: JSON.stringify({
      trackId: track.id,
      title: track.tags.title,
      artist: track.tags.artist,
      album: track.tags.album,
      albumArtist: track.tags.albumArtist,
      trackNo: track.tags.trackNo,
      discNo: track.tags.discNo,
      year: track.tags.year,
      keyword
    })
  })
}

/** 取封面候选图片；返回用于预览的 object URL 与用于写回的 data URL，404 表示没有封面。 */
export async function fetchCoverCandidate(
  candidate: CoverCandidateItem
): Promise<{ objectUrl: string; dataUrl: string } | null> {
  const { releaseGroupId, releaseId, imageUrl } = candidate
  if (!releaseGroupId && !releaseId && !imageUrl) {
    return null
  }
  const { baseUrl, token } = await backendConfig()
  const params = new URLSearchParams()
  if (imageUrl) {
    params.set('imageUrl', imageUrl)
  } else {
    if (releaseGroupId) {
      params.set('releaseGroupId', releaseGroupId)
    }
    if (releaseId) {
      params.set('releaseId', releaseId)
    }
  }
  const response = await fetch(`${baseUrl}/api/match/cover/image?${params.toString()}`, {
    headers: { 'X-App-Token': token }
  })
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`封面图片请求失败：HTTP ${response.status}`)
  }
  const blob = await response.blob()
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
  return { objectUrl: URL.createObjectURL(blob), dataUrl }
}

/** 取曲目内嵌封面并转为可直接展示的对象 URL；无封面返回 null。 */
export async function fetchCoverUrl(trackId: number): Promise<string | null> {
  const { baseUrl, token } = await backendConfig()
  const response = await fetch(`${baseUrl}/api/workspace/tracks/${trackId}/cover`, {
    headers: { 'X-App-Token': token }
  })
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`封面请求失败：HTTP ${response.status}`)
  }
  return URL.createObjectURL(await response.blob())
}
