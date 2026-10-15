<script setup lang="ts">
import { computed } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { tracks, commitAllWrites } = useWorkspace()

const editedTracks = computed(() =>
  tracks.value.filter((t) => t.status === 'edited' || t.status === 'matched')
)

const handleCommit = () => {
  commitAllWrites()
  emit('close')
}
</script>

<template>
  <ModalContainer title="💾 确认写入文件 (FR-7 / 持久化落盘)" max-width="720px" @close="emit('close')">
    <div class="write-modal-body">
      <div class="banner warning">
        <span>⚠️</span>
        <div class="banner-text">
          <strong>即将对磁盘文件执行原子写回</strong>
          <p>
            检测到共有 <strong>{{ editedTracks.length }}</strong> 首曲目包含未保存的元数据修改。确认后将通过后端安全事务管道原子替换音频文件标签块。
          </p>
        </div>
      </div>

      <!-- 待写入清单 -->
      <div class="pending-list-section">
        <div class="section-title">待写入文件列表 ({{ editedTracks.length }} 项)</div>
        <div class="pending-table-wrapper">
          <table class="pending-table">
            <thead>
              <tr>
                <th style="width: 40px;">#</th>
                <th>文件名</th>
                <th>修改标签概要</th>
                <th style="width: 80px;">格式</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(track, idx) in editedTracks" :key="track.id">
                <td>{{ idx + 1 }}</td>
                <td class="file-cell" :title="track.filePath">{{ track.filename }}</td>
                <td class="changes-cell">
                  <span class="change-tag">{{ track.tags.title }}</span>
                  <span class="change-tag">{{ track.tags.artist }}</span>
                  <span v-if="track.tags.lyrics" class="change-tag lyrics">含歌词更新</span>
                </td>
                <td><span class="format-badge">{{ track.ext.toUpperCase() }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="safety-features">
        <div class="feat-item">🛡️ <strong>安全保障：</strong>采用 .tmp 写入后原子替换，写入失败自动回滚</div>
        <div class="feat-item">⏱️ <strong>时间戳保留：</strong>默认保留源文件原始创建与修改时间戳</div>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">暂缓写入</button>
      <button class="btn primary" @click="handleCommit">立即执行原子写入 ({{ editedTracks.length }} 文件)</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.write-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.banner {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.banner.warning {
  background: var(--warning-dim);
  border: 1px solid var(--warning);
  color: var(--text-main);
}

.banner-text p {
  margin: 4px 0 0 0;
  color: var(--text-muted);
}

.section-title {
  font-size: var(--font-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: var(--space-xs);
}

.pending-table-wrapper {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.pending-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-xs);
}

.pending-table th,
.pending-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle);
}

.pending-table th {
  background: var(--bg-hover);
  color: var(--text-muted);
  font-weight: 600;
  position: sticky;
  top: 0;
}

.file-cell {
  color: var(--text-main);
  font-weight: 500;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.changes-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.change-tag {
  background: var(--bg-hover);
  border: 1px solid var(--border-subtle);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 10px;
}

.change-tag.lyrics {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-dim);
}

.format-badge {
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-muted);
}

.safety-features {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
