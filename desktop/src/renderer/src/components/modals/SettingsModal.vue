<script setup lang="ts">
import { ref } from 'vue'
import ModalContainer from './ModalContainer.vue'

const emit = defineEmits<{
  (e: 'close'): void
}>()

/** 标签卡顺序与标题对齐 S0.5 原型（BIZ-017 反馈：改为标签卡形式）。 */
type SettingsTab = 'player' | 'online' | 'lyrics'
const activeTab = ref<SettingsTab>('player')

// Tab 1：外部播放器试听 (FR-9)
const playerPath = ref('')
const playerArgs = ref('"%filepath%"')

// Tab 2：在线服务与限流 (FR-5 / BIZ-009)
const musicBrainzUserAgent = ref('TigerMusicTagEditor/1.0.0 ( contact@example.com )')
const acoustidApiKey = ref('')

// Tab 3：可选歌词源配置 (FR-9 / BIZ-012)
const lyricsSources = ref([
  { id: 'netease', name: '网易云音乐 (Netease Music)', enabled: true, priority: 1, desc: '支持精准时间轴 LRC，华语及流行曲库丰富' },
  { id: 'qq', name: 'QQ 音乐 (Tencent QQ Music)', enabled: true, priority: 2, desc: '支持逐字 LRC 与多语种翻译歌词' },
  { id: 'applemusic', name: 'Apple Music', enabled: true, priority: 3, desc: '欧美流行与原声带专辑歌词覆盖完备' },
  { id: 'lastfm', name: 'Last.fm 元数据映射', enabled: false, priority: 4, desc: '辅助校验曲目轨道信息与标签分类' }
])

const handleSave = () => {
  emit('close')
}
</script>

<template>
  <ModalContainer title="⚙️ 系统首选项与配置" max-width="680px" @close="emit('close')">
    <div class="settings-body">
      <!-- 标签卡导航（与原型一致） -->
      <div class="settings-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'player' }"
          @click="activeTab = 'player'"
        >
          🎧 外部播放器试听 (FR-9)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'online' }"
          @click="activeTab = 'online'"
        >
          🌐 在线服务与限流 (FR-5 / BIZ-009)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'lyrics' }"
          @click="activeTab = 'lyrics'"
        >
          🎵 可选歌词源配置 (FR-9 / BIZ-012)
        </button>
      </div>

      <!-- Tab 1：外部播放器 -->
      <div v-if="activeTab === 'player'" class="tab-pane">
        <div class="form-group">
          <label class="form-label">外部音频播放器可执行程序路径（如 foobar2000、VLC 等）：</label>
          <div class="inline-row">
            <input v-model="playerPath" type="text" class="form-input" placeholder="C:\Program Files\foobar2000\foobar2000.exe" />
            <button class="btn" disabled title="文件选择依赖主进程对话框，随试听功能在 S5 交付">浏览…</button>
          </div>
          <span class="form-hint">
            应用不内置播放器核心；触发试听时通过系统进程拉起该播放器打开当前选中曲目。
          </span>
        </div>

        <div class="form-group">
          <label class="form-label">命令行启动参数模板：</label>
          <input v-model="playerArgs" type="text" class="form-input" />
          <span class="form-hint">使用 %filepath% 代表当前曲目路径。</span>
        </div>

        <div>
          <button class="btn" disabled title="试听功能尚未实现，计划在 S5 交付">▶ 运行试听测试</button>
        </div>
      </div>

      <!-- Tab 2：在线服务与限流 -->
      <div v-else-if="activeTab === 'online'" class="tab-pane">
        <div class="form-group">
          <label class="form-label">MusicBrainz User-Agent 客户端标识（BIZ-009 要求可识别应用与联系方式）：</label>
          <input v-model="musicBrainzUserAgent" type="text" class="form-input" />
        </div>

        <div class="form-group">
          <label class="form-label">AcoustID 应用 API Key（用于音频指纹查询）：</label>
          <input v-model="acoustidApiKey" type="text" class="form-input" placeholder="注册 AcoustID 应用后获取" />
        </div>

        <div class="info-block">
          ⏱️ <strong>强制客户端节流策略（BIZ-009）</strong>
          <ul>
            <li>MusicBrainz：≤ 1.0 次请求/秒，超额将返回 429/503 并可能封禁。</li>
            <li>AcoustID：≤ 3.0 次请求/秒。</li>
            <li>遇到 429/503 时按 Retry-After 指数退避重试。</li>
          </ul>
          <span class="form-hint">以上为服务端强制约束，不可调高。</span>
        </div>
      </div>

      <!-- Tab 3：可选歌词源配置 -->
      <div v-else class="tab-pane">
        <span class="form-hint">
          调整批量在线检索时的歌词源启用状态与优先级；靠前的源优先检索，命中后停止。
        </span>
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
    </div>

    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn primary" @click="handleSave">保存配置</button>
    </template>
  </ModalContainer>
</template>

<style scoped>
.settings-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 300px;
}

/* 标签卡导航：横排 + 选中下划线（对齐原型） */
.settings-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border);
}
.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  padding: 8px 10px;
  font-family: inherit;
  font-size: var(--font-sm);
  color: var(--text-muted);
  cursor: pointer;
}
.tab-btn:hover {
  color: var(--text-bright);
}
.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-bright);
}
.form-input {
  height: 28px;
  padding: 0 8px;
  background: var(--surface);
  color: var(--text);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: var(--font-sm);
  outline: none;
}
.form-input:focus {
  border-color: var(--accent);
}
.form-hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: 1.5;
}
.inline-row {
  display: flex;
  gap: 8px;
}
.inline-row .form-input {
  flex: 1;
}

.info-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--info-dim);
  font-size: var(--font-sm);
  color: var(--text);
}
.info-block ul {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.6;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.source-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}
.source-item.enabled {
  border-color: var(--accent);
}
.source-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.source-name {
  font-size: var(--font-sm);
  font-weight: 500;
  color: var(--text-bright);
}
.source-desc {
  flex: 1;
  min-width: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.priority-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-muted);
  background: var(--surface-2);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
</style>
