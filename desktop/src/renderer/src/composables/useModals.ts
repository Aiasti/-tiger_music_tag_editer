import { ref, onMounted, onUnmounted } from 'vue'
import type { ModalType } from '../types'

const activeModal = ref<ModalType | null>(null)

export function useModals() {
  const openModal = (type: ModalType) => {
    activeModal.value = type
  }

  const closeModal = () => {
    activeModal.value = null
  }

  const handleKeydown = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && activeModal.value) {
      closeModal()
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
  })

  return {
    activeModal,
    openModal,
    closeModal
  }
}
