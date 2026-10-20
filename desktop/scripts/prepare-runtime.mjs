import { spawnSync } from 'node:child_process'
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync
} from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 构建可随包分发的后端运行时（S6）。
 *
 * <p>1) 用 Maven 打出 Spring Boot 可执行 jar；
 * 2) 用 jlink 生成只包含所需模块的 Java 运行时；
 * 3) 复制到 electron-builder 的 extraResources 源目录 `desktop/build`。
 *
 * 仅在打包机执行；最终用户无需安装 Java。
 */
const desktopRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = join(desktopRoot, '..')
const backendRoot = join(repoRoot, 'backend')
const buildRoot = join(desktopRoot, 'build')
const backendTarget = join(buildRoot, 'backend')
const runtimeTarget = join(buildRoot, 'runtime')

const isWindows = process.platform === 'win32'
const javaExecutable = isWindows ? 'java.exe' : 'java'
const jlinkExecutable = isWindows ? 'jlink.exe' : 'jlink'

/** jlink 模块集合：Java SE + Spring Boot / jaudiotagger 运行时常见依赖。 */
const MODULES = [
  'java.se',
  'jdk.unsupported',
  'jdk.crypto.ec',
  'jdk.charsets',
  'jdk.localedata'
].join(',')

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    ...options
  })
  if (result.error) {
    throw new Error(`无法执行 ${command}：${result.error.message}`)
  }
  if (result.status !== 0) {
    throw new Error(`${command} 执行失败，退出码 ${result.status}`)
  }
}

function findMavenWrapper() {
  const wrapper = join(backendRoot, isWindows ? 'mvnw.cmd' : 'mvnw')
  if (!existsSync(wrapper)) {
    throw new Error(`未找到 Maven Wrapper：${wrapper}`)
  }
  return wrapper
}

function findJlink() {
  const home = process.env['JAVA_HOME']
  if (home) {
    const candidate = join(home, 'bin', jlinkExecutable)
    if (existsSync(candidate)) {
      return candidate
    }
  }
  const finder = isWindows ? 'where' : 'which'
  const result = spawnSync(finder, ['jlink'], { encoding: 'utf8' })
  const found = (result.stdout || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean)
  if (!found) {
    throw new Error('未找到 jlink；请安装 JDK 21 并设置 JAVA_HOME')
  }
  return found
}

function backendVersion() {
  const pom = readFileSync(join(backendRoot, 'pom.xml'), 'utf8')
  const match = pom.match(
    /<artifactId>tiger-music-tag-editor-backend<\/artifactId>\s*<version>([^<]+)<\/version>/
  )
  if (!match) {
    throw new Error('Cannot read backend version from pom.xml')
  }
  return match[1].trim()
}

function findBackendJar() {
  const targetDir = join(backendRoot, 'target')
  const version = backendVersion()
  const expectedName = `tiger-music-tag-editor-backend-${version}.jar`
  const expected = join(targetDir, expectedName)
  if (existsSync(expected)) {
    return expected
  }
  const candidates = readdirSync(targetDir)
    .filter((name) => name.endsWith('.jar') && !name.endsWith('.original'))
    .map((name) => join(targetDir, name))
    .filter((path) => statSync(path).isFile())
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)
  if (candidates.length === 0) {
    throw new Error(`No backend jar found under ${targetDir}`)
  }
  return candidates[0]
}

function directorySize(path) {
  let total = 0
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    const child = join(path, entry.name)
    total += entry.isDirectory() ? directorySize(child) : statSync(child).size
  }
  return total
}

function main() {
  console.log('[prepare-runtime] Building backend jar...')
  const maven = findMavenWrapper()
  if (isWindows) {
    run(process.env.ComSpec || 'cmd.exe', ['/d', '/c', maven, '-B', '-DskipTests', 'package'], {
      cwd: backendRoot
    })
  } else {
    run(maven, ['-B', '-DskipTests', 'package'], { cwd: backendRoot })
  }

  const jar = findBackendJar()
  mkdirSync(backendTarget, { recursive: true })
  const packagedJar = join(backendTarget, 'backend.jar')
  copyFileSync(jar, packagedJar)
  console.log(`[prepare-runtime] ${jar} -> ${packagedJar}`)

  console.log('[prepare-runtime] Generating jlink runtime...')
  rmSync(runtimeTarget, { recursive: true, force: true })
  const jlink = findJlink()
  run(jlink, [
    '--add-modules',
    MODULES,
    '--strip-debug',
    '--no-header-files',
    '--no-man-pages',
    '--compress=zip-6',
    '--output',
    runtimeTarget
  ])

  const java = join(runtimeTarget, 'bin', javaExecutable)
  if (!existsSync(java)) {
    throw new Error(`jlink did not produce a java executable: ${java}`)
  }
  run(java, ['-version'])

  const sizeMb = (directorySize(runtimeTarget) / (1024 * 1024)).toFixed(1)
  console.log(`[prepare-runtime] Done: runtime ${sizeMb} MB at ${runtimeTarget}`)
}

try {
  main()
} catch (error) {
  console.error(`[prepare-runtime] ${error instanceof Error ? error.message : String(error)}`)
  process.exit(1)
}