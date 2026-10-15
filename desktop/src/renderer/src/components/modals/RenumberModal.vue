<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, renumberTracks } = useWorkspace()

const discNumber = ref(1)
const startTrack = ref(1)
const autoFillTotal = ref(true)

const handleConfirm = () => {
  renumberTracks(discNumber.value, startTrack.value, autoFillTotal.value)
  emit('close')
}
</script>

<template>
  <ModalContainer title="🔢 重新编号与碟号处理 (BIZ-003)" max-width="520px" @close="emit('close')">
    <div class="modal-body-form">
      <div class="banner warning">
        <span>⚠️</span>
        <div class="banner-text">
          <strong>检测到选中曲目缺少光盘编号</strong>
          <p>当前选中了 {{ selectedTracks.length }} 首曲目。按 BIZ-003 规则，建议明确指定目标光盘编号或使用默认值 1，避免多碟专辑曲目顺序错乱。</p>
        </div>
      </div>

      <div class="form-group">
        <label>目标光盘编号 (Disc No.)</label>
        <div class="input-with-desc">
          <input v-model.number="discNumber" type="number" min="1" max="99" class="input" />
          <span class="hint">若为单碟专辑保持为 1；多碟请指定实际盘号（例如 2）。</span>
        </div>
      </div>

      <div class="form-group">
        <label>起始音轨号 (Start Track)</label>
        <div class="input-with-desc">
          <input v-model.number="startTrack" type="number" min="1" max="999" class="input" />
          <span class="hint">曲目将按当前列表自上而下顺次递增编号。</span>
        </div>
      </div>

      <div class="form-group checkbox-group">
        <label class="checkbox-label">
          <input v-model="autoFillTotal" type="checkbox" />
          <span>自动将总曲目数设置为选中数量 ({{ selectedTracks.length }})</span>
        </label>
      </div>

      <div class="preview-box">
        <span class="preview-label">编号效果预览：</span>
        <code>第 1 首：{{ startTrack }}/{{ autoFillTotal ? selectedTracks.length : '?' }} (Disc {{ discNumber }})</code>
        <code v-if="selectedTracks.length > 1">... 第 {{ selectedTracks.length }} 首：{{ startTrack + selectedTracks.length - 1 }}/{{ autoFillTotal ? selectedTracks.length : '?' }} (Disc {{ discNumber }})</code>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleConfirm">应用编号并标记修改</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.modal-body-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.banner {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.banner.warning {
  background: var(--warning-dim);
  border: 1px solid var(--warning);
  color: var(--text-main);
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-main);
}

.input-with-desc {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.checkbox-group {
  margin-top: 4px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-sm);
  cursor: pointer;
  user-select: none;
}

.preview-box {
  background: var(--bg-hover);
  border: 1px dashed var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-xs);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-label {
  color: var(--text-muted);
}

.preview-box code {
  color: var(--accent);
  font-family: var(--font-mono);
}
</style>
