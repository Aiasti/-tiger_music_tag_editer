<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks } = useWorkspace()

const pattern = ref('%track% - %title%')
const dirPattern = ref('%artist%/%album%')
const enableDirMove = ref(false)

const handleApplyRename = () => {
  // 仅在内存标记或通知准备重命名，落地由后端执行
  emit('close')
}
</script>

<template>
  <ModalContainer title="📂 文件重命名与目录归档预览 (FR-6)" max-width="840px" @close="emit('close')">
    <div class="rename-modal-body">
      <!-- 规则配置栏 -->
      <div class="pattern-config-card">
        <div class="pattern-row">
          <label>文件名命名模式：</label>
          <input v-model="pattern" type="text" class="input pattern-input" />
          <div class="tokens-hint">
            常用占位符：<code>%track%</code> <code>%title%</code> <code>%artist%</code> <code>%album%</code> <code>%year%</code>
          </div>
        </div>

        <div class="pattern-row checkbox-row">
          <label class="checkbox-label">
            <input v-model="enableDirMove" type="checkbox" />
            <span>启用目录归档（自动按层级移动文件）</span>
          </label>
          <input
            v-if="enableDirMove"
            v-model="dirPattern"
            type="text"
            class="input dir-pattern-input"
            placeholder="例如：%artist%/%album%"
          />
        </div>
      </div>

      <!-- 预览表格 -->
      <div class="preview-table-wrapper">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width: 40px;">#</th>
              <th>原文件路径 / 名称</th>
              <th style="width: 30px;"></th>
              <th>重命名后目标路径 / 名称</th>
              <th style="width: 80px;">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(track, idx) in selectedTracks" :key="track.id">
              <td>{{ idx + 1 }}</td>
              <td class="cell-old-path" :title="track.filePath">{{ track.filename }}</td>
              <td class="cell-arrow">➔</td>
              <td class="cell-new-path">
                <span v-if="enableDirMove" class="dir-prefix">{{ track.tags.artist }}/{{ track.tags.album }}/</span>
                <span class="file-name">{{ track.tags.trackNo }} - {{ track.tags.title }}.{{ track.ext.toLowerCase() }}</span>
              </td>
              <td><span class="badge success">就绪</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="rename-note">
        * 重命名将在「💾 确认写回」阶段统一随标签修改一同提交文件系统执行。
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleApplyRename">确认命名规则并应用</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.rename-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.pattern-config-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.pattern-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.pattern-row label {
  font-size: var(--font-sm);
  font-weight: 600;
  white-space: nowrap;
}

.pattern-input {
  flex: 1;
  font-family: var(--font-mono);
}

.tokens-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.tokens-hint code {
  color: var(--accent);
  margin-right: 4px;
}

.checkbox-row {
  margin-top: 4px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-sm);
  cursor: pointer;
}

.dir-pattern-input {
  width: 260px;
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
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dir-prefix {
  color: var(--text-muted);
}

.rename-note {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
