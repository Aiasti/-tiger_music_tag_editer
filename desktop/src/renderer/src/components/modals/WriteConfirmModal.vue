<script setup lang="ts">
import { computed, ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useWorkspace } from '../../composables/useWorkspace'
import { trackBaseName } from '../../utils/trackText'
import type { EditableTagField, TrackItem, WriteOutcomeStatus } from '../../types'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { tracks, pendingCovers, commitAllWrites, lastWriteOutcomes, writing } = useWorkspace()

/** 12 个可写回元数据字段的中文标签（与表格列一致）。 */
const FIELD_LABELS: Record<EditableTagField, string> = {
  title: '标题',
  artist: '艺术家',
  album: '专辑',
  albumArtist: '专辑艺术家',
  trackNo: '音轨',
  discNo: '碟号',
  year: '年份',
  genre: '流派',
  composer: '作曲',
  lyricist: '作词',
  arranger: '编曲',
  comment: '注释'
}

const OUTCOME_LABELS: Record<WriteOutcomeStatus, string> = {
  written: '已写回',
  stale: '未执行（文件已被外部修改）',
  blocked: '未执行（预检未通过）',
  'failed-restored': '失败（已恢复原状）',
  'failed-manual': '失败（未恢复，需人工处理）'
}

/** 是否已发起写回；发起后切换到结果视图（BIZ-028 结果面板）。 */
const requested = ref(false)

const changedMetadata = (track: TrackItem): EditableTagField[] =>
  (Object.keys(FIELD_LABELS) as EditableTagField[]).filter(
    (field) => (track.tags[field] ?? '') !== (track.lastWritten[field] ?? '')
  )

const hasLyricsChange = (track: TrackItem): boolean =>
  (track.tags.lyrics ?? '') !== (track.lastWritten.lyrics ?? '')

const isPending = (track: TrackItem): boolean =>
  pendingCovers.value[track.id] !== undefined ||
  changedMetadata(track).length > 0 ||
  hasLyricsChange(track)

const pendingTracks = computed(() =>
  tracks.value.filter((t) => t.status !== 'failed' && isPending(t))
)

const results = computed(() => lastWriteOutcomes.value)

const summary = computed(() => {
  const counts: Record<WriteOutcomeStatus, number> = {
    written: 0,
    stale: 0,
    blocked: 0,
    'failed-restored': 0,
    'failed-manual': 0
  }
  results.value.forEach((outcome) => {
    counts[outcome.outcome] += 1
  })
  return counts
})

const trackOf = (id: number): TrackItem | undefined => tracks.value.find((t) => t.id === id)

const handleCommit = async () => {
  requested.value = true
  await commitAllWrites()
}
</script>

<template>
  <ModalContainer title="💾 确认写入文件 (FR-7 / 持久化落盘)" max-width="760px" @close="emit('close')">
    <!-- 写回前：待写入清单（真实差异） -->
    <div v-if="!requested" class="write-modal-body">
      <div class="banner warning">
        <span>⚠️</span>
        <div class="banner-text">
          <strong>即将把标签写入磁盘文件</strong>
          <p>
            共有 <strong>{{ pendingTracks.length }}</strong> 首曲目包含未写回的修改。确认后后端将逐文件执行
            「预检 → 同目录备份 → 写入并校验 → 失败补偿 → 收尾」，单个文件失败不影响其余文件。
          </p>
        </div>
      </div>

      <div class="pending-list-section">
        <div class="section-title">待写入文件列表 ({{ pendingTracks.length }} 项)</div>
        <div class="pending-table-wrapper">
          <table class="pending-table">
            <thead>
              <tr>
                <th style="width: 40px;">#</th>
                <th>文件名</th>
                <th>变更字段</th>
                <th style="width: 80px;">格式</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(track, idx) in pendingTracks" :key="track.id">
                <td>{{ idx + 1 }}</td>
                <td class="file-cell" :title="track.filePath">{{ trackBaseName(track) }}</td>
                <td class="changes-cell">
                  <span v-for="field in changedMetadata(track)" :key="field" class="change-tag">
                    {{ FIELD_LABELS[field] }}
                  </span>
                  <span v-if="pendingCovers[track.id] !== undefined" class="change-tag">
                    封面
                  </span>
                  <span v-if="hasLyricsChange(track)" class="change-tag media">歌词（暂不写回）</span>
                </td>
                <td><span class="format-badge">{{ track.ext.toUpperCase() }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="pendingTracks.length === 0" class="empty-hint">没有待写回的修改。</p>
      </div>

      <div class="safety-features">
        <div class="feat-item">🛡️ <strong>失败隔离：</strong>逐文件独立执行，写回失败会自动恢复该文件为执行前状态</div>
        <div class="feat-item">📦 <strong>恢复材料：</strong>写回前在源文件同目录创建整文件备份，成功后自动清理</div>
        <div class="feat-item">⏸️ <strong>本轮范围：</strong>只写回 12 个元数据字段与封面；歌词写回待 DEV-005（歌词帧映射）</div>
      </div>
    </div>

    <!-- 写回后：结果面板 -->
    <div v-else class="write-modal-body">
      <div class="banner" :class="summary['failed-manual'] + summary['failed-restored'] > 0 ? 'warning' : 'ok'">
        <span>{{ summary['failed-manual'] + summary['failed-restored'] > 0 ? '⚠️' : '✅' }}</span>
        <div class="banner-text">
          <strong>写回完成</strong>
          <p>
            已写回 <strong>{{ summary.written }}</strong> 首；
            未执行 <strong>{{ summary.stale + summary.blocked }}</strong> 首；
            失败 <strong>{{ summary['failed-restored'] + summary['failed-manual'] }}</strong> 首
            （其中需人工处理 <strong>{{ summary['failed-manual'] }}</strong> 首）。
          </p>
        </div>
      </div>

      <div class="pending-list-section">
        <div class="section-title">逐文件结果 ({{ results.length }} 项)</div>
        <div class="pending-table-wrapper">
          <table class="pending-table">
            <thead>
              <tr>
                <th style="width: 40px;">#</th>
                <th>文件名</th>
                <th style="width: 210px;">结果</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(outcome, idx) in results" :key="outcome.id">
                <td>{{ idx + 1 }}</td>
                <td class="file-cell" :title="outcome.filePath ?? ''">
                  {{ trackOf(outcome.id) ? trackBaseName(trackOf(outcome.id)!) : outcome.id }}
                </td>
                <td>
                  <span class="outcome-badge" :class="'outcome-' + outcome.outcome">
                    {{ OUTCOME_LABELS[outcome.outcome] }}
                  </span>
                </td>
                <td class="reason-cell">{{ outcome.detail || outcome.reason || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="summary.stale > 0" class="empty-hint">
          被外部修改的文件未被写入。可关闭本窗口后重新导入或核对文件，再重试写回。
        </p>
        <p v-if="summary['failed-manual'] > 0" class="empty-hint failed">
          存在未恢复的失败项，请按说明人工核对文件后决定恢复、重试或移出工作区。
        </p>
      </div>
    </div>

    <template #footer>
      <template v-if="!requested">
        <button class="btn" @click="emit('close')">暂缓写入</button>
        <button class="btn primary" :disabled="writing || pendingTracks.length === 0" @click="handleCommit">
          {{ writing ? '正在写入…' : `立即写回 (${pendingTracks.length} 文件)` }}
        </button>
      </template>
      <template v-else>
        <button class="btn primary" @click="emit('close')">关闭</button>
      </template>
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

.banner.ok {
  background: var(--success-dim, var(--bg-hover));
  border: 1px solid var(--success, var(--border-strong));
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
  max-height: 300px;
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
  vertical-align: top;
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
  max-width: 220px;
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

.change-tag.media {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-dim);
}

.format-badge {
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-muted);
}

.reason-cell {
  color: var(--text-muted);
  line-height: 1.5;
}

.outcome-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  font-size: 10.5px;
  border: 1px solid var(--border-subtle);
  white-space: nowrap;
}

.outcome-written {
  color: var(--success, var(--text-main));
  border-color: var(--success, var(--border-strong));
}

.outcome-stale,
.outcome-blocked {
  color: var(--warning);
  border-color: var(--warning);
}

.outcome-failed-restored,
.outcome-failed-manual {
  color: var(--state-failed);
  border-color: var(--state-failed);
}

.empty-hint {
  margin: 8px 0 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.empty-hint.failed {
  color: var(--state-failed);
}

.safety-features {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
