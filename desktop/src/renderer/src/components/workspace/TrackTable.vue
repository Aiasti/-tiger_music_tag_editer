<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { useModals } from '../../composables/useModals'
import { trackBaseName } from '../../utils/trackText'
import type { TrackItem, EditableTagField, ColumnDefinition } from '../../types'

const {
  tracks,
  columns,
  selectedTrackIds,
  editingCell,
  hasUncommittedInSelection,
  pendingPaths,
  selectTrack,
  selectAllTracks,
  removeSelectedTracks,
  reorderTrack,
  startInlineEdit,
  commitInlineEdit,
  cancelInlineEdit,
  navigateInlineEdit
} = useWorkspace()

const { activeModal } = useModals()

/**
 * 行内输入框引用：模板中它位于 v-for 内，Vue 会把同名 ref 收集为数组，
 * 因此聚焦时统一按「数组取首个」处理，避免 focus() 失效导致双击后无法输入。
 */
const inlineInputRef = ref<HTMLInputElement | HTMLInputElement[] | null>(null)
const focusInlineInput = () => {
  const raw = inlineInputRef.value
  const el = Array.isArray(raw) ? raw[0] : raw
  el?.focus()
  el?.select()
}
const inlineEditValue = ref('')

const visibleColumns = computed(() => columns.value.filter((col) => col.visible))
const tableWidth = computed(() => visibleColumns.value.reduce((sum, col) => sum + col.width, 0))

// 状态圆点文案（参考原型最左侧列的圆点，BIZ-022）
const STATUS_TEXT: Record<TrackItem['status'], string> = {
  saved: '已保存（无未提交修改）',
  edited: '已编辑（待提交）',
  matched: '已匹配（待提交）',
  failed: '读取失败'
}

/**
 * 单格染色（BIZ-022，参考原型 `td.cell-edited` / `td.cell-matched`）：
 * 当前值与**最近落盘值**不同的可编辑单元格加浅色底与左侧色条，颜色随曲目状态（FR-8 差异基准）。
 */
const cellStateClass = (track: TrackItem, field: EditableTagField): string => {
  if (track.status === 'failed') return ''
  const current = track.tags[field] || ''
  const written = track.lastWritten[field] || ''
  if (current === written) return ''
  return track.status === 'matched' ? 'cell-matched' : 'cell-edited'
}

/** 待写回目标文件名（去扩展名）；无待写回路径时返回空串（BIZ-030）。 */
const pendingBaseName = (track: TrackItem): string => {
  const target = pendingPaths.value[track.id]
  if (!target) return ''
  const name = target.split(/[\\/]/).pop() ?? target
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.slice(0, dot) : name
}

/** 文件名列悬停提示：有待写回路径时显示目标完整路径。 */
const filenameTitle = (track: TrackItem): string => pendingPaths.value[track.id] ?? track.filename

// 拖拽结束后短暂屏蔽行点击：排序本身不改状态，也不应因「落下即选中」产生行染色（BIZ-022）
const suppressClickUntil = ref(0)

// 点击单元格：普通单选；Ctrl 点选切换；Shift 连选（BIZ-015）
const handleCellClick = (trackId: number, event: MouseEvent) => {
  if (Date.now() < suppressClickUntil.value) return
  selectTrack(trackId, event)
}

// 双击单元格激活行内编辑 (BIZ-011)
const handleCellDblClick = (track: TrackItem, field: string) => {
  if (track.status === 'failed') return
  if (field === 'id' || field === 'status' || field === 'duration') return

  const tagField = field as EditableTagField
  inlineEditValue.value = track.tags[tagField] || ''
  startInlineEdit(track.id, tagField)

  nextTick(() => {
    focusInlineInput()
  })
}

// 提交编辑（Enter / 失焦）
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
  navigateInlineEdit(e.shiftKey ? 'up' : 'down')

  nextTick(() => {
    if (editingCell.value) {
      const currentTrack = tracks.value.find((t) => t.id === editingCell.value!.trackId)
      if (currentTrack) {
        inlineEditValue.value = currentTrack.tags[editingCell.value.field] || ''
        focusInlineInput()
      }
    }
  })
}

// 列宽拖拽调整（BIZ-015）
const resizing = ref<{ key: string; startX: number; startWidth: number } | null>(null)

const startColumnResize = (event: MouseEvent, column: ColumnDefinition) => {
  resizing.value = { key: String(column.key), startX: event.clientX, startWidth: column.width }
  window.addEventListener('mousemove', onColumnResizeMove)
  window.addEventListener('mouseup', stopColumnResize)
}

const onColumnResizeMove = (event: MouseEvent) => {
  if (!resizing.value) return
  const { key, startX, startWidth } = resizing.value
  const target = columns.value.find((col) => String(col.key) === key)
  if (!target) return
  const next = startWidth + (event.clientX - startX)
  target.width = Math.max(target.minWidth, Math.round(next))
}

const stopColumnResize = () => {
  resizing.value = null
  window.removeEventListener('mousemove', onColumnResizeMove)
  window.removeEventListener('mouseup', stopColumnResize)
}

// 拖拽排序（BIZ-016 / FR-3）：由行号列作为拖拽手柄，避免与单元格点击/编辑冲突
const draggingTrackId = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

const handleDragStart = (trackId: number, event: DragEvent) => {
  draggingTrackId.value = trackId
  // 拖拽手柄仍是行号单元格，但拖动反馈用整行（BIZ-022）
  const row = (event.currentTarget as HTMLElement | null)?.closest('tr')
  if (row && event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(trackId))
    event.dataTransfer.setDragImage(row, 24, row.offsetHeight / 2)
  }
}

const handleDragOver = (index: number) => {
  if (draggingTrackId.value === null) return
  dragOverIndex.value = index
}

const handleDrop = (index: number) => {
  const fromId = draggingTrackId.value
  handleDragEnd()
  if (fromId === null) return
  reorderTrack(fromId, index)
}

const handleDragEnd = () => {
  draggingTrackId.value = null
  dragOverIndex.value = null
  suppressClickUntil.value = Date.now() + 200
}

// 移出工作区（BIZ-015）：仅会话内移除，不改磁盘；有未提交修改时二次确认
const requestRemoval = async () => {
  if (selectedTrackIds.value.length === 0) return
  const count = selectedTrackIds.value.length
  if (hasUncommittedInSelection.value) {
    const confirmed = confirm(
      `选中的 ${count} 首曲目包含未写回的修改，移出工作区将立即废弃这些修改。是否继续？`
    )
    if (!confirmed) return
  }
  await removeSelectedTracks()
}

// Delete 键触发移出工作区（输入框内、编辑中或模态打开时不响应）
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Delete') return
  if (activeModal.value || editingCell.value) return

  const target = event.target as HTMLElement | null
  const tag = target?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select' || target?.isContentEditable) return

  void requestRemoval()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  stopColumnResize()
})
</script>

<template>
  <div class="workspace-table-container">
    <div class="table-statusbar">
      <span class="statusbar-text">
        共 <strong>{{ tracks.length }}</strong> 首 · 已选 <strong>{{ selectedTrackIds.length }}</strong> 首
      </span>
      <button class="link-btn" title="选中工作区内全部可编辑曲目" @click="selectAllTracks">全选</button>
      <button
        class="link-btn danger"
        :disabled="selectedTrackIds.length === 0"
        title="将所选曲目移出工作区（不改磁盘文件）"
        @click="requestRemoval"
      >
        移出工作区 (Delete)
      </button>
      <span class="statusbar-hint">Ctrl 点选 / Shift 连选 · 拖拽行号调整顺序</span>
    </div>
    <div class="table-scroll-wrapper">
      <table class="tracks-table" :style="{ width: `${tableWidth}px` }">
        <colgroup>
          <col
            v-for="col in visibleColumns"
            :key="`col-${col.key}`"
            :style="{ width: `${col.width}px` }"
          />
        </colgroup>
        <thead>
          <tr>
            <th
              v-for="col in visibleColumns"
              :key="col.key"
              :class="{ 'align-right': col.key === 'id' || col.key === 'duration' }"
            >
              {{ col.label }}
              <span
                class="col-resizer"
                title="拖拽调整列宽"
                @mousedown.stop.prevent="startColumnResize($event, col)"
              ></span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(track, index) in tracks"
            :key="track.id"
            :draggable="editingCell?.trackId !== track.id"
            :class="{
              selected: selectedTrackIds.includes(track.id),
              failed: track.status === 'failed',
              'drag-over': dragOverIndex === index,
              'is-dragging': draggingTrackId === track.id
            }"
            @click="handleCellClick(track.id, $event)"
            @dragstart="handleDragStart(track.id, $event)"
            @dragend="handleDragEnd"
            @dragover.prevent="handleDragOver(index)"
            @drop.prevent.stop="handleDrop(index)"
          >
            <!-- 1. 状态圆点（最左列，BIZ-022，参考原型最左侧列的圆点） -->
            <td class="col-status">
              <span
                class="status-dot"
                :class="track.status"
                :title="STATUS_TEXT[track.status]"
              ></span>
            </td>

            <!-- 2. 序号（按当前顺序的位置序号 1..N，拖拽后重排；BIZ-023） -->
            <td class="col-id align-right" title="拖拽整行调整顺序">
              <span class="row-num">{{ String(index + 1).padStart(2, '0') }}</span>
            </td>

            <!-- 3. 文件名（导入文件名，去除扩展名；BIZ-024；有待写回路径时显示目标名，BIZ-030） -->
            <td class="col-filename" :title="filenameTitle(track)">
              <template v-if="pendingBaseName(track)">
                <span class="pending-name">{{ pendingBaseName(track) }}</span>
              </template>
              <template v-else>{{ trackBaseName(track) }}</template>
            </td>

            <!-- 4. 碟号 -->
            <td
              :class="['col-disc', 'editable-cell', cellStateClass(track, 'discNo')]"
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

            <!-- 5. 音轨号 -->
            <td
              :class="['col-track', 'editable-cell', cellStateClass(track, 'trackNo')]"
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

            <!-- 6. 标题 -->
            <td
              :class="['col-title', 'editable-cell', cellStateClass(track, 'title')]"
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

            <!-- 7. 艺术家 -->
            <td
              :class="['col-artist', 'editable-cell', cellStateClass(track, 'artist')]"
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

            <!-- 8. 专辑 -->
            <td
              :class="['col-album', 'editable-cell', cellStateClass(track, 'album')]"
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

            <!-- 9. 专辑艺术家 -->
            <td
              :class="['col-album-artist', 'editable-cell', cellStateClass(track, 'albumArtist')]"
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

            <!-- 10. 年份 -->
            <td
              :class="['col-year', 'editable-cell', cellStateClass(track, 'year')]"
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

            <!-- 11. 流派 -->
            <td
              :class="['col-genre', 'editable-cell', cellStateClass(track, 'genre')]"
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

            <!-- 12. 作曲 -->
            <td
              :class="['col-composer', 'editable-cell', cellStateClass(track, 'composer')]"
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

            <!-- 13. 作词 -->
            <td
              :class="['col-lyricist', 'editable-cell', cellStateClass(track, 'lyricist')]"
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

            <!-- 14. 编曲 -->
            <td
              :class="['col-arranger', 'editable-cell', cellStateClass(track, 'arranger')]"
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

            <!-- 15. 注释 -->
            <td
              :class="['col-comment', 'editable-cell', cellStateClass(track, 'comment')]"
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

/* 选区与批量操作条（BIZ-015） */
.table-statusbar {
  height: 28px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
  font-size: 11.5px;
  color: var(--text-muted);
  user-select: none;
}
.statusbar-text strong {
  color: var(--text-bright);
}
.link-btn {
  background: transparent;
  border: none;
  padding: 0 2px;
  color: var(--accent);
  font-size: 11.5px;
  cursor: pointer;
  text-decoration: underline;
}
.link-btn:disabled {
  color: var(--text-faint);
  cursor: not-allowed;
  text-decoration: none;
}
.link-btn.danger {
  color: var(--state-failed);
}
.statusbar-hint {
  margin-left: auto;
  color: var(--text-faint);
  font-size: 11px;
}

.table-scroll-wrapper {
  flex: 1;
  overflow: auto;
}

.tracks-table {
  border-collapse: collapse;
  font-size: 12.5px;
  /* 固定布局 + colgroup 显式宽度：编辑时不因输入框出现而改变列宽 */
  table-layout: fixed;
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
  position: relative;
  height: 30px;
  padding: 0 8px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-muted);
  text-align: left;
  border-right: 1px solid var(--border);
  user-select: none;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 列宽拖拽手柄（BIZ-015） */
.col-resizer {
  position: absolute;
  top: 0;
  right: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
}
.col-resizer:hover {
  background: var(--accent-soft);
  box-shadow: inset -2px 0 0 var(--accent);
}

tbody tr {
  height: 30px;
  border-bottom: 1px solid var(--border);
  /* 整行均可作为拖拽排序手柄（BIZ-022） */
  cursor: grab;
  transition: background-color 0.1s ease;
}

tbody tr:hover {
  background-color: var(--surface-hover);
}

tbody tr.selected {
  background-color: var(--accent-soft) !important;
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
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 文件名列：次要信息配色（BIZ-024） */
td.col-filename {
  color: var(--text-muted);
}

/* 待写回的文件名（重命名/归档结果，BIZ-030） */
.pending-name {
  color: var(--accent);
  font-weight: 500;
}

/* 状态列：8px 圆点（BIZ-022，参考原型最左侧列） */
td.col-status {
  text-align: center;
}
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  vertical-align: middle;
  background: var(--text-muted);
}
.status-dot.saved {
  background: var(--state-ok);
}
.status-dot.edited {
  background: var(--state-edited);
}
.status-dot.matched {
  background: var(--state-matched);
}
.status-dot.failed {
  background: var(--state-failed);
}

/* 已更改未写回的单格染色（BIZ-022，参考原型 td.cell-edited / td.cell-matched） */
td.cell-edited {
  background: var(--state-edited-soft);
  box-shadow: inset 3px 0 0 var(--state-edited);
  color: var(--text-bright);
}
td.cell-matched {
  background: var(--state-matched-soft);
  box-shadow: inset 3px 0 0 var(--state-matched);
  color: var(--text-bright);
}

.align-right {
  text-align: right;
}

/* 拖拽排序（FR-3 / BIZ-016 / BIZ-022）：整行可拖，排序不改状态也不染色 */
tbody tr.is-dragging {
  cursor: grabbing;
}
tbody tr.drag-over {
  box-shadow: inset 0 2px 0 var(--accent);
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
  /* 固定列宽下，输入框必须占据单元格内部空间，不能撑开整列 */
  box-sizing: border-box;
  background: var(--surface);
  color: var(--text-bright);
  border: 1px solid var(--accent);
  border-radius: 3px;
  font-family: inherit;
  font-size: 12px;
  outline: none;
  box-shadow: 0 0 0 2px var(--accent-soft);
}
</style>
