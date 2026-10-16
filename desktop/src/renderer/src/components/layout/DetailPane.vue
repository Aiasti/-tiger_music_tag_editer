<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { fetchCoverUrl } from '../../api/client'
import { trackBaseName } from '../../utils/trackText'
import type { TrackTagData } from '../../types'

const { selectedTrack, pendingCovers } = useWorkspace()

const statusText = computed(() => {
  switch (selectedTrack.value?.status) {
    case 'saved':
      return '已保存（无待提交）'
    case 'edited':
      return '已编辑（待提交）'
    case 'matched':
      return '已在线匹配'
    case 'failed':
      return '读取损坏/失败'
    default:
      return '—'
  }
})

const hasLyrics = computed(() => {
  const lyrics = selectedTrack.value?.tags.lyrics
  return !!lyrics && lyrics.trim().length > 0
})

const lyricsLineCount = computed(() => {
  const lyrics = selectedTrack.value?.tags.lyrics
  return lyrics ? lyrics.trim().split('\n').length : 0
})

/** 差异比对涵盖的核心文本字段；内嵌歌词与封面作为媒体字段单独提示。 */
const DIFF_PAIRS: { key: keyof TrackTagData; label: string }[] = [
  { key: 'title', label: '标题' },
  { key: 'artist', label: '艺术家' },
  { key: 'album', label: '专辑' },
  { key: 'albumArtist', label: '专辑艺术家' },
  { key: 'trackNo', label: '音轨号' },
  { key: 'discNo', label: '碟号' },
  { key: 'year', label: '年份' },
  { key: 'genre', label: '流派' },
  { key: 'composer', label: '作曲' },
  { key: 'lyricist', label: '作词' },
  { key: 'arranger', label: '编曲' },
  { key: 'comment', label: '注释' }
]

/** 待提交变更：当前值 vs 最近成功落盘值（FR-8），只列出确实发生变化的字段。 */
const pendingFields = computed<{ label: string; current: string; written: string }[]>(() => {
  const track = selectedTrack.value
  if (!track) {
    return []
  }
  return DIFF_PAIRS
    .filter(({ key }) => (track.tags[key] ?? '') !== (track.lastWritten[key] ?? ''))
    .map(({ key, label }) => ({
      label,
      current: track.tags[key] || '已清除',
      written: track.lastWritten[key] || '（空）'
    }))
})

/** 待提交的媒体字段（歌词与封面）：只提示类别，不展开内容。 */
const pendingMedia = computed<string[]>(() => {
  const track = selectedTrack.value
  if (!track) {
    return []
  }
  const items: string[] = []
  if ((track.tags.lyrics ?? '') !== (track.lastWritten.lyrics ?? '')) {
    items.push('内嵌歌词')
  }
  if (pendingCovers.value[track.id] !== undefined) {
    items.push('封面')
  }
  return items
})

/** 最近落盘值与首次读取快照是否不同：回滚会把编辑值改回首次值并重新产生待提交差异（DEV-002）。 */
const snapshotDrift = computed(() => {
  const track = selectedTrack.value
  if (!track) {
    return false
  }
  return (Object.keys(track.lastWritten) as (keyof TrackTagData)[]).some(
    (key) => (track.lastWritten[key] ?? '') !== (track.initialSnapshot[key] ?? '')
  )
})

// 内嵌封面（BIZ-015）：随选中曲目按需从后端获取，切换时释放上一个对象 URL
const coverUrl = ref<string | null>(null)
const coverLoading = ref(false)

const releaseCover = () => {
  if (coverUrl.value) {
    URL.revokeObjectURL(coverUrl.value)
    coverUrl.value = null
  }
}

watch(
  () => [selectedTrack.value?.id, selectedTrack.value?.hasCover] as const,
  async ([id, hasCover]) => {
    releaseCover()
    if (id == null || !hasCover) return
    coverLoading.value = true
    try {
      coverUrl.value = await fetchCoverUrl(id)
    } catch {
      coverUrl.value = null
    } finally {
      coverLoading.value = false
    }
  },
  { immediate: true }
)

onUnmounted(releaseCover)

/** 待写回封面（BIZ-020）：存在键即表示该曲目有封面编辑态；空串表示已标记移除封面。 */
const displayCover = computed(() => {
  const id = selectedTrack.value?.id
  const pending = id == null ? undefined : pendingCovers.value[id]
  if (pending === undefined) {
    return coverUrl.value
  }
  return pending === '' ? null : pending
})
</script>

<template>
  <aside class="detail-pane">
    <div v-if="selectedTrack" class="detail-scroll">
      <!-- 标题 -->
      <section>
        <h3 class="section-title">当前曲目详情</h3>
        <div class="track-title">{{ selectedTrack.tags.title || trackBaseName(selectedTrack) }}</div>
      </section>

      <!-- 封面媒体卡片 (FR-6)：只读展示；编辑入口在工作区次工具栏「编辑封面」（BIZ-020） -->
      <section class="cover-card">
        <img v-if="displayCover" class="cover-preview" :src="displayCover" alt="内嵌专辑封面" />
        <div v-else class="cover-preview cover-empty">
          <span>{{ coverLoading ? '…' : '🎵' }}</span>
        </div>
        <div class="cover-meta">
          <span class="filename">{{ selectedTrack.filename }}</span>
          <span class="filesize">
            {{ selectedTrack.ext.toUpperCase() }} · {{ selectedTrack.size || '—' }} ·
            {{ selectedTrack.duration || '时长未知' }}
          </span>
          <span class="cover-state">{{ displayCover ? '已有封面' : '无封面' }}</span>
        </div>
      </section>

      <!-- 核心元数据 -->
      <section>
        <h3 class="section-title">核心元数据</h3>
        <dl class="meta-kv">
          <dt>艺术家</dt>
          <dd>{{ selectedTrack.tags.artist || '—' }}</dd>
          <dt>专辑</dt>
          <dd>{{ selectedTrack.tags.album || '—' }}</dd>
          <dt>音轨 / 碟号</dt>
          <dd>{{ selectedTrack.tags.trackNo || '—' }} / 碟 {{ selectedTrack.tags.discNo || '—' }}</dd>
          <dt>年份 / 流派</dt>
          <dd>{{ selectedTrack.tags.year || '—' }} · {{ selectedTrack.tags.genre || '—' }}</dd>
          <dt>内嵌歌词</dt>
          <dd>
            <span class="lyrics-state">{{ hasLyrics ? `已有歌词（${lyricsLineCount} 行）` : '暂无' }}</span>
          </dd>
          <dt>状态</dt>
          <dd>
            <span class="state-text" :class="selectedTrack.status">{{ statusText }}</span>
          </dd>
        </dl>
      </section>

      <!-- 待提交变更 vs 最近落盘值 (FR-8) -->
      <section>
        <h3 class="section-title">待提交变更 vs 最近落盘值</h3>
        <div v-if="pendingFields.length > 0" class="diff-box">
          <div v-for="field in pendingFields" :key="field.label" class="diff-row">
            <span class="diff-label">{{ field.label }}</span>
            <span class="diff-values">
              <span class="diff-current">{{ field.current }}</span>
              <span class="diff-initial">（落盘：{{ field.written }}）</span>
            </span>
          </div>
        </div>
        <p v-else class="diff-empty">无待提交变更</p>
        <p v-if="pendingMedia.length > 0" class="diff-notice">
          另有待提交的媒体字段：{{ pendingMedia.join('、') }}
        </p>
        <p v-if="snapshotDrift" class="diff-notice">
          最近落盘值已不同于首次读取快照：提交前回滚会把编辑值改回首次值，并重新产生待提交差异。
        </p>
      </section>

      <!-- 内嵌歌词 (FR-6)：只读预览；编辑入口收敛到工具栏「🎵 歌词搜索」弹窗（BIZ-018、BIZ-020） -->
      <section>
        <div class="section-head">
          <h3 class="section-title">内嵌歌词</h3>
        </div>
        <div class="lyrics-box">
          <template v-if="hasLyrics">{{ selectedTrack.tags.lyrics }}</template>
          <span v-else class="lyrics-empty">（本地音轨无内嵌歌词）</span>
        </div>
      </section>
    </div>

    <div v-else class="panel-empty">
      <div class="empty-icon">📁</div>
      <p>未选择任何曲目</p>
      <span class="empty-sub">在左侧表格中点击行即可查看媒体详情与歌词</span>
    </div>
  </aside>
</template>

<style scoped>
.detail-pane {
  flex: 1;
  width: 100%;
  min-width: 0;
  background: var(--surface-2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 保留滚轮滚动，但隐藏滚动条以免影响观感（BIZ-017 反馈） */
.detail-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.detail-scroll::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.section-title {
  margin: 0;
  font-size: 11.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.track-title {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-bright);
  word-break: break-all;
  line-height: 1.35;
}

/* 封面卡片：横向排版（与原型一致） */
.cover-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.cover-preview {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid var(--border-strong);
  background: var(--surface-3);
  flex-shrink: 0;
  object-fit: cover;
  display: block;
}
.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--text-muted);
}
.cover-meta {
  flex: 1;
  min-width: 0;
  font-size: 11.5px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cover-meta .filename {
  font-weight: 600;
  color: var(--text-bright);
  word-break: break-all;
  line-height: 1.35;
}
.cover-meta .filesize {
  color: var(--text-muted);
}
.cover-state {
  color: var(--text-faint);
}

/* 核心元数据键值表 */
.meta-kv {
  display: grid;
  grid-template-columns: 78px 1fr;
  gap: 6px 8px;
  margin: 8px 0 0;
  font-size: 12px;
}
.meta-kv dt {
  color: var(--text-muted);
  font-weight: 500;
}
.meta-kv dd {
  margin: 0;
  color: var(--text);
  word-break: break-word;
}
.lyrics-state {
  color: var(--accent);
  font-size: 11.5px;
}
.state-text {
  font-weight: 600;
}
.state-text.saved {
  color: var(--state-ok);
}
.state-text.edited {
  color: var(--state-edited);
}
.state-text.matched {
  color: var(--state-matched);
}
.state-text.failed {
  color: var(--state-failed);
}

/* 差异比对 */
.diff-box {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 8px;
}
.diff-row {
  display: flex;
  gap: 8px;
  font-size: 11.5px;
}
.diff-label {
  width: 62px;
  flex-shrink: 0;
  color: var(--text-muted);
}
.diff-values {
  min-width: 0;
  word-break: break-word;
}
.diff-current {
  color: var(--state-edited);
  font-weight: 600;
}
.diff-initial {
  color: var(--text-faint);
  margin-left: 6px;
}
.diff-empty,
.diff-notice {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--text-muted);
}

/* 内嵌歌词 */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.lyrics-box {
  margin-top: 8px;
  max-height: 84px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.45;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  scrollbar-width: none;
}
.lyrics-box::-webkit-scrollbar {
  width: 0;
  height: 0;
}
.lyrics-empty {
  color: var(--text-muted);
  font-family: var(--font);
}

.panel-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-muted);
  text-align: center;
}
.empty-icon {
  font-size: 36px;
  margin-bottom: 8px;
}
.empty-sub {
  font-size: 11px;
  margin-top: 4px;
}
</style>
