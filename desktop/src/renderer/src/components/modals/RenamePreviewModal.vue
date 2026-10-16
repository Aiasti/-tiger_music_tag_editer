<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, renamePreview, previewingRename, loadRenamePreview } = useWorkspace()

/** 目标根目录；空表示保持各文件当前目录（BIZ-029，此时为纯重命名）。 */
const rootDir = ref('')
/** 防抖计时器：根目录输入变化后重新预览。 */
let debounce: ReturnType<typeof setTimeout> | null = null

const refresh = () => loadRenamePreview(rootDir.value.trim() === '' ? null : rootDir.value.trim())

const onRootDirInput = () => {
  if (debounce) {
    clearTimeout(debounce)
  }
  debounce = setTimeout(refresh, 300)
}

onMounted(refresh)
watch(operationScope, refresh)

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
  <ModalContainer title="🏷️ 重命名 / 归档预览 (FR-7)" max-width="880px" @close="emit('close')">
    <div class="rename-modal-body">
      <div class="banner">
        <span>ℹ️</span>
        <div class="banner-text">
          <strong>内置固定命名模板：&lt;音轨号&gt; &lt;标题&gt;</strong>
          <p>
            首版只改变文件名并保留原扩展名；归档目录由「目标根目录」决定，留空表示保持文件当前目录（纯重命名）。
            作用范围为 {{ operationScope.length }} 首曲目。
          </p>
        </div>
      </div>

      <div class="root-row">
        <label>目标根目录：</label>
        <input
          v-model="rootDir"
          type="text"
          class="input root-input"
          placeholder="留空 = 保持各文件当前所在目录"
          @input="onRootDirInput"
        />
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
                <span v-if="item.adjusted && item.note" class="note" :title="item.note">ⓘ</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="previewingRename" class="rename-note">正在规划…</p>
      <p v-else-if="renamePreview.length === 0" class="rename-note">没有可预览的曲目。</p>
      <div v-else class="rename-note">
        * 本片（S3-1）只做规划与预览，<strong>不会移动或重命名磁盘文件</strong>；实际执行在 S3-2 接入。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">关闭</button>
      <button class="btn primary" disabled title="执行将在 S3-2 接入">应用重命名（S3-2 待接入）</button>
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
</style>
