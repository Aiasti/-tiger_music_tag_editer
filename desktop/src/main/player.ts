import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'

/**
 * 解析参数模板（FR-9）。
 *
 * <p>支持 `%filepath%` 占位符与双引号包裹；多选时占位符展开为多个带引号的路径。
 * 不做 shell 解析，避免命令注入。含空格的路径会自动以引号包裹，因此在模板中写
 * `"%filepath%"` 或 `%filepath%` 均可。
 */
export function splitArgs(template: string, filePaths: string[]): string[] {
  const quotedPaths = filePaths.map((path) => `"${path}"`).join(' ')
  const replaced = template
    .replaceAll('"%filepath%"', quotedPaths)
    .replaceAll("'%filepath%'", quotedPaths)
    .replaceAll('%filepath%', quotedPaths)

  const args: string[] = []
  let token = ''
  let inQuotes = false
  for (const char of replaced) {
    if (char === '"') {
      inQuotes = !inQuotes
      continue
    }
    if (!inQuotes && /\s/.test(char)) {
      if (token) {
        args.push(token)
        token = ''
      }
      continue
    }
    token += char
  }
  if (inQuotes) {
    throw new Error('参数模板中的双引号不匹配')
  }
  if (token) {
    args.push(token)
  }
  return args
}

/** 用外部播放器打开指定音频文件（支持多选）；不等待播放器退出。 */
export async function launchPlayer(
  executablePath: string,
  argsTemplate: string,
  filePaths: string[]
): Promise<void> {
  const executable = executablePath.trim()
  if (!executable) {
    throw new Error('尚未配置外部播放器路径')
  }
  if (!existsSync(executable)) {
    throw new Error(`外部播放器不存在：${executable}`)
  }
  if (filePaths.length === 0) {
    throw new Error('没有可播放的曲目')
  }
  const missing = filePaths.find((path) => !existsSync(path))
  if (missing) {
    throw new Error(`音频文件不存在：${missing}`)
  }

  const args = splitArgs(argsTemplate.trim() || '"%filepath%"', filePaths)
  const child = spawn(executable, args, {
    detached: true,
    stdio: 'ignore',
    shell: false,
    windowsHide: false
  })
  await new Promise<void>((resolve, reject) => {
    child.once('error', (error) => reject(new Error(`启动外部播放器失败：${error.message}`)))
    child.once('spawn', () => resolve())
  })
  child.unref()
}

/**
 * 向已运行的 foobar2000 发送控制命令（/stop 停止播放、/exit 退出）。
 *
 * <p>仅 foobar2000 原生支持这两个开关；其他播放器可能忽略或报错。
 */
export async function sendPlayerCommand(
  executablePath: string,
  command: '/stop' | '/exit'
): Promise<void> {
  const executable = executablePath.trim()
  if (!executable) {
    throw new Error('尚未配置外部播放器路径')
  }
  if (!existsSync(executable)) {
    throw new Error(`外部播放器不存在：${executable}`)
  }
  const child = spawn(executable, [command], {
    detached: true,
    stdio: 'ignore',
    shell: false
  })
  await new Promise<void>((resolve, reject) => {
    child.once('error', (error) => reject(new Error(`发送播放器命令失败：${error.message}`)))
    child.once('spawn', () => resolve())
  })
  child.unref()
}
