<script setup lang="ts">
import { useModals } from '../../composables/useModals'

defineProps<{
  title: string
  maxWidth?: string
}>()

const { closeModal } = useModals()

const handleBackdropClick = (e: MouseEvent) => {
  if (e.target === e.currentTarget) {
    closeModal()
  }
}
</script>

<template>
  <div class="modal-backdrop" @click="handleBackdropClick">
    <div class="modal-window" :style="{ maxWidth: maxWidth || '640px' }" @click.stop>
      <div class="modal-header">
        <span class="modal-title">{{ title }}</span>
        <button class="modal-close-btn" title="关闭 (ESC)" @click="closeModal">✕</button>
      </div>

      <div class="modal-body">
        <slot></slot>
      </div>

      <div v-if="$slots.footer" class="modal-footer">
        <slot name="footer"></slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.modal-window {
  width: 90%;
  max-height: 88vh;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
}

.modal-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-bright);
}

.modal-close-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  border-radius: 3px;
}
.modal-close-btn:hover {
  background: var(--surface-hover);
  color: var(--text-bright);
}

.modal-body {
  padding: 14px;
  overflow-y: auto;
  font-size: 12.5px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-footer {
  padding: 10px 14px;
  background: var(--surface-2);
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
</style>
