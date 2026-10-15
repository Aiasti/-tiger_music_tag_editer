<script setup lang="ts">
import ModalContainer from './ModalContainer.vue'
import { useModals } from '../../composables/useModals'

const { closeModal } = useModals()

const skipList = [
  {
    filename: 'track_08_corrupted.dsf',
    path: 'D:/Music/Tiger Live 2026/track_08_corrupted.dsf',
    reason: '不支持的音频格式扩展名 (.dsf)，首版仅支持 6 类主流格式 (MP3/FLAC/M4A-AAC/ALAC/OGG/WAV)'
  },
  {
    filename: '05. Outro.wav',
    path: 'D:/Music/Tiger Live 2026/05. Outro.wav',
    reason: 'RIFF 头部校验和异常截断 (CorruptHeaderException)，已载入工作区但标记为损坏状态'
  }
]
</script>

<template>
  <ModalContainer title="⚠️ 导入跳过项与异常清单 (FR-1)" max-width="680px">
    <p class="intro-text">
      在导入文件夹时，系统对不支持的文件格式或解析失败的音频进行了安全隔离，未阻断有效音轨的载入。
    </p>

    <div class="skip-table-wrapper">
      <table class="skip-table">
        <thead>
          <tr>
            <th>文件名称</th>
            <th>绝对路径</th>
            <th>跳过 / 异常原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in skipList" :key="item.filename">
            <td class="bold-cell">{{ item.filename }}</td>
            <td class="path-cell" :title="item.path">{{ item.path }}</td>
            <td class="reason-cell">{{ item.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <template #footer>
      <button class="btn" @click="closeModal">复制列表</button>
      <button class="btn primary" @click="closeModal">我知道了</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.intro-text {
  color: var(--text-muted);
  font-size: 12px;
}
.skip-table-wrapper {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
}
.skip-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
}
.skip-table th {
  background: var(--surface-3);
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid var(--border-strong);
  color: var(--text-muted);
}
.skip-table td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}
.bold-cell {
  color: var(--text-bright);
  font-weight: 500;
  white-space: nowrap;
}
.path-cell {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reason-cell {
  color: var(--state-failed);
}
</style>
