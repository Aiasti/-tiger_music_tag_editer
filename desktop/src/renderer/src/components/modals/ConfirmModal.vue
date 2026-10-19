<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useConfirm } from '../../composables/useConfirm'

const { confirmState, accept, cancel } = useConfirm()

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && confirmState.value) {
    event.stopPropagation()
    cancel()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown, true))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown, true))
</script>

<template>
  <ModalContainer
    v-if="confirmState"
    :title="confirmState.title ?? '确认'"
    max-width="420px"
    :closable="false"
    @close="cancel"
  >
    <p class="confirm-message">{{ confirmState.message }}</p>

    <template #footer>
      <button
        v-if="confirmState.showCancel !== false"
        class="btn"
        type="button"
        @click="cancel"
      >
        {{ confirmState.cancelText ?? '取消' }}
      </button>
      <button class="btn primary" type="button" @click="accept">
        {{ confirmState.confirmText ?? '确定' }}
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.confirm-message {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text);
  white-space: pre-line;
}
</style>
