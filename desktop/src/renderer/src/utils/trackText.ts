import type { TrackItem } from '../types'

/**
 * 导入文件名（去除扩展名）。
 * 后端 `filename` 含扩展名（如 `1-01 Fragile.m4a`），`ext` 为不带点的扩展名（如 `m4a`），无扩展名时为空串。
 */
export function trackBaseName(track: TrackItem): string {
  const ext = track.ext?.replace(/^\./, '') ?? ''
  if (!ext) {
    return track.filename
  }
  const suffix = `.${ext}`
  return track.filename.toLowerCase().endsWith(suffix.toLowerCase())
    ? track.filename.slice(0, track.filename.length - suffix.length)
    : track.filename
}
