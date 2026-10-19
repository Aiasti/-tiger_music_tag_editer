<script setup lang="ts">
import { useTheme } from '../../composables/useTheme'
import { useModals } from '../../composables/useModals'
import { useWorkspace } from '../../composables/useWorkspace'
import { useSettings } from '../../composables/useSettings'
import { useConfirm } from '../../composables/useConfirm'

const { theme, toggleTheme } = useTheme()
const { openModal } = useModals()
const { confirm } = useConfirm()
const {
  uncommittedCount,
  rollbackAll,
  importFromDialog,
  importing,
  skipped,
  selectedTrackIds,
  selectedTrack,
  selectedTracks,
  isWorkspaceDirty
} = useWorkspace()
const { playerConfigured, playTrack } = useSettings()

const runImport = async (mode: 'files' | 'folder') => {
  await importFromDialog(mode)
  // 有跳过项时单独提示（FR-1），不阻断其余文件
  if (skipped.value.length > 0) {
    openModal('skip-list')
  }
}

const handleImportFiles = () => runImport('files')

const handleImportFolder = () => runImport('folder')

const minimizeWindow = () => {
  void window.api.minimizeWindow()
}

const toggleMaximizeWindow = () => {
  void window.api.toggleMaximizeWindow()
}

const closeWindow = () => {
  void window.api.closeWindow()
}

const handleRollback = async () => {
  const confirmed = await confirm({
    title: '重置改动',
    message: '确定要将工作区所有未写回改动恢复至首次导入快照吗？'
  })
  if (confirmed) {
    rollbackAll()
  }
}

/**
 * 归档入口（BIZ-030）：有两个保险层——
 * ① 按钮在存在未写回内容时**禁用**（不可交互）；
 * ② 兜底守卫：若仍被触发，先询问是否写回，选「确定」跳转到「确认写回」弹窗，
 *    选「取消」则不执行归档、未写回内容保持原样。
 */
const handleArchive = async () => {
  if (uncommittedCount.value > 0) {
    const goWriteBack = await confirm({
      title: '归档',
      message: `工作区存在 ${uncommittedCount.value} 首曲目的未写回更改。\n是否先写回？选择「取消」将不执行归档，未写回内容保持原样。`
    })
    if (goWriteBack) {
      openModal('write-confirm')
    }
    return
  }
  openModal('archive')
}

/** 外部播放器试听（FR-9、S5）：多选时把全部选中曲目交给播放器。 */
const handleListen = async () => {
  const targets =
    selectedTracks.value.length > 0
      ? selectedTracks.value
      : selectedTrack.value
        ? [selectedTrack.value]
        : []
  if (targets.length === 0) {
    await confirm({
      title: '试听',
      message: '请先在工作区选中要试听的曲目。',
      showCancel: false
    })
    return
  }
  if (!playerConfigured.value) {
    const goSettings = await confirm({
      title: '试听',
      message: '尚未配置外部播放器，是否前往设置？'
    })
    if (goSettings) {
      openModal('settings')
    }
    return
  }
  try {
    await playTrack(targets.map((track) => track.filePath))
  } catch (error) {
    await confirm({
      title: '试听失败',
      message: error instanceof Error ? error.message : String(error),
      showCancel: false
    })
  }
}
</script>

<template>
  <header class="app-header">
    <!-- A1: 标题栏与窗口控制区 -->
    <div class="window-titlebar">
      <div class="app-brand">
        <span class="app-icon">🐯</span>
        <span class="app-name">Tiger Music Tag Editor</span>
        <span class="app-version">v0.1.0-alpha (S5 试听与首选项)</span>
      </div>
      <div class="window-controls">
        <button class="theme-btn" :title="theme === 'dark' ? '切换为浅色主题 (One Light)' : '切换为暗色主题 (One Dark)'" @click="toggleTheme">
          <span v-if="theme === 'dark'">☀️ 浅色</span>
          <span v-else>🌙 暗色</span>
        </button>
        <button class="win-btn" title="最小化" @click="minimizeWindow">—</button>
        <button class="win-btn" title="最大化 / 还原" @click="toggleMaximizeWindow">▢</button>
        <button class="win-btn close" title="关闭" @click="closeWindow">✕</button>
      </div>
    </div>

    <!-- A2: 主工具栏 (Primary Toolbar) -->
    <div class="toolbar primary-toolbar">
      <div class="toolbar-group">
        <!-- 导入按钮合并为分段按钮组（BIZ-018）：导入文件为主操作 -->
        <div class="seg-group" role="group" aria-label="导入音频">
          <button
            class="seg-btn primary"
            title="选择单个或多个音频文件导入工作区 (FR-1)"
            :disabled="importing"
            @click="handleImportFiles"
          >
            📂 导入文件
          </button>
          <button
            class="seg-btn primary"
            title="递归扫描文件夹批量导入音频 (FR-1)"
            :disabled="importing"
            @click="handleImportFolder"
          >
            📁 导入文件夹
          </button>
        </div>
        <span v-if="importing" class="importing-hint">导入中…</span>
      </div>

      <div class="toolbar-divider"></div>

      <div class="seg-group" role="group" aria-label="在线检索">
        <button class="seg-btn" title="按专辑名、音频指纹与元数据匹配封面；只写入封面，不修改标签或歌词 (FR-6)" @click="openModal('cover-match')">
          🖼️ 封面匹配
        </button>
        <button class="seg-btn" title="按当前选区（未选则全部）获取 MusicBrainz/AcoustID 基础元数据候选 (FR-5)" @click="openModal('online-match')">
          🌐 在线匹配
        </button>
        <!-- BIZ-012: 独立歌词搜索专属按钮（歌词主入口，BIZ-018） -->
        <button class="seg-btn" title="多源在线检索与匹配内嵌歌词 (FR-6, BIZ-012)" @click="openModal('lyrics-search')">
          🎵 歌词搜索
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <!-- BIZ-030：重命名（按选区）与归档（全工作区）拆为两个入口 -->
        <button
          class="btn"
          title="按文件名模板重命名当前选中的曲目；未选中任何行时不可用 (FR-7 / BIZ-030)"
          :disabled="selectedTrackIds.length === 0"
          @click="openModal('rename')"
        >
          🏷️ 重命名
        </button>
        <button
          class="btn"
          :title="
            uncommittedCount > 0
              ? '工作区存在未写回更改，请先「💾 确认写回」后再归档 (FR-7 / BIZ-030)'
              : '按目录模板把工作区内全部文件归档到所选目录 (FR-7 / BIZ-030)'
          "
          :disabled="uncommittedCount > 0"
          @click="handleArchive"
        >
          📦 归档
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button class="btn primary writeback-btn" title="将当前编辑态标签与路径变更持久化写入磁盘 (FR-8)" @click="openModal('write-confirm')">
          <span>💾 确认写回</span>
          <span v-if="uncommittedCount > 0" class="badge-count">{{ uncommittedCount }}</span>
        </button>
      </div>

      <!-- 右侧辅助入口：外部播放器试听 (FR-9) 与设置 -->
      <div class="toolbar-group right">
        <button
          class="btn ghost listen-btn"
          :disabled="selectedTracks.length === 0 && !selectedTrack"
          :title="
            playerConfigured
              ? selectedTracks.length > 1
                ? `用外部播放器打开选中的 ${selectedTracks.length} 首曲目`
                : '用外部播放器打开当前选中曲目'
              : '试听需先在「设置」中配置外部播放器与启动参数'
          "
          @click="handleListen"
        >
          <svg class="play-icon" viewBox="0 0 10 12" aria-hidden="true">
            <path d="M0 0 L10 6 L0 12 Z" />
          </svg>
          试听
        </button>
        <button class="btn ghost" title="外部播放器、在线服务与歌词源配置 (FR-9)" @click="openModal('settings')">
          ⚙️ 设置
        </button>
      </div>
    </div>

    <!-- A3: 次工具栏 (Secondary Toolbar)：对齐原型 .action-chip 胶囊 -->
    <div class="toolbar secondary-toolbar">
      <div class="toolbar-group">
        <span class="action-chip scope-chip" title="批量类操作（批量编辑 / 按范围编号 / 在线匹配 / 歌词搜索）作用于当前选区（BIZ-015）">
          <span class="chip-dot"></span>
          已选 {{ selectedTrackIds.length }} 首
        </span>
        <button class="action-chip" title="为当前选中曲目预览 / 选择 / 移除封面 (FR-6, BIZ-020)" @click="openModal('cover-edit')">
          编辑封面
        </button>
        <button class="action-chip" title="批量设置某一元数据字段；未选中曲目时作用于工作区全部可编辑曲目 (FR-4)" @click="openModal('batch-edit')">
          批量编辑字段
        </button>
        <button
          class="action-chip"
          title="按选区与碟号连续分配音轨号；未选中范围时不可触发 (FR-3 / BIZ-003)"
          :disabled="selectedTrackIds.length === 0"
          @click="openModal('renumber-confirm')"
        >
          批量编号
        </button>
        <button
          class="action-chip"
          :disabled="!isWorkspaceDirty"
          :title="
            isWorkspaceDirty
              ? '放弃未写回的标签、歌词、封面、路径及顺序改动，重置至首次导入快照与顺序 (FR-8 / BIZ-006 / BIZ-008)'
              : '工作区没有可重置的改动'
          "
          @click="handleRollback"
        >
          重置改动
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  flex-direction: column;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
}

.window-titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 8px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
  -webkit-app-region: drag;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.app-icon {
  font-size: 14px;
}
.app-name {
  font-weight: 600;
  color: var(--text-bright);
}
.app-version {
  font-size: 11px;
  color: var(--text-muted);
}

.window-controls {
  display: flex;
  align-items: center;
  gap: 4px;
  -webkit-app-region: no-drag;
}
.theme-btn {
  background: var(--surface-3);
  border: 1px solid var(--border-strong);
  color: var(--text);
  padding: 2px 7px;
  font-size: 11px;
  border-radius: 4px;
  cursor: pointer;
  margin-right: 8px;
}
.theme-btn:hover {
  background: var(--surface-hover);
  color: var(--text-bright);
}
.win-btn {
  width: 28px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  border-radius: 3px;
}
.win-btn:hover {
  background: var(--surface-hover);
  color: var(--text-bright);
}
.win-btn.close:hover {
  background: var(--state-failed);
  color: #fff;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  user-select: none;
}
/* 主工具栏底色为 --surface、次工具栏为 --surface-2（对齐原型） */
.primary-toolbar {
  padding: 8px 12px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}
.secondary-toolbar {
  padding: 6px 12px;
  gap: 8px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.toolbar-group.right {
  margin-left: auto;
}
.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--border-strong);
  margin: 0 4px;
}

/* 工具栏按钮：对齐原型 .btn（仅作用于本工具栏，不影响模态窗按钮） */
.toolbar .btn {
  height: 30px;
  padding: 0 11px;
  gap: 6px;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background: var(--surface);
  color: var(--text);
  font-size: 12.5px;
  font-weight: 500;
  transition: all 0.12s ease;
}
.toolbar .btn:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--text-bright);
  border-color: var(--text-muted);
}
.toolbar .btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.toolbar .btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
}
.toolbar .btn.primary:hover:not(:disabled) {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
  color: var(--accent-contrast);
}
.listen-btn .play-icon {
  width: 9px;
  height: 11px;
  flex-shrink: 0;
  fill: var(--accent);
}
.toolbar .btn.ghost {
  background: transparent;
  border-color: transparent;
  color: var(--text-muted);
}
.toolbar .btn.ghost:hover:not(:disabled) {
  background: var(--surface-hover);
  border-color: transparent;
  color: var(--text-bright);
}

/* 次工具栏操作 chip：对齐原型 .action-chip */
.action-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text);
  font-family: inherit;
  font-size: 11.5px;
  cursor: pointer;
  user-select: none;
  transition: all 0.12s ease;
}
.action-chip:hover:not(:disabled) {
  background: var(--surface-hover);
  border-color: var(--border-strong);
  color: var(--text-bright);
}
.action-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.action-chip .chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}
.scope-chip {
  color: var(--accent);
  border-color: var(--accent-soft);
}

/* 分段控制组：对齐原型 .seg-group / .seg-btn（当前用于导入按钮组；匹配粒度控件已按 BIZ-018 移除） */
.seg-group {
  display: inline-flex;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  overflow: hidden;
  background: var(--surface-2);
}
.seg-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  height: 28px;
  padding: 0 10px;
  font-family: inherit;
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.12s ease;
}
.seg-btn:hover:not(:disabled) {
  color: var(--text-bright);
}
/* 主操作段（导入文件）：保留主色强调 */
.seg-btn.primary {
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
}
.seg-btn.primary:hover:not(:disabled) {
  background: var(--accent-hover);
  color: var(--accent-contrast);
}
.seg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.importing-hint {
  font-size: 11.5px;
  color: var(--accent);
}
.writeback-btn {
  position: relative;
}
.badge-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--state-edited);
  color: var(--accent-contrast);
  font-size: 10px;
  font-weight: 700;
  margin-left: 4px;
}
</style>
