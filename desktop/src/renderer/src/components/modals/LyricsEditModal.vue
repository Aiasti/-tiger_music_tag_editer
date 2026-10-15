<script setup lang="ts">
import { ref, computed } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, applyLyricsToTrack } = useWorkspace()

const currentTrack = computed(() => selectedTracks.value[0])
const lyricsContent = ref(currentTrack.value?.tags.lyrics || '')

const lineCount = computed(() => {
  if (!lyricsContent.value) return 0
  return lyricsContent.value.split('\n').length
})

const handleCleanEmptyLines = () => {
  lyricsContent.value = lyricsContent.value
    .split('\n')
    .filter((line: string) => line.trim() !== '')
    .join('\n')
}

const handleShiftTimeline = (offsetMs: number) => {
  // 简易时间轴平移正则：[mm:ss.xx]
  const regex = /\[(\d{2}):(\d{2})\.(\d{2,3})\]/g
  lyricsContent.value = lyricsContent.value.replace(
    regex,
    (_match: string, minStr: string, secStr: string, msStr: string) => {
      let totalMs =
        parseInt(minStr, 10) * 60 * 1000 +
        parseInt(secStr, 10) * 1000 +
        parseInt(msStr.padEnd(3, '0').slice(0, 3), 10)
      totalMs = Math.max(0, totalMs + offsetMs)
      const newMin = Math.floor(totalMs / 60000)
        .toString()
        .padStart(2, '0')
      const remainingMs = totalMs % 60000
      const newSec = Math.floor(remainingMs / 1000)
        .toString()
        .padStart(2, '0')
      const newCentisec = Math.floor((remainingMs % 1000) / 10)
        .toString()
        .padStart(2, '0')
      return `[${newMin}:${newSec}.${newCentisec}]`
    }
  )
}

const handleClear = () => {
  lyricsContent.value = ''
}

const handleSave = () => {
  if (currentTrack.value) {
    applyLyricsToTrack(currentTrack.value.id, lyricsContent.value)
  }
  emit('close')
}
</script>

<template>
  <ModalContainer
    :title="`📝 内嵌歌词编辑器 — ${currentTrack?.tags.title || '单曲'}`"
    max-width="780px"
    @close="emit('close')"
  >
    <div class="lyrics-edit-body">
      <!-- 快捷工具条 -->
      <div class="edit-toolbar">
        <div class="toolbar-left">
          <button class="btn sm" @click="handleCleanEmptyLines">🧹 清洗多余空行</button>
          <button class="btn sm" @click="handleShiftTimeline(-500)">⏪ 时间轴 -0.5s</button>
          <button class="btn sm" @click="handleShiftTimeline(500)">⏩ 时间轴 +0.5s</button>
          <button class="btn sm danger" @click="handleClear">🗑️ 清空歌词</button>
        </div>
        <div class="toolbar-right">
          <span class="stats-text">总计 {{ lineCount }} 行 | {{ lyricsContent.length }} 字符</span>
        </div>
      </div>

      <!-- 歌词多行编辑区 -->
      <div class="editor-wrapper">
        <textarea
          v-model="lyricsContent"
          class="lyrics-textarea"
          placeholder="在此直接粘贴或修改 LRC / 纯文本歌词..."
          spellcheck="false"
        ></textarea>
      </div>

      <div class="edit-hint">
        * 保存后仅更新当前曲目的内存编辑态，需点击主界面「💾 确认写回」后才持久化写回文件。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleSave">保存至内存编辑态</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.lyrics-edit-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.edit-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-hover);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
}

.toolbar-left {
  display: flex;
  gap: 6px;
}

.stats-text {
  font-size: var(--font-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.editor-wrapper {
  height: 380px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--bg-surface);
}

.lyrics-textarea {
  width: 100%;
  height: 100%;
  padding: 12px;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  color: var(--text-main);
  font-family: var(--font-mono);
  font-size: var(--font-sm);
  line-height: 1.6;
  white-space: pre;
}

.lyrics-textarea:focus {
  border-color: var(--accent);
}

.edit-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
