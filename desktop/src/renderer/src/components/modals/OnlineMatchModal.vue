<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { selectedTracks, batchUpdateTags } = useWorkspace()

const activeTab = ref<'metadata' | 'fingerprint'>('metadata')
const selectedCandidate = ref<number>(0)

const candidates = ref([
  {
    id: 1,
    source: 'MusicBrainz',
    confidence: '98%',
    title: 'Hotel California (Live On MTV, 1994)',
    artist: 'Eagles',
    album: 'Hell Freezes Over',
    year: '1994',
    trackNo: '06/15',
    isrc: 'USIR19400112'
  },
  {
    id: 2,
    source: 'MusicBrainz',
    confidence: '85%',
    title: 'Hotel California',
    artist: 'Eagles',
    album: 'Hotel California (2013 Remaster)',
    year: '2013',
    trackNo: '01/09',
    isrc: 'USEE10001923'
  },
  {
    id: 3,
    source: 'AcoustID',
    confidence: '79%',
    title: 'Hotel California (Acoustic Version)',
    artist: 'Eagles',
    album: 'The Complete Greatest Hits',
    year: '2003',
    trackNo: '04/33',
    isrc: 'USIR10300452'
  }
])

const handleApply = () => {
  const chosen = candidates.value[selectedCandidate.value]
  if (chosen) {
    batchUpdateTags({
      title: chosen.title,
      artist: chosen.artist,
      album: chosen.album,
      year: chosen.year,
      trackNo: chosen.trackNo
    })
  }
  emit('close')
}
</script>

<template>
  <ModalContainer title="🌐 在线元数据匹配与声学指纹对比 (FR-2 / FR-3)" max-width="840px" @close="emit('close')">
    <div class="online-modal-body">
      <div class="tab-header">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'metadata' }"
          @click="activeTab = 'metadata'"
        >
          📝 文本元数据匹配 (MusicBrainz API)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'fingerprint' }"
          @click="activeTab = 'fingerprint'"
        >
          🌊 声学指纹识别 (Chromaprint / AcoustID)
        </button>
      </div>

      <div class="match-info-strip">
        <span>当前分析曲目：<strong>{{ selectedTracks[0]?.tags.title || '选中的曲目' }}</strong></span>
        <span class="rate-limit-badge">限流状态：≤ 1.0 req/s 安全队列中</span>
      </div>

      <div class="match-layout">
        <!-- 候选列表 -->
        <div class="candidates-col">
          <div class="section-title">匹配候选结果 (共 {{ candidates.length }} 条)</div>
          <div class="candidate-cards">
            <div
              v-for="(cand, idx) in candidates"
              :key="cand.id"
              class="candidate-card"
              :class="{ selected: selectedCandidate === idx }"
              @click="selectedCandidate = idx"
            >
              <div class="cand-top">
                <span class="cand-source">{{ cand.source }}</span>
                <span class="cand-confidence">匹配度 {{ cand.confidence }}</span>
              </div>
              <div class="cand-title">{{ cand.title }}</div>
              <div class="cand-sub">{{ cand.artist }} · {{ cand.album }} ({{ cand.year }})</div>
            </div>
          </div>
        </div>

        <!-- 差异比对视窗 -->
        <div class="diff-col">
          <div class="section-title">元数据差异比对 (Diff)</div>
          <div class="diff-table-box">
            <table class="diff-table">
              <thead>
                <tr>
                  <th style="width: 80px;">字段</th>
                  <th>当前本地标签</th>
                  <th></th>
                  <th>匹配候选值</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="field-name">标题</td>
                  <td class="val-curr">{{ selectedTracks[0]?.tags.title }}</td>
                  <td class="val-arrow">➔</td>
                  <td class="val-new">{{ candidates[selectedCandidate].title }}</td>
                </tr>
                <tr>
                  <td class="field-name">艺术家</td>
                  <td class="val-curr">{{ selectedTracks[0]?.tags.artist }}</td>
                  <td class="val-arrow">➔</td>
                  <td class="val-new">{{ candidates[selectedCandidate].artist }}</td>
                </tr>
                <tr>
                  <td class="field-name">专辑</td>
                  <td class="val-curr">{{ selectedTracks[0]?.tags.album }}</td>
                  <td class="val-arrow">➔</td>
                  <td class="val-new">{{ candidates[selectedCandidate].album }}</td>
                </tr>
                <tr>
                  <td class="field-name">年份</td>
                  <td class="val-curr">{{ selectedTracks[0]?.tags.year }}</td>
                  <td class="val-arrow">➔</td>
                  <td class="val-new">{{ candidates[selectedCandidate].year }}</td>
                </tr>
                <tr>
                  <td class="field-name">音轨号</td>
                  <td class="val-curr">{{ selectedTracks[0]?.tags.trackNo }}</td>
                  <td class="val-arrow">➔</td>
                  <td class="val-new">{{ candidates[selectedCandidate].trackNo }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="diff-notice">
            * 应用后仅载入当前内存工作区，不立即持久化落盘。
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">放弃</button>
      <button class="btn primary" @click="handleApply">应用所选候选至当前曲目</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.online-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.tab-header {
  display: flex;
  border-bottom: 1px solid var(--border-subtle);
  gap: var(--space-xs);
}

.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  padding: var(--space-xs) var(--space-md);
  font-size: var(--font-sm);
  color: var(--text-muted);
  cursor: pointer;
  font-family: inherit;
}

.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}

.match-info-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-xs);
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
}

.rate-limit-badge {
  color: var(--success);
  font-family: var(--font-mono);
}

.match-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--space-md);
}

.section-title {
  font-size: var(--font-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: var(--space-xs);
}

.candidate-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 340px;
  overflow-y: auto;
}

.candidate-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px;
  cursor: pointer;
  background: var(--bg-surface);
  transition: all 0.15s ease;
}

.candidate-card:hover {
  background: var(--bg-hover);
  border-color: var(--border-strong);
}

.candidate-card.selected {
  border-color: var(--accent);
  background: var(--bg-active);
}

.cand-top {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-xs);
  margin-bottom: 4px;
}

.cand-source {
  font-weight: 600;
  color: var(--accent);
}

.cand-confidence {
  color: var(--success);
  font-family: var(--font-mono);
}

.cand-title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cand-sub {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.diff-table-box {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  overflow: hidden;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-xs);
}

.diff-table th,
.diff-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle);
}

.diff-table th {
  background: var(--bg-hover);
  color: var(--text-muted);
  font-weight: 600;
}

.field-name {
  color: var(--text-muted);
  font-weight: 500;
}

.val-curr {
  color: var(--text-muted);
}

.val-arrow {
  color: var(--accent);
  text-align: center;
  width: 24px;
}

.val-new {
  color: var(--accent);
  font-weight: 600;
}

.diff-notice {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-top: 8px;
}
</style>
