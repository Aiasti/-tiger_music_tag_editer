<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import type { EditableTagField } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, batchUpdateTags } = useWorkspace()

interface BatchFieldConfig {
  field: EditableTagField
  label: string
  enabled: boolean
  value: string
  placeholder: string
}

const batchFields = ref<BatchFieldConfig[]>([
  { field: 'artist', label: '艺术家 (Artist)', enabled: false, value: '', placeholder: '保持多选原样，或输入统一艺术家' },
  { field: 'album', label: '专辑 (Album)', enabled: true, value: '', placeholder: '输入统一步调的专辑名' },
  { field: 'albumArtist', label: '专辑艺术家 (Album Artist)', enabled: false, value: '', placeholder: '例如：Various Artists 或主要演出者' },
  { field: 'genre', label: '流派 (Genre)', enabled: false, value: '', placeholder: '例如：Rock, Pop, SoundTrack' },
  { field: 'year', label: '发行年份 (Year)', enabled: false, value: '', placeholder: '例如：2024' },
  { field: 'discNo', label: '光盘编号 (Disc No)', enabled: false, value: '1', placeholder: '例如：1' }
])

const handleApply = () => {
  const updates: Partial<Record<EditableTagField, string>> = {}
  let hasAny = false

  batchFields.value.forEach((item) => {
    if (item.enabled && item.value.trim() !== '') {
      updates[item.field] = item.value.trim()
      hasAny = true
    }
  })

  if (hasAny) {
    batchUpdateTags(updates)
  }
  emit('close')
}
</script>

<template>
  <ModalContainer title="⚡ 批量修改元数据标签 (FR-4)" max-width="640px" @close="emit('close')">
    <div class="batch-modal-body">
      <div class="banner info">
        <span>💡</span>
        <div class="banner-text">
          <strong>当前已选中 {{ selectedTracks.length }} 首曲目</strong>
          <p>勾选需要统一变更的字段并输入新内容。未勾选的字段将完全保留原曲目的个性化值。所有修改仅更新内存编辑态，需点击「💾 确认写回」后才持久化落盘。</p>
        </div>
      </div>

      <div class="field-list">
        <div v-for="item in batchFields" :key="item.field" class="field-row" :class="{ active: item.enabled }">
          <label class="field-checkbox">
            <input v-model="item.enabled" type="checkbox" />
            <span class="field-label-text">{{ item.label }}</span>
          </label>
          <div class="field-input-box">
            <input
              v-model="item.value"
              type="text"
              class="input"
              :placeholder="item.placeholder"
              :disabled="!item.enabled"
            />
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleApply">应用修改至 {{ selectedTracks.length }} 首曲目</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.batch-modal-body {
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

.banner.info {
  background: var(--info-dim);
  border: 1px solid var(--info);
  color: var(--text-main);
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.field-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}

.field-row.active {
  background: var(--bg-hover);
  border-color: var(--border-subtle);
}

.field-checkbox {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  width: 200px;
  flex-shrink: 0;
  cursor: pointer;
  user-select: none;
}

.field-label-text {
  font-size: var(--font-sm);
  font-weight: 500;
  color: var(--text-main);
}

.field-input-box {
  flex: 1;
}

.field-input-box .input:disabled {
  opacity: 0.5;
  background: var(--bg-surface);
  cursor: not-allowed;
}
</style>
