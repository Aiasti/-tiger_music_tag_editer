import { promises as fs } from 'node:fs'
import { dirname } from 'node:path'

/**
 * 生成固定路径的 M3U8 试听播放列表（S5）。
 *
 * <p>文件名固定为 `tiger trial.m3u8`，每次试听覆盖写入；交给 foobar2000 打开后
 * 以文件名作为播放列表标签页名称。foobar 原生命令行没有创建/清空命名列表的参数。
 */
export async function writeM3u8Playlist(
  playlistPath: string,
  filePaths: string[]
): Promise<void> {
  await fs.mkdir(dirname(playlistPath), { recursive: true })
  const content = ['#EXTM3U', ...filePaths].join('\r\n') + '\r\n'
  const temp = `${playlistPath}.tmp`
  await fs.writeFile(temp, content, 'utf8')
  await fs.rename(temp, playlistPath)
}
