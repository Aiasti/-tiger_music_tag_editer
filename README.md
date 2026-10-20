# Tiger Music Tag Editor

Windows 桌面端的批量音频元数据（标签）编辑器。把一批本地音频文件导入为「工作区」，集中查看与修改标签，从在线库匹配元数据候选，确认后写回文件标签，并按模板重命名与归档。

## 下载与安装

- **安装包**：[Tiger Music Tag Editor 1.0.0](https://github.com/Aiasti/-tiger_music_tag_editer/releases/tag/v1.0.0)（Windows x64，`TigerMusicTagEditor-1.0.0-setup.exe`，内置 Java 运行时，安装即用）
- **代码仓库**：[GitHub（主仓库）](https://github.com/Aiasti/-tiger_music_tag_editer) ｜ [Gitee（镜像）](https://gitee.com/aisati/tiger_music_tag_editer)
- 安装包未做代码签名，首次运行如出现 Windows SmartScreen 提示，请选择「仍要运行」。

## 功能特性

- **安装即用**：安装包内置 Java 运行时、后端服务与 fpcalc，用户无需安装 Java 或其他依赖。
- **文件导入与工作区**：导入文件或文件夹，读取并展示现有标签；新导入文件按专辑 → 碟号 → 音轨号排序；建立「首次元数据快照」作为提交前回滚目标。
- **标签查看**：以表格呈现每首曲目的字段值，多碟专辑按碟号分组显示。
- **手动排序与批量编号**：拖动调整顺序；按选定范围从 1 连续编号，并可按碟号分组分别编号。
- **批量 / 单独编辑**：单元格直接编辑，或对多首曲目批量设置同一字段。
- **在线元数据匹配**：基于在线数据源检索曲目元数据；**只处理基础元数据，不读取、不修改歌词**。
- **在线封面匹配**：独立「封面匹配」入口与弹窗，按曲目信息检索封面；批量应用时每首曲目使用各自候选，只写入封面。
- **封面与歌词**：单张封面（获取/替换/嵌入）；歌词支持在线检索并写入内嵌歌词标签。**歌词搜索只修改歌词，不修改基础元数据**。
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


## 项目结构

```
backend/    Spring Boot 3.5.x + Java 21 后端（领域端口 + jaudiotagger 适配器；导入、写回、重命名/归档、在线匹配）
desktop/    Electron + Vue 3 + TypeScript + Vite 桌面端
  prototype/      UI 静态原型（单文件 HTML，含双主题与全部交互）
  src/main/       Electron 主进程
  src/preload/    受限 preload 桥接
  src/renderer/   Vue 3 渲染层（组件、状态机、双主题 Token）
```

## 在线数据源

| 名称 | 用途 | 主体 | 官方地址 |
|---|---|---|---|
| MusicBrainz | 元数据 | MetaBrainz Foundation | https://musicbrainz.org/doc/MusicBrainz_API |
| AcoustID | 音频指纹 | AcoustID 开源项目 | https://acoustid.org/webservice |
| LRCLIB | 歌词 | LRCLIB（tranxuanthang 维护） | https://lrclib.net/docs |
| iTunes Search API | 封面 | Apple Inc. | https://performance-partners.apple.com/search-api |
| LrcAPI | 歌词 / 封面 | HisAtri（LrcAPI 开源项目） | https://github.com/HisAtri/LrcApi |


## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
