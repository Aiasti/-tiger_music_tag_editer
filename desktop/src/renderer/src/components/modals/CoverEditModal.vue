<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { fetchCoverUrl } from '../../api/client'
import { trackBaseName } from '../../utils/trackText'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, selectedTrack, selectedTracks, pendingCovers, setPendingCover } =
  useWorkspace()

/** 本地新选图片（data URL，尚未应用，仅用于预览） */
const pickedImage = ref<string | null>(null)
/** 是否已标记移除封面 */
const removeMarked = ref(false)
/** 需先移除的封面文件读取状态 */
const coverLoading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pickError = ref<string | null>(null)

/** 批量作用范围：当前选区，未选中则全部可编辑曲目（BIZ-015、BIZ-021） */
const scopeTracks = computed(() => operationScope.value.filter((t) => t.status !== 'failed'))
const scopeIsSelection = computed(() => selectedTracks.value.length > 0)

/** 预览曲目：优先当前主选中行（若在作用范围内），否则取范围内首曲 */
const previewTrack = computed(() => {
  const list = scopeTracks.value
  const current = selectedTrack.value
  if (current && list.some((t) => t.id === current.id)) {
    return current
  }
  return list[0] ?? null
})

// 内嵌封面（BIZ-020）：按预览曲目从后端按需获取，切换时释放上一个对象 URL
const existingCoverUrl = ref<string | null>(null)

const releaseExistingCover = () => {
  if (existingCoverUrl.value) {
    URL.revokeObjectURL(existingCoverUrl.value)
    existingCoverUrl.value = null
  }
}

const storedPending = computed(() => {
  const id = previewTrack.value?.id
  return id == null ? undefined : pendingCovers.value[id]
})

/** 预览优先级：本次新选 > 已标记移除 > 编辑态已有值 > 文件内嵌封面 */
const previewUrl = computed(() => {
  if (pickedImage.value) return pickedImage.value
  if (removeMarked.value) return null
  if (storedPending.value !== undefined) {
    return storedPending.value === '' ? null : storedPending.value
  }
  return existingCoverUrl.value
})

const previewStateText = computed(() => {
  if (pickedImage.value) return '新选择的图片（待应用）'
  if (removeMarked.value) return '已标记移除封面'
  if (storedPending.value !== undefined) {
    return storedPending.value === '' ? '已标记移除封面' : '编辑态封面（待写回）'
  }
  return existingCoverUrl.value ? '文件内嵌封面' : '无封面'
})

watch(
  () => [previewTrack.value?.id, previewTrack.value?.hasCover] as const,
  async ([id, hasCover]) => {
    releaseExistingCover()
    pickedImage.value = null
    removeMarked.value = false
    pickError.value = null
    if (id == null || !hasCover) return
    coverLoading.value = true
    try {
      existingCoverUrl.value = await fetchCoverUrl(id)
    } catch {
      existingCoverUrl.value = null
    } finally {
      coverLoading.value = false
    }
  },
  { immediate: true }
)

onUnmounted(releaseExistingCover)

const openPicker = () => {
  fileInputRef.value?.click()
}

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    pickError.value = '请选择图片文件（PNG / JPG / WEBP 等）'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    pickError.value = '图片超过 10 MB，请换一张更小的图片'
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    pickedImage.value = typeof reader.result === 'string' ? reader.result : null
    removeMarked.value = false
    pickError.value = null
  }
  reader.onerror = () => {
    pickError.value = '图片读取失败，请重试'
  }
  reader.readAsDataURL(file)
}

const markRemove = () => {
  pickedImage.value = null
  removeMarked.value = true
  pickError.value = null
}

/**
 * 应用封面（待写回）：按作用范围批量写入内存编辑态（BIZ-021），
 * 落盘需在主工具栏点「💾 确认写回」。
 */
const handleApply = () => {
  const value = removeMarked.value ? '' : pickedImage.value
  if (value === null) {
    emit('close')
    return
  }
  scopeTracks.value.forEach((track) => setPendingCover(track.id, value))
  emit('close')
}

const canApply = computed(
  () => scopeTracks.value.length > 0 && (pickedImage.value !== null || removeMarked.value)
)
</script>

<template>
  <ModalContainer title="🖼️ 编辑封面" max-width="520px" @close="emit('close')">
    <div class="cover-edit-body">
      <template v-if="scopeTracks.length > 0">
        <!-- 作用范围（BIZ-015 / BIZ-021） -->
        <div class="scope-strip">
          <span>
            将应用到
            <strong>{{ scopeIsSelection ? '当前选区' : '工作区全部可编辑曲目' }}</strong>
            · 共 <strong>{{ scopeTracks.length }}</strong> 首
          </span>
          <span class="scope-tip">仅内存编辑态，需「💾 确认写回」才落盘</span>
        </div>

        <div class="track-line">
          <span class="track-label">预览曲目</span>
          <span class="track-name">
            {{ previewTrack?.tags.title || (previewTrack ? trackBaseName(previewTrack) : '') }}
          </span>
          <span class="track-meta">{{ previewTrack?.filename }}</span>
        </div>

        <!-- 封面预览 -->
        <div class="preview-wrap">
          <img v-if="previewUrl" class="preview-img" :src="previewUrl" alt="封面预览" />
          <div v-else class="preview-empty">
            <span class="preview-icon">{{ coverLoading ? '…' : '🖼️' }}</span>
            <span class="preview-tip">无封面</span>
          </div>
        </div>
        <div class="cover-state-line">当前：{{ previewStateText }}</div>

        <!-- 操作 -->
        <div class="action-row">
          <button class="btn" title="从本地选择一张图片作为封面" @click="openPicker">
            📁 选择图片…
          </button>
          <button
            class="btn"
            title="标记移除封面（作用于当前作用范围，待写回）"
            @click="markRemove"
          >
            🗑️ 移除封面
          </button>
          <input
            ref="fileInputRef"
            class="file-input"
            type="file"
            accept="image/*"
            @change="handleFileChange"
          />
        </div>
        <p v-if="pickError" class="pick-error">{{ pickError }}</p>

        <div class="persist-alert">
          🛡️ 封面变更将应用到作用范围内全部曲目，且仅进入内存编辑态；必须点击主工具栏「💾 确认写回」后才会写入音频文件。
        </div>
      </template>

      <div v-else class="empty-state">
        <div class="empty-icon">🖼️</div>
        <p>工作区暂无可操作曲目</p>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" :disabled="!canApply" @click="handleApply">
        应用到 {{ scopeTracks.length }} 首（待写回）
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.cover-edit-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.scope-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-xs);
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
}
.scope-strip strong {
  color: var(--text-bright);
}

.track-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.track-label {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.track-name {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-bright);
  word-break: break-all;
}
.track-meta {
  font-size: var(--font-xs);
  color: var(--text-muted);
  word-break: break-all;
}

.preview-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 220px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  overflow: hidden;
}
.preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
}
.preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}
.preview-icon {
  font-size: 34px;
}
.preview-tip {
  font-size: var(--font-xs);
}

.cover-state-line {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.action-row {
  display: flex;
  gap: var(--space-xs);
}
.file-input {
  display: none;
}
.pick-error {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--state-failed);
}

.persist-alert {
  font-size: var(--font-xs);
  line-height: 1.5;
  color: var(--text-muted);
  background: var(--info-dim);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: var(--space-xl);
  color: var(--text-muted);
}
.empty-icon {
  font-size: 34px;
}
</style>
