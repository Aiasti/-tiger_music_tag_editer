<script setup lang="ts">
import { computed, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import type { EditableTagField } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, selectedTracks, batchUpdateTags } = useWorkspace()

interface BatchFieldConfig {
  field: EditableTagField
  label: string
  enabled: boolean
  value: string
  placeholder: string
}

/** 批量编辑的回显基准：作用范围内第一首曲目的当前值。 */
const firstTrack = computed(() => operationScope.value[0] ?? null)
const echo = (field: EditableTagField): string => firstTrack.value?.tags[field] ?? ''

/** 10 个可见标签字段（不含封面、文件名、时长、歌词、碟号、音轨号）；默认全部不勾选。 */
const batchFields = ref<BatchFieldConfig[]>([
  { field: 'title', label: '标题 (Title)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' },
  { field: 'artist', label: '艺术家 (Artist)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' },
  { field: 'album', label: '专辑 (Album)', enabled: false, value: echo('album'), placeholder: '例如：专辑名' },
  { field: 'albumArtist', label: '专辑艺术家 (Album Artist)', enabled: false, value: echo('albumArtist'), placeholder: '例如：Various Artists' },
  { field: 'year', label: '年份 (Year)', enabled: false, value: echo('year'), placeholder: '例如：2024' },
  { field: 'genre', label: '流派 (Genre)', enabled: false, value: echo('genre'), placeholder: '例如：Rock, Pop' },
  { field: 'composer', label: '作曲 (Composer)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' },
  { field: 'lyricist', label: '作词 (Lyricist)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' },
  { field: 'arranger', label: '编曲 (Arranger)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' },
  { field: 'comment', label: '注释 (Comment)', enabled: false, value: '', placeholder: '留空则保持各曲目原值' }
])

const hasEffectiveUpdate = computed(() =>
  batchFields.value.some((item) => item.enabled && item.value.trim() !== '')
)
const scopeLabel = computed(() =>
  selectedTracks.value.length > 0
    ? `当前选区 ${operationScope.value.length} 首曲目`
    : `工作区全部 ${operationScope.value.length} 首可编辑曲目`
)

const handleApply = () => {
  const updates: Partial<Record<EditableTagField, string>> = {}

  batchFields.value.forEach((item) => {
    if (item.enabled && item.value.trim() !== '') {
      updates[item.field] = item.value.trim()
    }
  })

  if (Object.keys(updates).length > 0) {
    batchUpdateTags(updates)
  }
  emit('close')
}
</script>

<template>
  <ModalContainer title="⚡ 批量编辑字段 (FR-4)" max-width="700px" @close="emit('close')">
    <div class="batch-modal-body">
      <div class="banner info">
        <span>💡</span>
        <div class="banner-text">
          <strong>作用范围：{{ scopeLabel }}</strong>
          <p>
            勾选需要统一变更的字段并输入新内容；未勾选的字段保留各曲目原值。专辑、专辑艺术家、流派、年份已回显当前曲目值。
            所有修改仅更新内存编辑态，需点击「💾 确认写回」后才持久化落盘。
          </p>
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
      <button class="btn primary" :disabled="!hasEffectiveUpdate || operationScope.length === 0" @click="handleApply">
        应用修改至 {{ operationScope.length }} 首曲目
      </button>
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
  gap: var(--space-xs);
  max-height: 430px;
  overflow-y: auto;
  padding-right: 4px;
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
  width: 210px;
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
