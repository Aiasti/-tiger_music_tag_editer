import { app, safeStorage } from 'electron'
import { existsSync, readdirSync } from 'node:fs'
import { promises as fs } from 'node:fs'
import { join } from 'node:path'
import { getBackendConfig } from './backend'

/** 歌词源启停与优先级；首版仅 LRCLIB 可用，其余仅为持久化展示。 */
export interface LyricsSourceSetting {
  id: string
  enabled: boolean
  priority: number
}

/**
 * 主进程持有的设置（S5、BIZ-035）。
 *
 * <p>`acoustidApiKeyEncrypted` 的三种状态：
 * - `null`：未在本地配置过 Key（启动同步时**不发送**，保留后端环境变量值）；
 * - `''`：用户在设置中显式清除（同步时发送空串，后端清除 Key）；
 * - 其他：safeStorage 加密后的 base64。
 */
export interface StoredSettings {
  playerPath: string
  playerArgsTemplate: string
  /** 是否用固定 M3U8 播放列表（tiger trial）承载多选试听。 */
  useTrialPlaylist: boolean
  musicBrainzUserAgent: string
  fpcalcPath: string
  acoustidApiKeyEncrypted: string | null
  lyricsSources: LyricsSourceSetting[]
}

/** 渲染层可见的设置视图：API Key 只暴露「是否已配置」，不回传明文。 */
export interface PublicSettings {
  playerPath: string
  playerArgsTemplate: string
  useTrialPlaylist: boolean
  musicBrainzUserAgent: string
  fpcalcPath: string
  fpcalcEffectivePath: string
  acoustidKeyConfigured: boolean
  lyricsSources: LyricsSourceSetting[]
}

/** 保存设置：`acoustidApiKey` 缺省表示保持不变，空串表示清除。 */
export interface SaveSettingsPayload {
  playerPath: string
  playerArgsTemplate: string
  useTrialPlaylist: boolean
  musicBrainzUserAgent: string
  fpcalcPath: string
  acoustidApiKey?: string
  lyricsSources: LyricsSourceSetting[]
}

export interface SyncSettingsResult {
  synced: boolean
  error?: string
}

const DEFAULT_PLAYER_ARGS = '"%filepath%"'

const DEFAULT_LYRICS_SOURCES: LyricsSourceSetting[] = [
  { id: 'lrclib', enabled: true, priority: 1 },
  { id: 'netease', enabled: false, priority: 2 },
  { id: 'qq', enabled: false, priority: 3 },
  { id: 'applemusic', enabled: false, priority: 4 },
  { id: 'lastfm', enabled: false, priority: 5 }
]

let cached: StoredSettings | null = null

function settingsFile(): string {
  return join(app.getPath('userData'), 'settings.json')
}

function defaults(): StoredSettings {
  return {
    playerPath: '',
    playerArgsTemplate: DEFAULT_PLAYER_ARGS,
    useTrialPlaylist: true,
    musicBrainzUserAgent: '',
    fpcalcPath: '',
    acoustidApiKeyEncrypted: null,
    lyricsSources: DEFAULT_LYRICS_SOURCES.map((item) => ({ ...item }))
  }
}

function normalize(raw: Partial<StoredSettings>): StoredSettings {
  const base = defaults()
  return {
    playerPath: typeof raw.playerPath === 'string' ? raw.playerPath : base.playerPath,
    playerArgsTemplate:
      typeof raw.playerArgsTemplate === 'string' ? raw.playerArgsTemplate : base.playerArgsTemplate,
    useTrialPlaylist:
      typeof raw.useTrialPlaylist === 'boolean' ? raw.useTrialPlaylist : base.useTrialPlaylist,
    musicBrainzUserAgent:
      typeof raw.musicBrainzUserAgent === 'string' ? raw.musicBrainzUserAgent : base.musicBrainzUserAgent,
    fpcalcPath: typeof raw.fpcalcPath === 'string' ? raw.fpcalcPath : base.fpcalcPath,
    acoustidApiKeyEncrypted:
      raw.acoustidApiKeyEncrypted === null || typeof raw.acoustidApiKeyEncrypted === 'string'
        ? raw.acoustidApiKeyEncrypted
        : null,
    lyricsSources: Array.isArray(raw.lyricsSources)
      ? raw.lyricsSources
          .filter((item) => item && typeof item.id === 'string')
          .map((item) => ({
            id: item.id,
            enabled: Boolean(item.enabled),
            priority: Number.isFinite(item.priority) ? item.priority : 99
          }))
      : base.lyricsSources
  }
}

/** 读取设置；文件不存在或损坏时回退默认值，不阻断应用启动。 */
export async function loadSettings(): Promise<StoredSettings> {
  if (cached) {
    return cached
  }
  try {
    const text = await fs.readFile(settingsFile(), 'utf8')
    cached = normalize(JSON.parse(text) as Partial<StoredSettings>)
  } catch {
    cached = defaults()
  }
  return cached
}

function encryptApiKey(key: string): string {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('系统安全存储不可用，无法安全保存 AcoustID API Key')
  }
  return safeStorage.encryptString(key).toString('base64')
}

function decryptApiKey(encrypted: string): string | null {
  if (!encrypted) {
    return ''
  }
  try {
    return safeStorage.decryptString(Buffer.from(encrypted, 'base64'))
  } catch {
    // 解密失败时返回 null：调用方不得把它当成「用户显式清空」
    return null
  }
}

/** 原子写入设置文件（同目录临时文件 + rename）。 */
async function persist(settings: StoredSettings): Promise<void> {
  const file = settingsFile()
  const temp = `${file}.tmp`
  await fs.mkdir(app.getPath('userData'), { recursive: true })
  await fs.writeFile(temp, JSON.stringify(settings, null, 2), 'utf8')
  await fs.rename(temp, file)
  cached = settings
}

/**
 * fpcalc 解析优先级（BIZ-035）：用户覆盖 > 打包资源 > 开发本地目录 > PATH。
 *
 * <p>开发目录兼容 Chromaprint 官方压缩包解压后的单层子目录
 * （`tools/fpcalc/<版本目录>/fpcalc.exe`）。
 */
export function resolveFpcalcPath(override: string): string {
  const value = override.trim()
  if (value) {
    return value
  }
  const executable = process.platform === 'win32' ? 'fpcalc.exe' : 'fpcalc'
  if (app.isPackaged) {
    const bundled = join(process.resourcesPath, 'fpcalc', executable)
    if (existsSync(bundled)) {
      return bundled
    }
  }
  const localAppData = process.env['LOCALAPPDATA']
  if (localAppData) {
    const root = join(localAppData, 'TigerTag', 'tools', 'fpcalc')
    const direct = join(root, executable)
    if (existsSync(direct)) {
      return direct
    }
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (!entry.isDirectory()) {
          continue
        }
        const nested = join(root, entry.name, executable)
        if (existsSync(nested)) {
          return nested
        }
      }
    } catch {
      // 目录不存在时回退到 PATH
    }
  }
  return 'fpcalc'
}

export function toPublicSettings(settings: StoredSettings): PublicSettings {
  return {
    playerPath: settings.playerPath,
    playerArgsTemplate: settings.playerArgsTemplate,
    useTrialPlaylist: settings.useTrialPlaylist,
    musicBrainzUserAgent: settings.musicBrainzUserAgent,
    fpcalcPath: settings.fpcalcPath,
    fpcalcEffectivePath: resolveFpcalcPath(settings.fpcalcPath),
    acoustidKeyConfigured: Boolean(settings.acoustidApiKeyEncrypted),
    lyricsSources: settings.lyricsSources.map((item) => ({ ...item }))
  }
}

export async function saveSettings(payload: SaveSettingsPayload): Promise<PublicSettings> {
  const current = await loadSettings()
  const next: StoredSettings = {
    playerPath: payload.playerPath.trim(),
    playerArgsTemplate: payload.playerArgsTemplate.trim() || DEFAULT_PLAYER_ARGS,
    useTrialPlaylist: payload.useTrialPlaylist,
    musicBrainzUserAgent: payload.musicBrainzUserAgent.trim(),
    fpcalcPath: payload.fpcalcPath.trim(),
    acoustidApiKeyEncrypted:
      payload.acoustidApiKey === undefined
        ? current.acoustidApiKeyEncrypted
        : payload.acoustidApiKey === ''
          ? ''
          : encryptApiKey(payload.acoustidApiKey),
    lyricsSources: payload.lyricsSources.map((item) => ({ ...item }))
  }
  await persist(next)
  return toPublicSettings(next)
}

/** 仅供主进程向后端同步，不通过 IPC 暴露；解密失败返回 null。 */
export async function readApiKey(): Promise<string | null> {
  const settings = await loadSettings()
  return settings.acoustidApiKeyEncrypted
    ? decryptApiKey(settings.acoustidApiKeyEncrypted)
    : ''
}

/**
 * 把在线相关设置下发给本地后端（S5-2）。
 *
 * <p>Key 为 `null` 时不下发，保留后端环境变量配置；空串表示显式清除。
 */
export async function syncOnlineSettings(): Promise<SyncSettingsResult> {
  const settings = await loadSettings()
  const { baseUrl, token } = getBackendConfig()
  const body: Record<string, unknown> = {
    fpcalcPath: resolveFpcalcPath(settings.fpcalcPath)
  }
  if (settings.musicBrainzUserAgent.trim()) {
    body.musicBrainzUserAgent = settings.musicBrainzUserAgent.trim()
  }
  if (settings.acoustidApiKeyEncrypted !== null) {
    const key = await readApiKey()
    // 解密失败（null）时不下发，避免误清空后端已有 Key
    if (key !== null) {
      body.acoustidApiKey = key
    }
  }
  try {
    const response = await fetch(`${baseUrl}/api/settings/online`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-App-Token': token
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(3000)
    })
    if (!response.ok) {
      return { synced: false, error: `HTTP ${response.status}` }
    }
    return { synced: true }
  } catch (error) {
    return { synced: false, error: error instanceof Error ? error.message : String(error) }
  }
}
