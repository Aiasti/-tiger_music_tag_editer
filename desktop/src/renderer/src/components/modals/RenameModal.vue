<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, selectedTrackIds, renamePreview, previewingRename, loadRenamePreview, applyRenamePlan } =
  useWorkspace()

/** 文件名模板（占位符）；默认「音轨号 标题」（BIZ-030：首版即可编辑）。 */
const nameTemplate = ref('%track% %title%')
/** 应用结果提示。 */
const appliedCount = ref<number | null>(null)

let debounce: ReturnType<typeof setTimeout> | null = null

const refresh = () => {
  if (selectedTracks.value.length === 0) {
    return Promise.resolve([])
  }
  return loadRenamePreview({ mode: 'rename', nameTemplate: nameTemplate.value }, selectedTracks.value)
}

const scheduleRefresh = () => {
  appliedCount.value = null
  if (debounce) {
    clearTimeout(debounce)
  }
  debounce = setTimeout(refresh, 300)
}

onMounted(refresh)
watch(selectedTrackIds, refresh)

const changedCount = computed(() => renamePreview.value.filter((item) => item.changed).length)

const handleApply = () => {
  appliedCount.value = applyRenamePlan(renamePreview.value)
}

const statusOf = (item: (typeof renamePreview.value)[number]): { label: string; cls: string } => {
  if (!item.changed) {
    return { label: '无需变更', cls: 'muted' }
  }
  if (item.adjusted) {
    return { label: '因冲突调整', cls: 'warning' }
  }
  if (item.note) {
    return { label: '批内顺序调整', cls: 'info' }
  }
  return { label: '就绪', cls: 'success' }
}
</script>

<template>
  <ModalContainer title="🏷️ 重命名（按选区）(FR-7 / BIZ-030)" max-width="880px" @close="emit('close')">
    <div class="rename-modal-body">
      <div class="banner">
        <span>ℹ️</span>
        <div class="banner-text">
          <strong>只改变文件名，保留原扩展名与所在目录</strong>
          <p>
            作用于当前选中的 {{ selectedTracks.length }} 首曲目；渲染后自动清洗非法字符、重名冲突加序号。
            占位符：<code>%track%</code> <code>%title%</code> <code>%artist%</code> <code>%albumArtist%</code>
            <code>%album%</code> <code>%disc%</code> <code>%year%</code>
          </p>
        </div>
      </div>

      <div class="root-row">
        <label>文件名模板：</label>
        <input v-model="nameTemplate" type="text" class="input root-input" @input="scheduleRefresh" />
        <button class="btn" :disabled="previewingRename" @click="refresh">重新预览</button>
      </div>

      <div class="preview-table-wrapper">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width: 40px;">#</th>
              <th>当前文件名</th>
              <th style="width: 30px;"></th>
              <th>重命名后文件名</th>
              <th style="width: 120px;">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in renamePreview" :key="item.id">
              <td>{{ idx + 1 }}</td>
              <td class="cell-old-path" :title="item.oldPath">{{ item.oldName }}</td>
              <td class="cell-arrow">➔</td>
              <td class="cell-new-path" :title="item.newPath">{{ item.newName }}</td>
              <td>
                <span class="badge" :class="statusOf(item).cls">{{ statusOf(item).label }}</span>
                <span v-if="item.note" class="note" :title="item.note">ⓘ</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="selectedTracks.length === 0" class="rename-note">未选中任何曲目：请先在表格中选择要重命名的行。</p>
      <p v-else-if="previewingRename" class="rename-note">正在规划…</p>
      <p v-else-if="renamePreview.length === 0" class="rename-note">没有可预览的曲目。</p>
      <p v-else class="rename-note">
        * 点「应用」只写入**待写回态**（{{ changedCount }} 项将改名），不落盘；实际执行在「💾 确认写回」。
      </p>
      <p v-if="appliedCount !== null" class="applied-note">
        已应用到待写回态：{{ appliedCount }} 项；请在工具栏「💾 确认写回」时落盘。
      </p>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">关闭</button>
      <button class="btn primary" :disabled="changedCount === 0" @click="handleApply">
        应用到待写回（{{ changedCount }} 项）
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.rename-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.banner {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  background: var(--info-dim, var(--bg-hover));
  border: 1px solid var(--border-subtle);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.banner-text code {
  font-family: var(--font-mono);
  color: var(--accent);
  margin-right: 3px;
}

.root-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.root-row label {
  font-size: var(--font-sm);
  font-weight: 600;
  white-space: nowrap;
}

.root-input {
  flex: 1;
  font-family: var(--font-mono);
}

.preview-table-wrapper {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-xs);
}

.preview-table th,
.preview-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle);
}

.preview-table th {
  background: var(--bg-hover);
  color: var(--text-muted);
  font-weight: 600;
  position: sticky;
  top: 0;
}

.cell-old-path {
  color: var(--text-muted);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-arrow {
  color: var(--accent);
  text-align: center;
}

.cell-new-path {
  color: var(--accent);
  font-weight: 500;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  font-size: 10.5px;
  border: 1px solid var(--border-subtle);
  white-space: nowrap;
}

.badge.success {
  color: var(--success, var(--text-main));
  border-color: var(--success, var(--border-strong));
}

.badge.warning {
  color: var(--warning);
  border-color: var(--warning);
}

.badge.info {
  color: var(--accent);
  border-color: var(--accent);
}

.badge.muted {
  color: var(--text-muted);
}

.note {
  margin-left: 4px;
  color: var(--text-muted);
  cursor: help;
}

.rename-note {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.applied-note {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--accent);
}
</style>
