import { app } from 'electron'
import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { createWriteStream, existsSync, mkdirSync, type WriteStream } from 'node:fs'
import { createServer } from 'node:net'
import { dirname, join } from 'node:path'

/**
 * 内置后端生命周期（S6）。
 *
 * <p>打包后由主进程用随包 jlink 运行时拉起后端 jar：动态选择端口、生成随机
 * token，等待 `/api/health` 就绪后再创建窗口；退出应用时结束后端进程。
 * 开发态默认仍连接外部后端，设置 `TIGER_USE_BUNDLED_BACKEND=1` 可验证随包链路。
 */
export interface BackendConfig {
  baseUrl: string
  token: string
}

const DEFAULT_BASE_URL = 'http://127.0.0.1:8080'
const DEFAULT_TOKEN = 'dev-local-token'
const STARTUP_TIMEOUT_MS = 60_000
const HEALTH_INTERVAL_MS = 500

let config: BackendConfig | null = null
let child: ChildProcess | null = null
let logStream: WriteStream | null = null
let starting: Promise<BackendConfig> | null = null

function useBundledBackend(): boolean {
  return app.isPackaged || process.env['TIGER_USE_BUNDLED_BACKEND'] === '1'
}

function bundledRoot(): string {
  return app.isPackaged ? process.resourcesPath : join(app.getAppPath(), 'build')
}

function executableName(name: string): string {
  return process.platform === 'win32' ? `${name}.exe` : name
}

/** 未启用内置后端时，沿用开发期的外部服务配置。 */
export function getBackendConfig(): BackendConfig {
  if (config) {
    return config
  }
  return {
    baseUrl: process.env['TIGER_BACKEND_URL'] ?? DEFAULT_BASE_URL,
    token: process.env['TIGER_APP_TOKEN'] ?? DEFAULT_TOKEN
  }
}

function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.unref()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      const port = typeof address === 'object' && address ? address.port : 0
      server.close(() => (port > 0 ? resolve(port) : reject(new Error('无法分配后端端口'))))
    })
  })
}

async function waitForHealth(baseUrl: string): Promise<void> {
  const deadline = Date.now() + STARTUP_TIMEOUT_MS
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseUrl}/api/health`, {
        signal: AbortSignal.timeout(2_000)
      })
      if (response.ok) {
        return
      }
    } catch {
      // 后端尚未就绪，继续轮询
    }
    await new Promise((resolve) => setTimeout(resolve, HEALTH_INTERVAL_MS))
  }
  throw new Error(`后端在 ${STARTUP_TIMEOUT_MS / 1000} 秒内未就绪`)
}

function openLogStream(): WriteStream {
  const logDir = join(app.getPath('userData'), 'logs')
  mkdirSync(logDir, { recursive: true })
  logStream = createWriteStream(join(logDir, 'backend.log'), { flags: 'a' })
  return logStream
}

async function launchBundledBackend(): Promise<BackendConfig> {
  const root = bundledRoot()
  const java = join(root, 'runtime', 'bin', executableName('java'))
  const jar = join(root, 'backend', 'backend.jar')
  const fpcalc = join(root, 'fpcalc', executableName('fpcalc'))
  if (!existsSync(java)) {
    throw new Error(`未找到内置 Java 运行时：${java}`)
  }
  if (!existsSync(jar)) {
    throw new Error(`未找到后端程序：${jar}`)
  }

  const port = await freePort()
  const token = randomBytes(24).toString('hex')
  const baseUrl = `http://127.0.0.1:${port}`
  const stream = openLogStream()
  stream.write(`\n[${new Date().toISOString()}] starting backend on ${baseUrl}\n`)

  const env: NodeJS.ProcessEnv = {
    ...process.env,
    TIGER_SERVER_PORT: String(port),
    TIGER_APP_TOKEN: token
  }
  if (existsSync(fpcalc)) {
    env.TIGER_FPCALC_PATH = fpcalc
  }

  child = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar], {
    cwd: dirname(jar),
    windowsHide: true,
    env
  })
  child.stdout?.pipe(stream)
  child.stderr?.pipe(stream)
  child.on('exit', (code, signal) => {
    stream.write(`[${new Date().toISOString()}] backend exited code=${code} signal=${signal}\n`)
    child = null
  })

  await waitForHealth(baseUrl)
  config = { baseUrl, token }
  stream.write(`[${new Date().toISOString()}] backend ready\n`)
  return config
}

/** 启动后端；重复调用复用同一个启动过程。 */
export async function startBackend(): Promise<BackendConfig> {
  if (!useBundledBackend()) {
    return getBackendConfig()
  }
  if (config) {
    return config
  }
  if (!starting) {
    starting = launchBundledBackend().finally(() => {
      starting = null
    })
  }
  return starting
}

/** 结束后端进程；Windows 下同时清理子进程树。 */
export function stopBackend(): void {
  const processToStop = child
  child = null
  config = null
  if (!processToStop?.pid) {
    return
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(processToStop.pid), '/T', '/F'], {
      windowsHide: true,
      stdio: 'ignore'
    })
  } else {
    processToStop.kill()
  }
  logStream?.end()
  logStream = null
}
