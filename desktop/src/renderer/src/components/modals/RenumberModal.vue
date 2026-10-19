<script setup lang="ts">
import { computed, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { trackBaseName } from '../../utils/trackText'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { operationScope, renumberGroups, renumberTracks } = useWorkspace()

const autoFillTotal = ref(false)
/** 存在缺碟号曲目时，是否对其余曲目继续编号（BIZ-003）。 */
const continueAfterMissing = ref(true)
/** 可选项：添加/更改碟号（BIZ-030）——勾选则把范围内全部曲目的碟号统一设为该值。 */
const setDisc = ref(false)
const discNumber = ref(1)

const groups = computed(() => renumberGroups.value.entries)
const missing = computed(() => renumberGroups.value.missing)
const numberedCount = computed(() => groups.value.reduce((sum, group) => sum + group.tracks.length, 0))

const canApply = computed(() =>
  setDisc.value
    ? operationScope.value.length > 0
    : groups.value.length > 0 && (missing.value.length === 0 || continueAfterMissing.value)
)

const handleConfirm = () => {
  renumberTracks(autoFillTotal.value, setDisc.value ? discNumber.value : null)
  emit('close')
}
</script>

<template>
  <ModalContainer title="🔢 批量编号（按碟号分组）" max-width="560px" @close="emit('close')">
    <div class="modal-body-form">
      <div class="banner" :class="missing.length > 0 ? 'warning' : 'info'">
        <span>{{ missing.length > 0 ? '⚠️' : 'ℹ️' }}</span>
        <div class="banner-text">
          <strong>按碟号分组，组内自 1 起连续编号</strong>
          <p v-if="!setDisc">
            作用范围为 {{ operationScope.length }} 首曲目，按各自碟号分成 {{ groups.length }} 组；碟号仅作分组依据，不会被改写。
          </p>
          <p v-else>
            作用范围为 {{ operationScope.length }} 首曲目，将统一碟号并作为一组自 1 起编号。
          </p>
        </div>
      </div>

      <!-- 分组预览（不勾选改碟号时） -->
      <div v-if="!setDisc" class="group-list">
        <div v-if="groups.length === 0" class="empty-hint">范围内没有含碟号的曲目。</div>
        <div v-for="group in groups" :key="group.disc" class="group-row">
          <span class="disc-chip">Disc {{ group.disc }}</span>
          <span class="group-count">{{ group.tracks.length }} 首</span>
          <span class="group-range">
            编号 1{{ autoFillTotal ? '/' + group.tracks.length : '' }} …
            {{ group.tracks.length }}{{ autoFillTotal ? '/' + group.tracks.length : '' }}
          </span>
        </div>
      </div>

      <!-- 缺碟号提示与询问 -->
      <div v-if="!setDisc && missing.length > 0" class="missing-block">
        <div class="missing-title">以下 {{ missing.length }} 首曲目缺少碟号，将<strong>不参与编号</strong>：</div>
        <div class="missing-names">
          <span v-for="track in missing" :key="track.id" class="missing-name" :title="track.filePath">
            {{ trackBaseName(track) }}
          </span>
        </div>
        <label class="checkbox-label">
          <input v-model="continueAfterMissing" type="checkbox" />
          <span>对其余 {{ numberedCount }} 首曲目继续编号</span>
        </label>
      </div>

      <!-- 可选：添加 / 更改碟号（BIZ-030） -->
      <div class="form-group checkbox-group">
        <label class="checkbox-label">
          <input v-model="setDisc" type="checkbox" />
          <span>添加 / 更改碟号（把范围内全部曲目统一设为该碟号）</span>
        </label>
        <div v-if="setDisc" class="disc-input-row">
          <label>碟号：</label>
          <input v-model.number="discNumber" type="number" min="1" max="99" class="input disc-input" />
          <span class="hint">共 {{ operationScope.length }} 首将作为一组，自 1 起连续编号。</span>
        </div>
      </div>

      <div class="form-group checkbox-group">
        <label class="checkbox-label">
          <input v-model="autoFillTotal" type="checkbox" />
          <span>自动将总曲目数写入音轨号（<code>序号/总数</code>）</span>
        </label>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" :disabled="!canApply" @click="handleConfirm">
        应用编号并标记修改
      </button>
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
  border: 1px solid var(--border-subtle);
  font-size: var(--font-sm);
  line-height: 1.5;
  background: var(--bg-hover);
}

.banner.warning {
  background: var(--warning-dim);
  border-color: var(--warning);
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-sm);
  padding: var(--space-xs) var(--space-sm);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.disc-chip {
  color: var(--accent);
  font-weight: 600;
  min-width: 72px;
}

.group-count {
  color: var(--text-muted);
}

.group-range {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.missing-block {
  border: 1px solid var(--warning);
  background: var(--warning-dim);
  border-radius: var(--radius-sm);
  padding: var(--space-sm) var(--space-md);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.missing-title {
  font-size: var(--font-sm);
}

.missing-names {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.missing-name {
  font-size: 10.5px;
  padding: 1px 6px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-hover);
  color: var(--text-muted);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checkbox-group {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.disc-input-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding-left: 22px;
}

.disc-input-row label {
  font-size: var(--font-sm);
  white-space: nowrap;
}

.disc-input {
  width: 84px;
}

.hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-sm);
  cursor: pointer;
  user-select: none;
}

.checkbox-label code {
  font-family: var(--font-mono);
  color: var(--accent);
}

.empty-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
