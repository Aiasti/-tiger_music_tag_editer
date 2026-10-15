import type { TrackItem, SkipItem } from '../types'

/** 后端工作区视图（与 Spring Boot 返回契约一致）。 */
export interface WorkspaceView {
  tracks: TrackItem[]
  skipped: SkipItem[]
}

interface BackendConfig {
  baseUrl: string
  token: string
  resetWorkspace: boolean
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

/** 是否需要在应用启动时清空工作区（BIZ-017：工作区不跨重启留存）。 */
export async function shouldClearWorkspaceOnStart(): Promise<boolean> {
  const { resetWorkspace } = await backendConfig()
  return resetWorkspace
}

/** 清空工作区（会话内状态，BIZ-017）。 */
export function clearWorkspace(): Promise<WorkspaceView> {
  return request<WorkspaceView>('/api/workspace/clear', { method: 'POST' })
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
