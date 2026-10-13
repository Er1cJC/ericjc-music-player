# 任务书：歌词系统升级（Folia 风格，方案三）

> 交给 Codex 的任务书。开工前请先读 `docs/CODEX_HANDOFF.md`（环境、启动命令、Windows 坑、既有约定），再读本文件。

## 1. 目标

在不改动后端、数据库、API 契约，也不破坏现有播放器 / 登录 / 收藏 / 歌单 / 播放历史 / 管理后台的前提下，把**前端歌词层**升级为：

1. 解析能力增强：支持普通 LRC 之外的**逐字时间轴**歌词格式；
2. 视觉呈现增强：实现一个参考 Folia 视觉风格、但**完全自研**的歌词舞台（流光逐字高亮）。

## 2. 硬性约束（违反即失败）

### 2.1 许可证红线（最重要，先读三遍）

参考项目 Folia（https://github.com/chthollyphile/folia-major ）采用 **AGPL-3.0**（已核实：其 `package.json` 中 `"license": "AGPL-3.0"`，LICENSE 为 GNU AGPL v3 全文）。

- **禁止**复制、移植、翻译或改写 Folia 的任何源码：包括它的歌词解析器、组件、shader、动画代码、样式、常量表。
- **允许**阅读它的公开仓库/文档，理解其数据模型与交互设计思路，然后**用 Vue + CSS + Canvas 从头实现**。
- 不要给本项目引入任何 AGPL 依赖。
- 如果你写出的一段实现与 Folia 高度雷同，必须能说明是自己独立实现的；拿不准就换一种实现方式。
- 本项目当前**没有开源许可证声明**，默认保留所有权利——这条红线不能碰。

### 2.2 技术栈约束

- Vue 3.5 + Vite 6 + TypeScript 5.7，本机 Node 22.17。**不要引入要求 Node >= 24 的依赖。**
- 不引入 React / framer-motion / three.js / pixi.js / tailwind / zustand——不要为了复刻视觉而更换依赖体系。
- 新增依赖要极克制：能用原生 Canvas / CSS / Web Animations 实现就不要加库。
- 后端、数据库、REST 接口一律不改，`Song.lyricUrl` 单文件设计保留。

### 2.3 不许破坏的既有行为

- 播放器、登录、收藏、歌单、播放历史、管理后台全部照旧。
- 歌词页已实现的良好行为必须保留：当前句高亮、切歌重置、无歌词占位提示、横屏"唱片左/歌词右"且歌词左对齐、竖屏"上唱片/下歌词"、**只有顶部按钮能收起歌词**、歌曲开头第一句在中心线下方待命。
- 之前修过的 bug 不要改回去（见 `docs/CODEX_HANDOFF.md` 第六节）。

## 3. 现状精确坐标（改之前先读这些地方）

`frontend/src/App.vue`：

| 位置 | 内容 |
|---|---|
| L183 | `const lyricLines = ref<{ time: number; text: string }[]>([])` |
| L184-185 | `currentLyricIndex`、`lyricContainerRef` |
| L217 | `watch(currentSong, ...)` → 调 `loadLyrics` |
| L467 | `updateProgress()`：在 `<audio>` 的 `timeupdate` 里调 `updateLyricIndex(a.currentTime)` |
| L468-487 | `updateLyricIndex()`：找当前行 + `scrollTo` 让当前行居中（行高由 `offsetHeight` 动态读取） |
| L489-500 | `loadLyrics()`：`fetch(song.lyricUrl)` → `parseLrc(raw)` |
| L502-513 | `parseLrc()`：只认 `/\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?\]\s*(.*)/` |
| L1347-1355 | 模板：`.detail-lyrics` + `v-for` + `:class="{ active: i === currentLyricIndex }"` |

`frontend/src/styles.css`：

- `.detail-lyrics`：`::before { height: 50% }` 与 `::after { height: calc(50% - 22px) }` 两个占位块，实现"开头待命 + 首尾句都能滚到中心线"；
- `.lyric-line`：**固定 44px 行高**（这是消除切换抖动的原因，active 只变色不变字号）；
- 横屏 `@media (orientation: landscape)` 里歌词区 `text-align: left`。

后端：`Song.lyricUrl` 单文件字段；上传接口 `/api/admin/upload`（`type=lyric`）；静态映射 `/uploads/**`。

**真实歌词样本（必须用它们测试，不要只造理想数据）**：

- `backend/uploads/lyrics/Echoes.lrc` —— 网易云下载格式：**前两行是 JSON 元数据**（形如 `{"t":0,"c":[{"tx":"作词: "},{"tx":"Roger Waters"}]}`），之后才是 `[mm:ss.xxx] 文本`；
- `backend/uploads/lyrics/Goldener Schnitt.lrc`；
- 星降る海的歌词文件（UUID 文件名，见 `songs` 表 `lyric_url`）。

## 4. 要做的四件事（分阶段，每阶段都能独立验证）

### 阶段 1：解析器模块（纯 TS，零 UI 依赖）

新建 `frontend/src/lyrics/`：

- `types.ts` —— 统一数据模型：

```ts
export interface LyricWord { startTime: number; endTime: number; text: string }
export interface LyricLine {
  startTime: number; endTime: number;
  text: string;
  words: LyricWord[] | null;      // 逐字时间轴；无逐字信息时为 null
  translation?: string | null;    // 翻译
  romaji?: string | null;         // 罗马音 / 注音
}
export type LyricFormat = 'lrc' | 'lrc-enhanced' | 'yrc' | 'qrc' | 'krc' | 'ttml' | 'plain';
export interface LyricsDoc { lines: LyricLine[]; format: LyricFormat; hasWordTiming: boolean }
```

- `parser.ts` —— `parseLyrics(raw: string, fileName?: string): LyricsDoc`
  - **按内容特征自动识别格式**，不要只靠扩展名（本项目有 `.lrc` / `.lyric` 两种扩展名）；
  - 本阶段必须完成：普通 LRC、增强 LRC（行内 `<mm:ss.xx>` 逐字）、YRC（逐字 `[start,duration]`）；
  - QRC / KRC / TTML 允许先降级为"仅行级"解析并在 `format` 里如实标注，**不要假装支持**；
  - 容错要求：跳过 JSON 元数据行、`[ti:]/[ar:]/[al:]/[by:]/[offset:]` 标签（其中 `offset` 需要应用到所有时间戳）、空行、纯文本行；时间戳乱序要排序；同一时间戳多行要合理合并或保留；
  - 翻译/罗马音：网易云常见"同一时间戳成对出现"或 translrc 形式；能可靠识别就填，识别不了留 `null`，**不要猜**；
  - `endTime` 推导：某行 `endTime = 下一行 startTime`；最后一行用歌曲时长兜底（拿不到就给一个保守值）。

- **不要为了写单测而引入 vitest/playwright**（项目现在没有测试框架）。自检逻辑写成可在浏览器 console 直接调用的纯函数即可。

### 阶段 2：`LyricStage.vue`（新组件，专职渲染）

新建 `frontend/src/components/LyricStage.vue`：

- props：`lines: LyricLine[]`、`currentTime: number`、`activeIndex: number`、`playing: boolean`；
- 保留既有良好行为：当前句居中、开头待命、固定行高不抖动、无歌词占位；
- **性能红线**：行级变化走 Vue 响应式；**逐字进度不要每帧改响应式数据**——在组件内部用 `requestAnimationFrame` 驱动，通过 CSS 自定义属性（如 `--progress`）或直接 Canvas 绘制体现高亮；暂停时必须 `cancelAnimationFrame`。
- 无障碍：`@media (prefers-reduced-motion: reduce)` 下关闭流光/位移动效，只保留高亮与居中。

### 阶段 3：接入 App.vue + 第一个视觉模式「流光」

- 用 `<LyricStage ...>` 替换模板里 `.detail-lyrics` 那一块；原来 `lyricContainerRef` 承担的居中职责迁进组件（**注意保留 `.detail-lyrics` 的 `::before/::after` 占位思路或等价实现，否则会退回"整屏歌词"的老问题**）。
- 驱动源：现有的 `timeupdate`（约 4Hz）**不够逐字用**。播放时用 `requestAnimationFrame` 读取 `audioElement.value.currentTime`，暂停/切歌时停掉。
- 视觉：当前行发光 + 逐字自左向右的流光推进 + 轻微上浮入场。所有动效参数集中放在组件内一个常量对象里，方便后续调参。
- 保持横竖屏布局规则（横屏左对齐）。

### 阶段 4（可选，做不完不影响验收）

- 增加第二种视觉模式（例如"呼吸/粒子"），用 prop 或一个简单下拉切换即可；**不要为此引入状态管理库**。

## 5. 验收标准

1. `cd frontend && npm run build` 通过（含 `vue-tsc --noEmit` 类型检查）。
2. `npm run dev` 后打开 http://localhost:5173 ，点开 Echoes：
   - 播放时当前句始终居中；有逐字时间轴时逐字高亮推进，否则逐行高亮；
   - 歌曲开头第一句停在中心线下方待命，播到第一句才上移；
   - 行切换无抖动（行高稳定）；
   - 快进、后退、切歌、暂停均正确，暂停后没有残留的 rAF 循环；
   - 横屏：唱片左 / 歌词右且左对齐；竖屏：上唱片 / 下歌词。
3. Echoes.lrc（含 JSON 元数据行）解析不崩溃、不产生多余歌词行。
4. 无歌词歌曲显示"暂无歌词 / 纯音乐，请欣赏"。
5. 浏览器 console 无报错、无逐帧日志输出。
6. `git status` 只包含预期的前端文件；**后端一行都没改**。

## 6. 开工前先做

1. 读 `docs/CODEX_HANDOFF.md`（环境路径、启动命令、Windows 的 PowerShell 坑）。
2. `git pull --ff-only`。
3. 启动前后端，确认现状可跑（`curl http://localhost:18080/api/health`、打开 5173）。
4. 打开 `backend/uploads/lyrics/Echoes.lrc` 看真实内容——它决定了解析器要处理哪些怪格式。

## 7. 交付与协作约定

- 改完先本地验证（build + 页面实测），通过后**自动 commit（提交说明用中文）并 push**，不需要停下来等确认。
- 测试产生的数据（账号、歌曲、上传文件）要清理干净。
- 汇报内容：改了哪些文件、支持了哪些歌词格式（哪些是降级支持）、逐字高亮实测结论、遗留问题。

## 8. 允许参考的公开资料（只用于理解设计，不可复制代码）

- Folia 仓库：https://github.com/chthollyphile/folia-major
- Folia 歌词管线说明（第三方文档站）：https://deepwiki.com/chthollyphile/folia-major/5-lyrics-pipeline
- 增强歌词格式约定：网易云 YRC、QQ 音乐 QRC、酷狗 KRC、Apple Music TTML（公开格式说明）
