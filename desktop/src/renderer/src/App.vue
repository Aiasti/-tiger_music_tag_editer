<script setup lang="ts">
import { onMounted } from 'vue'
import { useTheme } from './composables/useTheme'
import { useModals } from './composables/useModals'

// 布局组件
import AppHeader from './components/layout/AppHeader.vue'
import AppFooter from './components/layout/AppFooter.vue'
import DetailPane from './components/layout/DetailPane.vue'
import TrackTable from './components/workspace/TrackTable.vue'

// 9 大交互模态弹窗组件
import SkipListModal from './components/modals/SkipListModal.vue'
import RenumberModal from './components/modals/RenumberModal.vue'
import BatchEditModal from './components/modals/BatchEditModal.vue'
import OnlineMatchModal from './components/modals/OnlineMatchModal.vue'
import LyricsSearchModal from './components/modals/LyricsSearchModal.vue'
import LyricsEditModal from './components/modals/LyricsEditModal.vue'
import RenamePreviewModal from './components/modals/RenamePreviewModal.vue'
import WriteConfirmModal from './components/modals/WriteConfirmModal.vue'
import SettingsModal from './components/modals/SettingsModal.vue'

const { initTheme } = useTheme()
const { activeModal, closeModal } = useModals()

onMounted(() => {
  initTheme()
})
</script>

<template>
  <div class="desktop-app-container">
    <!-- A1 ~ A3 顶部区域 -->
    <AppHeader />

    <!-- A4 + A5 主工作区分栏 -->
    <main class="app-workspace-body">
      <section class="workspace-table-area">
        <TrackTable />
      </section>
      <aside class="workspace-detail-area">
        <DetailPane />
      </aside>
    </main>

    <!-- A6 底部状态栏 -->
    <AppFooter />

    <!-- 交互模态体系挂载 -->
    <SkipListModal v-if="activeModal === 'skip-list'" @close="closeModal" />
    <RenumberModal v-if="activeModal === 'renumber'" @close="closeModal" />
    <BatchEditModal v-if="activeModal === 'batch-edit'" @close="closeModal" />
    <OnlineMatchModal v-if="activeModal === 'online-match'" @close="closeModal" />
    <LyricsSearchModal v-if="activeModal === 'lyrics-search'" @close="closeModal" />
    <LyricsEditModal v-if="activeModal === 'lyrics-edit'" @close="closeModal" />
    <RenamePreviewModal v-if="activeModal === 'rename-preview'" @close="closeModal" />
    <WriteConfirmModal v-if="activeModal === 'write-confirm'" @close="closeModal" />
    <SettingsModal v-if="activeModal === 'settings'" @close="closeModal" />
  </div>
</template>

<style scoped>
.desktop-app-container {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-base);
  color: var(--text-main);
}

.app-workspace-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-base);
}

.workspace-table-area {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.workspace-detail-area {
  width: 320px;
  flex-shrink: 0;
  height: 100%;
  border-left: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  display: flex;
  flex-direction: column;
}
</style>
