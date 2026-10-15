<script setup lang="ts">
import ModalContainer from './ModalContainer.vue'
import { useModals } from '../../composables/useModals'
import { useWorkspace } from '../../composables/useWorkspace'

const { closeModal } = useModals()
const { skipped } = useWorkspace()
</script>

<template>
  <ModalContainer title="⚠️ 导入跳过项与异常清单 (FR-1)" max-width="680px">
    <p class="intro-text">
      导入时对无法读取的音频（损坏、无权限、被占用）做了单文件隔离，未阻断其余音频导入；
      非音频扩展名的文件会被静默忽略，不出现在此处（BIZ-014）。
    </p>

    <div v-if="skipped.length === 0" class="empty-hint">本次导入没有跳过项。</div>

    <div v-else class="skip-table-wrapper">
      <table class="skip-table">
        <thead>
          <tr>
            <th>文件名称</th>
            <th>绝对路径</th>
            <th>跳过原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in skipped" :key="item.path">
            <td class="bold-cell">{{ item.filename }}</td>
            <td class="path-cell" :title="item.path">{{ item.path }}</td>
            <td class="reason-cell">{{ item.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <template #footer>
      <button class="btn primary" @click="closeModal">我知道了</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.intro-text {
  color: var(--text-muted);
  font-size: 12px;
}
.skip-table-wrapper {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
}
.skip-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
}
.skip-table th {
  background: var(--surface-3);
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid var(--border-strong);
  color: var(--text-muted);
}
.skip-table td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}
.bold-cell {
  color: var(--text-bright);
  font-weight: 500;
  white-space: nowrap;
}
.path-cell {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reason-cell {
  color: var(--state-failed);
}
.empty-hint {
  padding: 18px 4px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
