<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { useModals } from '../../composables/useModals'
import type { TrackItem, EditableTagField } from '../../types'

const {
  tracks,
  columns,
  selectedTrackId,
  editingCell,
  selectTrack,
  startInlineEdit,
  commitInlineEdit,
  cancelInlineEdit,
  navigateInlineEdit
} = useWorkspace()

const { openModal } = useModals()

const inlineInputRef = ref<HTMLInputElement | null>(null)
const inlineEditValue = ref('')

// 点击单元格
const handleCellClick = (trackId: number) => {
  selectTrack(trackId)
}

// 双击单元格激活行内编辑 (BIZ-011)
const handleCellDblClick = (track: TrackItem, field: string) => {
  if (track.status === 'failed') return
  if (field === 'id' || field === 'status' || field === 'duration' || field === 'lyricsBtn') return

  const tagField = field as EditableTagField
  inlineEditValue.value = track.tags[tagField] || ''
  startInlineEdit(track.id, tagField)

  nextTick(() => {
    if (inlineInputRef.value) {
      inlineInputRef.value.focus()
      inlineInputRef.value.select()
    }
  })
}

// 提交编辑
const handleInputBlur = () => {
  commitInlineEdit(inlineEditValue.value)
}

const handleInputEnter = () => {
  commitInlineEdit(inlineEditValue.value)
}

const handleInputEsc = () => {
  cancelInlineEdit()
}

// Tab / Shift+Tab 纵向跨行跳转 (BIZ-011)
const handleInputTab = (e: KeyboardEvent) => {
  e.preventDefault()
  commitInlineEdit(inlineEditValue.value)
  const direction = e.shiftKey ? 'up' : 'down'
  navigateInlineEdit(direction)

  nextTick(() => {
    if (editingCell.value) {
      const currentTrack = tracks.value.find((t) => t.id === editingCell.value!.trackId)
      if (currentTrack) {
        inlineEditValue.value = currentTrack.tags[editingCell.value.field] || ''
        if (inlineInputRef.value) {
          inlineInputRef.value.focus()
          inlineInputRef.value.select()
        }
      }
    }
  })
}

// 打开单曲歌词独立编辑弹窗 (BIZ-012)
const handleOpenLyricsEdit = (trackId: number, e: MouseEvent) => {
  e.stopPropagation()
  selectTrack(trackId)
  openModal('lyrics-edit')
}
</script>

<template>
  <div class="workspace-table-container">
    <div class="table-scroll-wrapper">
      <table class="tracks-table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ minWidth: `${col.minWidth}px` }"
              :class="{ 'align-right': col.key === 'id' || col.key === 'duration' }"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="track in tracks"
            :key="track.id"
            :class="{
              selected: track.id === selectedTrackId,
              failed: track.status === 'failed',
              edited: track.status === 'edited',
              matched: track.status === 'matched'
            }"
            @click="handleCellClick(track.id)"
          >
            <!-- 1. 行号 -->
            <td class="col-id align-right">
              <span class="row-num">{{ String(track.id).padStart(2, '0') }}</span>
            </td>

            <!-- 2. 状态徽标 -->
            <td class="col-status">
              <span class="status-badge" :class="track.status">
                <template v-if="track.status === 'saved'">已保存</template>
                <template v-else-if="track.status === 'edited'">已编辑</template>
                <template v-else-if="track.status === 'matched'">已匹配</template>
                <template v-else-if="track.status === 'failed'">失败</template>
              </span>
            </td>

            <!-- 3. 音轨号 -->
            <td
              class="col-track editable-cell"
              @dblclick="handleCellDblClick(track, 'trackNo')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'trackNo'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.trackNo }">
                {{ track.tags.trackNo || '—' }}
              </span>
            </td>

            <!-- 4. 碟号 -->
            <td
              class="col-disc editable-cell"
              @dblclick="handleCellDblClick(track, 'discNo')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'discNo'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.discNo }">
                {{ track.tags.discNo || '—' }}
              </span>
            </td>

            <!-- 5. 标题 -->
            <td
              class="col-title editable-cell"
              @dblclick="handleCellDblClick(track, 'title')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'title'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.title, 'bold-text': !!track.tags.title }">
                {{ track.tags.title || '—' }}
              </span>
            </td>

            <!-- 6. 艺术家 -->
            <td
              class="col-artist editable-cell"
              @dblclick="handleCellDblClick(track, 'artist')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'artist'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.artist }">
                {{ track.tags.artist || '—' }}
              </span>
            </td>

            <!-- 7. 专辑 -->
            <td
              class="col-album editable-cell"
              @dblclick="handleCellDblClick(track, 'album')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'album'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.album }">
                {{ track.tags.album || '—' }}
              </span>
            </td>

            <!-- 8. 专辑艺术家 -->
            <td
              class="col-album-artist editable-cell"
              @dblclick="handleCellDblClick(track, 'albumArtist')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'albumArtist'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.albumArtist }">
                {{ track.tags.albumArtist || '—' }}
              </span>
            </td>

            <!-- 9. 年份 -->
            <td
              class="col-year editable-cell"
              @dblclick="handleCellDblClick(track, 'year')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'year'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.year }">
                {{ track.tags.year || '—' }}
              </span>
            </td>

            <!-- 10. 流派 -->
            <td
              class="col-genre editable-cell"
              @dblclick="handleCellDblClick(track, 'genre')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'genre'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.genre }">
                {{ track.tags.genre || '—' }}
              </span>
            </td>

            <!-- 11. 作曲 -->
            <td
              class="col-composer editable-cell"
              @dblclick="handleCellDblClick(track, 'composer')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'composer'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.composer }">
                {{ track.tags.composer || '—' }}
              </span>
            </td>

            <!-- 12. 作词 -->
            <td
              class="col-lyricist editable-cell"
              @dblclick="handleCellDblClick(track, 'lyricist')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'lyricist'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.lyricist }">
                {{ track.tags.lyricist || '—' }}
              </span>
            </td>

            <!-- 13. 编曲 -->
            <td
              class="col-arranger editable-cell"
              @dblclick="handleCellDblClick(track, 'arranger')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'arranger'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.arranger }">
                {{ track.tags.arranger || '—' }}
              </span>
            </td>

            <!-- 14. 内嵌歌词独立按钮 (BIZ-012 状态感知) -->
            <td class="col-lyrics-btn" @click.stop>
              <button
                v-if="track.status !== 'failed'"
                class="btn-lyric-cell"
                :class="{ 'has-content': !!(track.tags.lyrics && track.tags.lyrics.trim().length > 0) }"
                :title="track.tags.lyrics ? '点击查看与编辑内嵌歌词' : '本地无歌词，点击手动添加'"
                @click="handleOpenLyricsEdit(track.id, $event)"
              >
                {{ track.tags.lyrics && track.tags.lyrics.trim().length > 0 ? '📝 编辑歌词' : '+ 添加歌词' }}
              </button>
              <span v-else class="faint">—</span>
            </td>

            <!-- 15. 注释 -->
            <td
              class="col-comment editable-cell"
              @dblclick="handleCellDblClick(track, 'comment')"
            >
              <input
                v-if="editingCell?.trackId === track.id && editingCell?.field === 'comment'"
                ref="inlineInputRef"
                v-model="inlineEditValue"
                class="inline-input"
                @blur="handleInputBlur"
                @keydown.enter="handleInputEnter"
                @keydown.esc="handleInputEsc"
                @keydown.tab="handleInputTab"
              />
              <span v-else :class="{ faint: !track.tags.comment }">
                {{ track.tags.comment || '—' }}
              </span>
            </td>

            <!-- 16. 时长 -->
            <td class="col-duration align-right">
              <span class="mono-num">{{ track.duration }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.workspace-table-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--surface);
}

.table-scroll-wrapper {
  flex: 1;
  overflow: auto;
}

.tracks-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  table-layout: auto;
  white-space: nowrap;
}

thead {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--surface-3);
  box-shadow: 0 1px 0 var(--border);
}

th {
  height: 30px;
  padding: 0 8px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-muted);
  text-align: left;
  border-right: 1px solid var(--border);
  user-select: none;
}

tbody tr {
  height: 30px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background-color 0.1s ease;
}

tbody tr:hover {
  background-color: var(--surface-hover);
}

tbody tr.selected {
  background-color: var(--accent-soft) !important;
}

tbody tr.edited {
  border-left: 3px solid var(--state-edited);
}

tbody tr.matched {
  border-left: 3px solid var(--state-matched);
}

tbody tr.failed {
  background-color: var(--state-failed-soft);
  color: var(--state-failed);
}

td {
  padding: 0 8px;
  color: var(--text);
  border-right: 1px solid var(--border);
  vertical-align: middle;
}

.align-right {
  text-align: right;
}

.row-num, .mono-num {
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.bold-text {
  font-weight: 500;
  color: var(--text-bright);
}

.faint {
  color: var(--text-faint);
}

/* 行内就地编辑 (Inline Input) */
.editable-cell {
  position: relative;
}

.inline-input {
  width: 100%;
  height: 24px;
  margin: -2px 0;
  padding: 0 6px;
  background: var(--surface);
  color: var(--text-bright);
  border: 1px solid var(--accent);
  border-radius: 3px;
  font-family: inherit;
  font-size: 12px;
  outline: none;
  box-shadow: 0 0 0 2px var(--accent-soft);
}

/* 内嵌歌词状态感知按钮 (BIZ-012) */
.btn-lyric-cell {
  height: 20px;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
  cursor: pointer;
  border: 1px dashed var(--border-strong);
  background: var(--surface-2);
  color: var(--text-muted);
  transition: all 0.15s ease;
}

.btn-lyric-cell:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.btn-lyric-cell.has-content {
  border: 1px solid var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 500;
}
</style>
