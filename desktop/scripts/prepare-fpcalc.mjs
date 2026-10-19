import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 把 fpcalc 复制到 electron-builder 的 extraResources 源目录（S5、BIZ-035）。
 *
 * <p>二进制不入库：默认从 `%LOCALAPPDATA%\TigerTag\tools\fpcalc` 读取，
 * 兼容 Chromaprint 官方压缩包解压后的单层子目录；也可用 `TIGER_FPCALC_SOURCE` 指定。
 * 仅打包前运行，不参与开发构建。
 */
const desktopRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const executable = process.platform === 'win32' ? 'fpcalc.exe' : 'fpcalc'

function findInDevTools() {
  if (!process.env.LOCALAPPDATA) {
    return ''
  }
  const root = join(process.env.LOCALAPPDATA, 'TigerTag', 'tools', 'fpcalc')
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
    return ''
  }
  return ''
}

const source = process.env.TIGER_FPCALC_SOURCE || findInDevTools()

if (!source || !existsSync(source)) {
  console.error(`[prepare-fpcalc] 未找到 fpcalc：${source || '(未设置 TIGER_FPCALC_SOURCE)'}`)
  console.error('[prepare-fpcalc] 请设置 TIGER_FPCALC_SOURCE 或准备 %LOCALAPPDATA%\\TigerTag\\tools\\fpcalc')
  process.exit(1)
}

const targetDir = join(desktopRoot, 'build', 'fpcalc')
mkdirSync(targetDir, { recursive: true })
const target = join(targetDir, executable)
copyFileSync(source, target)
console.log(`[prepare-fpcalc] ${source} -> ${target}`)
