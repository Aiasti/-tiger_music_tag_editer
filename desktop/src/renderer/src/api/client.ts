import type { TrackItem, SkipItem } from '../types'

/** 后端工作区视图（与 Spring Boot 返回契约一致）。 */
export interface WorkspaceView {
  tracks: TrackItem[]
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
