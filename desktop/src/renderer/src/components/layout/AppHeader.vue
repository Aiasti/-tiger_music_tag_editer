<script setup lang="ts">
import { useTheme } from '../../composables/useTheme'
import { useModals } from '../../composables/useModals'
import { useWorkspace } from '../../composables/useWorkspace'

const { theme, toggleTheme } = useTheme()
const { openModal } = useModals()
const { uncommittedCount, rollbackAll, importFromDialog, importing, skipped, selectedTrackIds } =
  useWorkspace()

const runImport = async (mode: 'files' | 'folder') => {
  await importFromDialog(mode)
  // 有跳过项时单独提示（FR-1），不阻断其余文件
  if (skipped.value.length > 0) {
    openModal('skip-list')
  }
}

const handleImportFiles = () => runImport('files')

const handleImportFolder = () => runImport('folder')

const handleRollback = () => {
  if (confirm('确定要将工作区所有未写回改动恢复至首次导入快照吗？')) {
    rollbackAll()
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
        <span class="app-version">v0.1.0-alpha (S0.5 基线)</span>
      </div>
      <div class="window-controls">
        <button class="theme-btn" :title="theme === 'dark' ? '切换为浅色主题 (One Light)' : '切换为暗色主题 (One Dark)'" @click="toggleTheme">
          <span v-if="theme === 'dark'">☀️ 浅色</span>
          <span v-else>🌙 暗色</span>
        </button>
        <button class="win-btn" title="最小化">—</button>
        <button class="win-btn" title="最大化">▢</button>
        <button class="win-btn close" title="关闭">✕</button>
      </div>
    </div>

    <!-- A2: 主工具栏 (Primary Toolbar) -->
    <div class="toolbar primary-toolbar">
      <div class="toolbar-group">
        <button class="btn" title="导入单个或多个音频文件 (FR-1)" :disabled="importing" @click="handleImportFiles">
          📁 导入文件
        </button>
        <button class="btn" title="递归扫描导入文件夹 (FR-1)" :disabled="importing" @click="handleImportFolder">
          📂 导入文件夹
        </button>
        <span v-if="importing" class="importing-hint">导入中…</span>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <span class="label-muted">匹配粒度:</span>
        <select class="select sm">
          <option value="album">💿 专辑粒度 (默认)</option>
          <option value="track">🎵 单轨粒度</option>
        </select>
        <button class="btn" title="通过 MusicBrainz/AcoustID 获取基础元数据候选 (FR-5)" @click="openModal('online-match')">
          🌐 在线匹配
        </button>
        <!-- BIZ-012: 独立歌词搜索专属按钮 -->
        <button class="btn" title="批量在线多源检索与匹配内嵌歌词 (FR-6, BIZ-012)" @click="openModal('lyrics-search')">
          🎵 歌词搜索
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button class="btn" title="依据预设模板预览重命名与目录归档 (FR-7)" @click="openModal('rename-preview')">
          🗂️ 重命名与归档
        </button>
        <button class="btn primary writeback-btn" title="将当前编辑态标签与重命名持久化写入磁盘 (FR-8)" @click="openModal('write-confirm')">
          <span>💾 确认写回</span>
          <span v-if="uncommittedCount > 0" class="badge-count">{{ uncommittedCount }}</span>
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <!-- 右侧辅助入口：试听（未实现，禁用并提示）与设置 (FR-9) -->
      <div class="toolbar-group right">
        <span
          class="disabled-slot"
          title="试听功能尚未实现（计划 S5）：需先在「设置」中配置外部播放器与启动参数"
        >
          <button class="btn ghost" disabled>▶ 试听</button>
        </span>
        <button class="btn ghost" title="外部播放器路径与请求节流配置 (FR-9)" @click="openModal('settings')">
          ⚙️ 设置
        </button>
      </div>
    </div>

    <!-- A3: 次工具栏 (Secondary Toolbar) -->
    <div class="toolbar secondary-toolbar">
      <div class="toolbar-group">
        <span class="label-muted">批量操作:</span>
        <span class="scope-badge" title="批量类操作（批量修改 / 按范围编号 / 在线匹配 / 歌词搜索）作用于当前选区（BIZ-015）">
          已选 {{ selectedTrackIds.length }} 首
        </span>
        <button class="btn sm" title="打开批量标签编辑模态窗 (FR-4)" @click="openModal('batch-edit')">
          📝 批量修改
        </button>
        <button class="btn sm" title="按范围顺序重新编号曲目与碟号 (FR-3, BIZ-003)" @click="openModal('renumber-confirm')">
          🔢 按范围编号
        </button>
        <button class="btn sm" title="放弃未写回改动，重置至工作区首次导入快照 (FR-8, BIZ-006)" @click="handleRollback">
          ↺ 提交前回滚
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
  padding: 4px 10px;
  gap: 8px;
  user-select: none;
}
.primary-toolbar {
  height: 40px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border);
}
.secondary-toolbar {
  height: 32px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  font-size: 11.5px;
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
  height: 18px;
  background: var(--border-strong);
  margin: 0 2px;
}

.label-muted {
  font-size: 11.5px;
  color: var(--text-muted);
}
.importing-hint {
  font-size: 11.5px;
  color: var(--accent);
}
.scope-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--accent);
}
/* 未实现功能：禁用按钮，说明放在外层元素以便仍可显示提示 */
.disabled-slot {
  display: inline-flex;
}
.disabled-slot .btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
  color: #1b1c1e;
  font-size: 10px;
  font-weight: 700;
  margin-left: 4px;
}
</style>
