<script setup lang="ts">
import { computed } from 'vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { useModals } from '../../composables/useModals'

const { selectedTrack } = useWorkspace()
const { openModal } = useModals()

const hasLyrics = computed(() => {
  return !!(selectedTrack.value && selectedTrack.value.tags.lyrics && selectedTrack.value.tags.lyrics.trim().length > 0)
})

const lyricsLineCount = computed(() => {
  if (!selectedTrack.value || !selectedTrack.value.tags.lyrics) return 0
  return selectedTrack.value.tags.lyrics.trim().split('\n').length
})

const handleOpenLyricsEdit = () => {
  openModal('lyrics-edit')
}
</script>

<template>
  <aside class="detail-pane">
    <div class="panel-header">
      <span class="panel-title">💿 单曲媒体与标签详情</span>
      <span v-if="selectedTrack" class="track-status-tag" :class="selectedTrack.status">
        {{ selectedTrack.status === 'saved' ? '已保存 (无待提交)' : selectedTrack.status === 'edited' ? '已编辑 (待写回)' : selectedTrack.status === 'matched' ? '已在线匹配' : '读取损坏/失败' }}
      </span>
    </div>

    <div v-if="selectedTrack" class="panel-body">
      <!-- 封面区域 -->
      <div class="cover-section">
        <div class="cover-box">
          <div class="cover-placeholder">
            <span class="cover-icon">🎵</span>
            <span class="cover-desc">600 × 600 · PNG</span>
          </div>
        </div>
        <div class="cover-actions">
          <button class="btn sm" title="选择本地图片替换当前内嵌封面">替换封面</button>
          <button class="btn sm danger" title="清除音频内嵌封面">移除</button>
        </div>
      </div>

      <!-- 文件技术参数 -->
      <div class="meta-section">
        <div class="section-title">文件参数</div>
        <div class="meta-row">
          <span class="meta-label">文件路径:</span>
          <span class="meta-val path" :title="selectedTrack.filePath">{{ selectedTrack.filePath }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">格式规格:</span>
          <span class="meta-val">{{ selectedTrack.ext.toUpperCase() }} · 24bit / 96kHz · {{ selectedTrack.size }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">音频时长:</span>
          <span class="meta-val">{{ selectedTrack.duration }}</span>
        </div>
      </div>

      <!-- 内嵌歌词状态与独立编辑入口 (BIZ-012) -->
      <div class="meta-section">
        <div class="section-title flex-between">
          <span>内嵌歌词 (LRC)</span>
          <span class="badge-lyrics-info">
            {{ hasLyrics ? `${lyricsLineCount} 行 · 已同步` : '暂无歌词' }}
          </span>
        </div>

        <div class="lyrics-preview-box">
          <div v-if="hasLyrics" class="lyrics-text">
            {{ selectedTrack.tags.lyrics }}
          </div>
          <div v-else class="lyrics-empty">
            <span>（本地音轨无内嵌歌词）</span>
          </div>
        </div>

        <div class="lyrics-btn-bar">
          <button
            class="btn sm btn-lyrics-action"
            :class="{ 'has-content': hasLyrics }"
            @click="handleOpenLyricsEdit"
          >
            {{ hasLyrics ? '📝 编辑歌词 (BIZ-012)' : '+ 添加歌词 (BIZ-012)' }}
          </button>
          <button class="btn sm" @click="openModal('lyrics-search')">
            🎵 在线搜索歌词
          </button>
        </div>
      </div>

      <!-- 字段比对列表 -->
      <div class="meta-section">
        <div class="section-title">当前标签 vs 首次快照</div>
        <div class="tag-diff-list">
          <div class="diff-item">
            <span class="meta-label">标题:</span>
            <span class="meta-val" :class="{ 'is-edited': selectedTrack.tags.title !== selectedTrack.initialSnapshot.title }">
              {{ selectedTrack.tags.title || '—' }}
            </span>
          </div>
          <div class="diff-item">
            <span class="meta-label">艺术家:</span>
            <span class="meta-val" :class="{ 'is-edited': selectedTrack.tags.artist !== selectedTrack.initialSnapshot.artist }">
              {{ selectedTrack.tags.artist || '—' }}
            </span>
          </div>
          <div class="diff-item">
            <span class="meta-label">专辑:</span>
            <span class="meta-val" :class="{ 'is-edited': selectedTrack.tags.album !== selectedTrack.initialSnapshot.album }">
              {{ selectedTrack.tags.album || '—' }}
            </span>
          </div>
          <div class="diff-item">
            <span class="meta-label">音轨/碟号:</span>
            <span class="meta-val">
              {{ selectedTrack.tags.trackNo || '—' }} / Disc {{ selectedTrack.tags.discNo || '—' }}
            </span>
          </div>
          <div class="diff-item">
            <span class="meta-label">年份/流派:</span>
            <span class="meta-val">
              {{ selectedTrack.tags.year || '—' }} · {{ selectedTrack.tags.genre || '—' }}
            </span>
          </div>
        </div>
      </div>
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
  width: 290px;
  min-width: 280px;
  max-width: 340px;
  background: var(--surface-2);
  border-left: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  background: var(--surface-3);
  border-bottom: 1px solid var(--border);
}
.panel-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-bright);
}

.track-status-tag {
  font-size: 10.5px;
  padding: 2px 6px;
  border-radius: 3px;
}
.track-status-tag.saved {
  background: var(--state-ok-soft);
  color: var(--state-ok);
  border: 1px solid var(--state-ok);
}
.track-status-tag.edited {
  background: var(--state-edited-soft);
  color: var(--state-edited);
  border: 1px solid var(--state-edited);
}
.track-status-tag.matched {
  background: var(--state-matched-soft);
  color: var(--state-matched);
  border: 1px solid var(--state-matched);
}
.track-status-tag.failed {
  background: var(--state-failed-soft);
  color: var(--state-failed);
  border: 1px solid var(--state-failed);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cover-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.cover-box {
  width: 130px;
  height: 130px;
  background: var(--surface-3);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.cover-icon {
  font-size: 32px;
}
.cover-desc {
  font-size: 10px;
  color: var(--text-muted);
}
.cover-actions {
  display: flex;
  gap: 6px;
}

.meta-section {
  display: flex;
  flex-direction: column;
  gap: 5px;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  padding: 8px;
}
.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 2px;
}
.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.badge-lyrics-info {
  font-size: 10px;
  color: var(--accent);
}

.meta-row {
  display: flex;
  font-size: 11.5px;
  line-height: 1.4;
  gap: 6px;
}
.meta-label {
  color: var(--text-muted);
  width: 65px;
  flex-shrink: 0;
}
.meta-val {
  color: var(--text-bright);
  word-break: break-all;
}
.meta-val.path {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text);
}
.meta-val.is-edited {
  color: var(--state-edited);
  font-weight: 600;
}

.lyrics-preview-box {
  height: 70px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 3px;
  padding: 6px;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.4;
}
.lyrics-text {
  white-space: pre-wrap;
  color: var(--text);
}
.lyrics-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: 11px;
}
.lyrics-btn-bar {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
.btn-lyrics-action.has-content {
  border-color: var(--accent);
  color: var(--accent);
}

.tag-diff-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.diff-item {
  display: flex;
  font-size: 11px;
  gap: 6px;
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
