<script setup lang="ts">
import { computed } from 'vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { useModals } from '../../composables/useModals'

const { tracks, uncommittedCount, skipped } = useWorkspace()
const { openModal } = useModals()

const failedCount = computed(() => tracks.value.filter((t) => t.status === 'failed').length)
</script>

<template>
  <footer class="app-footer">
    <div class="footer-left">
      <span class="metric-item">
        总曲目：<strong>{{ tracks.length }} 首</strong>
      </span>
      <span class="metric-divider">|</span>
      <span class="metric-item">
        已编辑未提交：
        <strong :style="{ color: uncommittedCount > 0 ? 'var(--state-edited)' : 'var(--text-muted)' }">
          {{ uncommittedCount }} 首
        </strong>
      </span>
      <span class="metric-divider">|</span>
      <span class="metric-item" v-if="failedCount > 0">
        异常状态：<strong style="color: var(--state-failed)">{{ failedCount }} 首损坏/解析失败</strong>
      </span>
    </div>

    <div class="footer-right">
      <div class="rate-limit-pill" title="遵守 BIZ-009 与 PRD FR-5: 客户端严格限制 ≤1.0 req/s">
        <span class="dot"></span>
        <span>MusicBrainz 限流保护: <strong>≤1.0 req/s</strong> (空闲)</span>
      </div>

      <button
        v-if="skipped.length > 0"
        class="skip-btn"
        title="查看导入时因解析损坏被跳过的文件清单 (FR-1)"
        @click="openModal('skip-list')"
      >
        ⚠️ {{ skipped.length }} 项被跳过 (点击查看详情)
      </button>
      <span v-else class="metric-item skip-empty">无跳过项</span>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 26px;
  padding: 0 10px;
  background: var(--surface-2);
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-muted);
  user-select: none;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.metric-item strong {
  color: var(--text-bright);
}
.metric-divider {
  color: var(--border-strong);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rate-limit-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 1px 6px;
  border-radius: 10px;
  background: var(--surface-3);
  border: 1px solid var(--border-strong);
  font-size: 10.5px;
}
.rate-limit-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--state-ok);
}

.skip-btn {
  background: transparent;
  border: none;
  color: var(--state-failed);
  font-size: 11px;
  cursor: pointer;
  text-decoration: underline;
  padding: 0 2px;
}
.skip-btn:hover {
  filter: brightness(1.2);
}
.skip-empty {
  color: var(--text-muted);
}
</style>
