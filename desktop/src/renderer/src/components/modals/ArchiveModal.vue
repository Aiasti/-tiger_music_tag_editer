<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { tracks, renamePreview, previewingRename, writing, importError, loadRenamePreview, commitArchive } =
  useWorkspace()

type ArchiveMode = 'keep' | 'flat' | 'template'

const archiveMode = ref<ArchiveMode>('template')
const rootDir = ref('')
const dirTemplate = ref('%albumArtist%/%album%')

let debounce: ReturnType<typeof setTimeout> | null = null

/** 归档忽略选区，对工作区内全部可编辑曲目生效（BIZ-030）。 */
const scope = computed(() => tracks.value.filter((t) => t.status !== 'failed'))

const refresh = () => {
  if (archiveMode.value !== 'keep' && rootDir.value.trim() === '') {
    renamePreview.value = []
    return Promise.resolve([])
  }
  return loadRenamePreview(
    {
      mode: 'archive',
      archiveMode: archiveMode.value,
      rootDir: rootDir.value.trim() === '' ? null : rootDir.value.trim(),
      dirTemplate: dirTemplate.value
    },
    scope.value
  )
}

const scheduleRefresh = () => {
  if (debounce) {
    clearTimeout(debounce)
  }
  debounce = setTimeout(refresh, 300)
}

const pickRoot = async () => {
  const picked = await window.api.selectFolder()
  if (picked.length > 0) {
    rootDir.value = picked[0]
    scheduleRefresh()
  }
}

onMounted(refresh)
watch([archiveMode, scope], refresh)

const changedCount = computed(() => renamePreview.value.filter((item) => item.changed).length)

const canApply = computed(
  () => changedCount.value > 0 && (archiveMode.value === 'keep' || rootDir.value.trim() !== '')
)

/** 点「确定」立即移动文件（不经过待写回态）；成功后关闭窗口、弹完成提示并重载界面清空工作区。 */
const handleConfirm = async () => {
  const outcomes = await commitArchive(renamePreview.value)
  if (outcomes.length === 0) {
    // 失败时保留窗口，错误信息由 importError 展示，避免静默无反应
    return
  }
  const moved = outcomes.filter((o) => o.outcome === 'written').length
  const failed = outcomes.filter((o) => o.outcome !== 'written')
  const lines = [`归档完成：已移动 ${moved} 个文件。`]
  if (failed.length > 0) {
    lines.push(`${failed.length} 项未完成：`)
    failed.slice(0, 5).forEach((o) => lines.push(`· ${o.reason ?? o.detail ?? o.outcome}`))
    if (failed.length > 5) {
      lines.push(`… 以及另外 ${failed.length - 5} 项`)
    }
  }
  // 有文件被移动时，确认提示后重载界面（渲染层加载即新会话 → 清空工作区，BIZ-027）
  if (moved > 0) {
    lines.push('')
    lines.push('点击「确定」后界面将重载并清空工作区。')
  }
  emit('close')
  alert(lines.join('\n'))
  if (moved > 0) {
    window.location.reload()
  }
}

const statusOf = (item: (typeof renamePreview.value)[number]): { label: string; cls: string } => {
  if (!item.changed) {
    return { label: '无需变更', cls: 'muted' }
  }
  if (item.adjusted) {
    return { label: '因冲突调整', cls: 'warning' }
  }
  if (item.note) {
    return { label: '批内顺序调整', cls: 'info' }
  }
  return { label: '就绪', cls: 'success' }
}
</script>

<template>
  <ModalContainer title="📦 归档（全工作区）" max-width="920px" @close="emit('close')">
    <div class="archive-modal-body">
      <div class="banner">
        <span>ℹ️</span>
        <div class="banner-text">
          <strong>对工作区内全部 {{ scope.length }} 首曲目生效（忽略当前选区）</strong>
          <p>
            位置三种：不移动 / 平铺到所选目录 / 移到所选目录并按目录模板建子目录；归档不改变文件名。
            点「确定」**立即移动磁盘文件**，移动后仍保留在工作区列表。
          </p>
        </div>
      </div>

      <div class="options">
        <label class="option">
          <input v-model="archiveMode" type="radio" value="keep" />
          <span>不移动（保持原目录）</span>
        </label>
        <label class="option">
          <input v-model="archiveMode" type="radio" value="flat" />
          <span>平铺到所选目录（不建子目录）</span>
        </label>
        <label class="option">
          <input v-model="archiveMode" type="radio" value="template" />
          <span>移到所选目录并按目录模板建子目录</span>
        </label>
      </div>

      <div v-if="archiveMode !== 'keep'" class="dir-row">
        <label>目标目录：</label>
        <input v-model="rootDir" type="text" class="input dir-input" placeholder="例如 D:\\Music\\Library" @input="scheduleRefresh" />
        <button class="btn" @click="pickRoot">选择目录…</button>
      </div>

      <div v-if="archiveMode === 'template'" class="dir-row">
        <label>目录模板：</label>
        <input v-model="dirTemplate" type="text" class="input dir-input" @input="scheduleRefresh" />
        <button class="btn" :disabled="previewingRename" @click="refresh">重新预览</button>
      </div>

      <div class="preview-table-wrapper">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width: 40px;">#</th>
              <th>当前路径</th>
              <th style="width: 30px;"></th>
              <th>归档后路径</th>
              <th style="width: 120px;">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in renamePreview" :key="item.id">
              <td>{{ idx + 1 }}</td>
              <td class="cell-path" :title="item.oldPath">{{ item.oldPath }}</td>
              <td class="cell-arrow">➔</td>
              <td class="cell-path new" :title="item.newPath">{{ item.newPath }}</td>
              <td>
                <span class="badge" :class="statusOf(item).cls">{{ statusOf(item).label }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="previewingRename" class="note-line">正在规划…</p>
      <p v-else-if="archiveMode !== 'keep' && rootDir.trim() === ''" class="note-line">请先选择目标目录。</p>
      <p v-else-if="renamePreview.length === 0" class="note-line">没有可预览的曲目。</p>
      <p v-else class="note-line">
        * 点「确定」将**立即移动磁盘文件**（不经过待写回态）；移动后文件仍保留在工作区列表并更新为新路径。
      </p>
      <p v-if="importError" class="error-line">归档失败：{{ importError }}</p>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">关闭</button>
      <button class="btn primary" :disabled="!canApply || writing" @click="handleConfirm">
        {{ writing ? '正在移动…' : `确定归档（立即移动 ${changedCount} 个文件）` }}
      </button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.archive-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.banner {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  background: var(--info-dim, var(--bg-hover));
  border: 1px solid var(--border-subtle);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.option {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-sm);
  cursor: pointer;
  user-select: none;
}

.dir-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.dir-row label {
  font-size: var(--font-sm);
  font-weight: 600;
  white-space: nowrap;
}

.dir-input {
  flex: 1;
  font-family: var(--font-mono);
}

.preview-table-wrapper {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-xs);
}

.preview-table th,
.preview-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle);
}

.preview-table th {
  background: var(--bg-hover);
  color: var(--text-muted);
  font-weight: 600;
  position: sticky;
  top: 0;
}

.cell-path {
  color: var(--text-muted);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-path.new {
  color: var(--accent);
  font-weight: 500;
}

.cell-arrow {
  color: var(--accent);
  text-align: center;
}

.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  font-size: 10.5px;
  border: 1px solid var(--border-subtle);
  white-space: nowrap;
}

.badge.success {
  color: var(--success, var(--text-main));
  border-color: var(--success, var(--border-strong));
}

.badge.warning {
  color: var(--warning);
  border-color: var(--warning);
}

.badge.info {
  color: var(--accent);
  border-color: var(--accent);
}

.badge.muted {
  color: var(--text-muted);
}

.note-line {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.error-line {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--state-failed);
}
</style>
