<script setup lang="ts">
import { onMounted } from 'vue'
import { useTheme } from './composables/useTheme'
import { useModals } from './composables/useModals'
import { useWorkspace } from './composables/useWorkspace'

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
import CoverEditModal from './components/modals/CoverEditModal.vue'
import RenameModal from './components/modals/RenameModal.vue'
import ArchiveModal from './components/modals/ArchiveModal.vue'
import WriteConfirmModal from './components/modals/WriteConfirmModal.vue'
import SettingsModal from './components/modals/SettingsModal.vue'

const { initTheme } = useTheme()
const { activeModal, closeModal } = useModals()
const { tracks, importing, importError, clearWorkspaceSession } = useWorkspace()

onMounted(async () => {
  initTheme()
  // 工作区为纯会话状态：每次渲染层加载（含重载/热重载）都视为新会话，加载即清空后端工作区（BIZ-017、BIZ-027）
  await clearWorkspaceSession()
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
        <div v-if="importing" class="workspace-overlay">
          <span class="overlay-text">正在读取标签…</span>
        </div>
        <div v-else-if="tracks.length === 0" class="workspace-overlay">
          <div class="overlay-empty">
            <p class="empty-title">工作区为空</p>
            <p class="empty-desc">点击左上角「导入文件」或「导入文件夹」开始整理音频标签。</p>
            <p v-if="importError" class="empty-error">{{ importError }}</p>
          </div>
        </div>
      </section>
      <aside class="workspace-detail-area">
        <DetailPane />
      </aside>
    </main>

    <!-- A6 底部状态栏 -->
    <AppFooter />

    <!-- 交互模态体系挂载 -->
    <SkipListModal v-if="activeModal === 'skip-list'" @close="closeModal" />
    <RenumberModal v-if="activeModal === 'renumber-confirm'" @close="closeModal" />
    <BatchEditModal v-if="activeModal === 'batch-edit'" @close="closeModal" />
    <OnlineMatchModal v-if="activeModal === 'online-match'" @close="closeModal" />
    <LyricsSearchModal v-if="activeModal === 'lyrics-search'" @close="closeModal" />
    <CoverEditModal v-if="activeModal === 'cover-edit'" @close="closeModal" />
    <RenameModal v-if="activeModal === 'rename'" @close="closeModal" />
    <ArchiveModal v-if="activeModal === 'archive'" @close="closeModal" />
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
  position: relative;
}

.workspace-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-base);
  opacity: 0.94;
}
.overlay-text {
  color: var(--text-muted);
  font-size: 13px;
}
.overlay-empty {
  text-align: center;
  max-width: 420px;
}
.empty-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--text-bright);
}
.empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
}
.empty-error {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--state-failed);
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
