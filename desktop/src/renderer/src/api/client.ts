import type { TrackItem, SkipItem, TrackTagData, WriteOutcomeItem } from '../types'

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

/** 单首曲目的写回提交内容（BIZ-028）：整份当前标签值 + 封面意图。 */
export interface WriteCommandItem {
  id: number
  tags: TrackTagData
  /** null=保持现状、""=移除封面、否则为 data URL。 */
  cover: string | null
}

/** 写回结果（与提交清单同序）。 */
export interface WriteBackResult {
  outcomes: WriteOutcomeItem[]
}

/** 写回标签到磁盘（FR-8、DEV-003、BIZ-028）：后端按落盘基线自算差异并逐文件五阶段执行。 */
export function commitWriteBack(tracks: WriteCommandItem[]): Promise<WriteBackResult> {
  return request<WriteBackResult>('/api/workspace/commit', {
    method: 'POST',
    body: JSON.stringify({ tracks })
  })
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
