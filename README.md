# Tiger Music Tag Editor

Windows 桌面端的批量音频元数据（标签）编辑器。把一批本地音频文件导入为「工作区」，集中查看与修改标签，从在线库匹配元数据候选，确认后写回文件标签，并按模板重命名与归档。

> **当前版本**：`0.5.1`（S5 验收后增强；minor 对应 S 阶段，patch 对应验收后增强与修复）。
>
> **当前状态**：S0~S5 已完成并通过真机验收；Java 运行时与安装包延后 S6。在线数据源包含 MusicBrainz、AcoustID、LRCLIB、iTunes（US/JP/HK）与 lrcapi；支持歌词读写与独立封面匹配。项目文档在本地 `docs/` 目录维护，不随本仓库发布。

## 功能特性

- **文件导入与工作区**：导入文件或文件夹，读取并展示现有标签；新导入文件按专辑 → 碟号 → 音轨号排序；建立「首次元数据快照」作为提交前回滚目标。
- **标签查看**：以表格呈现每首曲目的字段值，多碟专辑按碟号分组显示。
- **手动排序与批量编号**：拖动调整顺序；按选定范围从 1 连续编号，并可按碟号分组分别编号。
- **批量 / 单独编辑**：单元格直接编辑，或对多首曲目批量设置同一字段。
- **在线元数据匹配**：接入 MusicBrainz + AcoustID，支持文本检索与本地 Chromaprint 指纹；按单曲串行检索、显示进度并可停止；AcoustID 不可用时自动降级为 MusicBrainz 文本检索。**只处理基础元数据，不读取、不修改歌词**。
- **在线封面匹配**：独立「封面匹配」入口与弹窗，通过 iTunes Search API（US/JP/HK）与 lrcapi 检索封面；每源最多 3 条、合并去重后最多 5 条，图片统一按 600×600 请求；批量应用时每首曲目使用各自候选，只写入封面。
- **封面与歌词**：单张封面（获取/替换/嵌入）；歌词同时支持 LRCLIB 与 lrcapi 在线检索（LRC/纯文本），每源最多 3 条、合并去重后最多 6 条，并写入内嵌歌词标签。**歌词搜索只修改歌词，不修改基础元数据**。
- **重命名与归档**：写前预览，自动处理非法字符、重名冲突与路径长度（首版提供可编辑变量模板）。
- **提交前回滚与确认写回**：写回前预览全部变更，确认后写入标签（含歌词）并重命名归档；失败逐文件隔离与恢复。
- **外部播放器试听（S5）**：不内置播放器，调用用户设置的外部播放器（如 foobar2000）打开当前曲目；**多选时把全部选中曲目交给播放器**，默认写入固定 `tiger trial.m3u8` 试听列表（每次覆盖）；播放器路径与参数模板在设置中持久化。
- **界面**：单窗口工作区（工具栏 + 曲目表格 + 详情/媒体区 + 状态栏），支持 One Dark / One Light 双主题；11 个交互模态（导入跳过项、批量编号、批量编辑、在线匹配、歌词搜索、封面匹配、编辑封面、重命名、归档、写回确认、首选项设置）。

## 支持的音频格式与标签体系

| 格式 | 标签体系 |
|---|---|
| FLAC | Vorbis Comment |
| ALAC | iTunes 风格 |
| M4A | iTunes 风格 |
| AAC | iTunes 风格 |
| MP3 | ID3 |
| OGG | Vorbis Comment |
| WAV | RIFF / ID3 |

## 技术栈

- **后端**：Java 21 LTS + Spring Boot 3.5.x + Maven
- **前端**：Vue 3 + TypeScript + Vite
- **桌面壳**：Electron
- **通信**：本机 HTTP/JSON
- **存储**：首版不引入 SQL 数据库，工作区快照、编辑态与会话结果保存在 Java 服务内存中（会话级，不跨会话持久化）

> 已完成 S0 验证：jaudiotagger 3.0.1 对 FLAC / ALAC / M4A / AAC / MP3 / OGG / WAV 的标签读写通过；在线源约束见本地文档。

## 项目结构

```
backend/    Spring Boot 3.5.x + Java 21 后端（领域端口 + jaudiotagger 适配器；导入、写回、重命名/归档、在线匹配）
desktop/    Electron + Vue 3 + TypeScript + Vite 桌面端
  prototype/      UI 静态原型（单文件 HTML，含双主题与全部交互）
  src/main/       Electron 主进程
  src/preload/    受限 preload 桥接
  src/renderer/   Vue 3 渲染层（组件、状态机、双主题 Token）
```

## 在线匹配配置

后端已内置 AcoustID 应用 API Key（仅用于 Tiger 项目）；其余在线配置可通过环境变量覆盖，也可在桌面端「设置 → 首选项」中填写播放器与 fpcalc 路径（自定义 Key 加密存储于 Electron userData）：

| 变量 | 说明 |
|---|---|
| `TIGER_ACOUSTID_KEY` | 覆盖内置默认 AcoustID Key；置空且无其它 Key 时自动降级为 MusicBrainz 文本检索 |
| `TIGER_FPCALC_PATH` | Chromaprint `fpcalc` 可执行文件路径；未配置时按 PATH 查找 |
| `TIGER_MUSICBRAINZ_USER_AGENT` | MusicBrainz User-Agent（默认 `TigerMusicTagEditor/版本号 (master98@live.cn)`，版本由构建注入） |
| `TIGER_LRCLIB_USER_AGENT` | LRCLIB 客户端标识（默认与 MusicBrainz 相同，版本由构建注入） |
| `LRCLIB_APP_ID` | 覆盖内置的 LRCLIB 客户端 App-Id；内置 ID 下歌词检索按 2 请求/秒运行 |

> 内置 Key 仅适用于 Tiger 项目；如需私有部署或改用自有 Key，请通过 `TIGER_ACOUSTID_KEY` 覆盖。

开发期启动示例：

```powershell
$env:TIGER_ACOUSTID_KEY = "<your-key>"
$env:TIGER_FPCALC_PATH = "C:\path\to\fpcalc.exe"
cd backend
.\mvnw.cmd spring-boot:run
```

## 文档

需求、技术方案、业务决策、缺陷与进度文档在本地 `docs/` 目录维护，Agent 规则见本地 `AGENTS.md`；这些文件已在 `.gitignore` 中忽略，不随本仓库发布。

## 当前进度

- **S0~S3 已完成**：技术验证、UI 基线、导入与展示、编辑/回滚/写回、重命名/归档。
- **S4 已完成并验收（主体 `83ae05d`，含缺陷修复的最新提交 `96d45cf`）**：MusicBrainz 文本检索、AcoustID 指纹、LRCLIB 歌词检索、歌词读写落盘、独立封面匹配、批量逐曲候选应用。
- **S5 已完成并验收**：外部播放器试听、首选项持久化与运行时下发、fpcalc 交付准备；设置弹窗、主题与原生标题栏（Windows 11 分窗）已调整；Java 运行时与 NSIS 安装包延后 S6。
- **0.5.1（S5 验收后增强）**：封面扩展为 iTunes US/JP/HK + lrcapi，歌词扩展为 LRCLIB + lrcapi；每源最多 3 条，封面/歌词合并后分别最多 5/6 条，封面统一按 600×600 请求；修复 lrcapi http 封面被图片代理拒绝的问题。

进度详见本地 `docs/PROG.md`。

## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
