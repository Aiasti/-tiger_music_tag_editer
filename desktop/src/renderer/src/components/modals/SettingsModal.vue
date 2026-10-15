<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'
import { useTheme } from '../../composables/useTheme'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { theme, toggleTheme } = useTheme()

const activeTab = ref<'lyrics' | 'metadata' | 'general'>('lyrics')

// 可选歌词源配置 (满足用户针对网易云、QQ音乐、Apple Music、Last.fm 等可选源的需求)
const lyricsSources = ref([
  { id: 'netease', name: '网易云音乐 (Netease Music)', enabled: true, priority: 1, desc: '支持精准时间轴 LRC，华语及流行曲库丰富' },
  { id: 'qq', name: 'QQ音乐 (Tencent QQ Music)', enabled: true, priority: 2, desc: '支持高精逐字 LRC 与多语种翻译歌词' },
  { id: 'applemusic', name: 'Apple Music (Musixmatch 映射)', enabled: true, priority: 3, desc: '欧美流行与原声带专辑歌词覆盖完备' },
  { id: 'lrcdb', name: 'LRCDB (社区开源歌词库)', enabled: true, priority: 4, desc: '无 DRM 限制的开放时间轴歌词库' },
  { id: 'kugou', name: '酷狗音乐 (Kugou)', enabled: false, priority: 5, desc: '逐字歌词资源与小众曲目补充' },
  { id: 'lastfm', name: 'Last.fm 元数据映射', enabled: false, priority: 6, desc: '辅助校验曲目轨道信息与标签分类' }
])

// 外部播放器与通用配置
const playerPath = ref('C:\\Program Files\\foobar2000\\foobar2000.exe')
const retainTimestamp = ref(true)
const musicBrainzRateLimit = ref(1.0)
const acoustidApiKey = ref('demo-free-acoustid-key')

const handleSave = () => {
  emit('close')
}
</script>

<template>
  <ModalContainer title="⚙️ 偏好设置 (Settings)" max-width="720px" @close="emit('close')">
    <div class="settings-modal-body">
      <!-- 选项卡导航 -->
      <div class="settings-nav">
        <button
          class="nav-item"
          :class="{ active: activeTab === 'lyrics' }"
          @click="activeTab = 'lyrics'"
        >
          🎵 歌词源配置
        </button>
        <button
          class="nav-item"
          :class="{ active: activeTab === 'metadata' }"
          @click="activeTab = 'metadata'"
        >
          🌐 元数据与指纹
        </button>
        <button
          class="nav-item"
          :class="{ active: activeTab === 'general' }"
          @click="activeTab = 'general'"
        >
          🎨 常规与播放器
        </button>
      </div>

      <!-- 选项卡内容 -->
      <div class="settings-content">
        <!-- 歌词源设置 -->
        <div v-if="activeTab === 'lyrics'" class="tab-pane">
          <div class="pane-headline">
            <strong>多源在线歌词提供商 (BIZ-012)</strong>
            <p>启用或禁用在线歌词检索候选源。检索时将根据启用的源并发查询并汇集为统一候选卡片。</p>
          </div>

          <div class="sources-list">
            <div
              v-for="source in lyricsSources"
              :key="source.id"
              class="source-item"
              :class="{ enabled: source.enabled }"
            >
              <label class="source-checkbox">
                <input v-model="source.enabled" type="checkbox" />
                <span class="source-name">{{ source.name }}</span>
              </label>
              <span class="source-desc">{{ source.desc }}</span>
              <span class="priority-badge">优先度 #{{ source.priority }}</span>
            </div>
          </div>
        </div>

        <!-- 元数据与指纹设置 -->
        <div v-else-if="activeTab === 'metadata'" class="tab-pane">
          <div class="pane-headline">
            <strong>在线元数据与指纹提供商 (FR-2 / FR-3)</strong>
            <p>配置 MusicBrainz 权威库与 AcoustID 声学指纹检索参数。</p>
          </div>

          <div class="form-row">
            <label>MusicBrainz API 限流保护 (req/s)</label>
            <input v-model.number="musicBrainzRateLimit" type="number" step="0.1" min="0.5" max="2.0" class="input sm" />
            <span class="field-hint">官方建议 ≤ 1.0 req/s 以免触发 503 限流</span>
          </div>

          <div class="form-row">
            <label>AcoustID User API Key</label>
            <input v-model="acoustidApiKey" type="text" class="input" />
            <span class="field-hint">用于 Chromaprint 指纹上传与反查专辑录音</span>
          </div>
        </div>

        <!-- 常规与播放器设置 -->
        <div v-else class="tab-pane">
          <div class="pane-headline">
            <strong>常规与外部工具 (FR-9)</strong>
            <p>界面主题以及与本地外部播放器（如 foobar2000 / PotPlayer）的集成联动。</p>
          </div>

          <div class="form-row">
            <label>当前界面主题</label>
            <button class="btn" @click="toggleTheme">
              {{ theme === 'dark' ? '🌙 当前为 One Dark (点击切换浅色)' : '☀️ 当前为 One Light (点击切换深色)' }}
            </button>
          </div>

          <div class="form-row">
            <label>外部播放器可执行文件路径</label>
            <input v-model="playerPath" type="text" class="input" />
            <span class="field-hint">双击或快捷键预览试听时将调用此播放器</span>
          </div>

          <div class="form-row checkbox-row">
            <label class="checkbox-label">
              <input v-model="retainTimestamp" type="checkbox" />
              <span>写入文件后保留原始创建与修改时间戳 (mtime)</span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleSave">保存配置</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.settings-modal-body {
  display: flex;
  gap: var(--space-md);
  min-height: 360px;
}

.settings-nav {
  display: flex;
  flex-direction: column;
  width: 180px;
  border-right: 1px solid var(--border-subtle);
  padding-right: var(--space-sm);
  gap: 4px;
}

.nav-item {
  background: none;
  border: none;
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  text-align: left;
  font-size: var(--font-sm);
  color: var(--text-muted);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s ease;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

.nav-item.active {
  background: var(--bg-active);
  color: var(--accent);
  font-weight: 600;
}

.settings-content {
  flex: 1;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.pane-headline strong {
  font-size: var(--font-sm);
  color: var(--text-main);
}

.pane-headline p {
  margin: 4px 0 0 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: 1.4;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.source-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  transition: all 0.15s ease;
}

.source-item.enabled {
  border-color: var(--accent);
  background: var(--bg-hover);
}

.source-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.source-name {
  font-size: var(--font-sm);
  font-weight: 500;
}

.source-desc {
  font-size: var(--font-xs);
  color: var(--text-muted);
  flex: 1;
  margin: 0 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.priority-badge {
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-muted);
  background: var(--bg-base);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-row label {
  font-size: var(--font-sm);
  font-weight: 600;
}

.form-row .input.sm {
  width: 120px;
}

.field-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.checkbox-row {
  margin-top: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-sm);
  cursor: pointer;
}
</style>
