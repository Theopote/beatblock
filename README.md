# BeatBlock

BeatBlock 是一个运行在 Minecraft 中的**音乐可视化创作工具**：在世界里选中方块、编排时间轴事件、配合相机与粒子，制作可被录屏或存档回放的演出。音频分析的作用是辅助创作者快速找到节拍点、生成初稿；**最终呈现的每一个方块变化都是时间轴上的、可编辑、可重放的确定性数据**，不依赖运行时重新分析音频。

> BeatBlock 面向**被观看的演出**（录屏、存档回放），不是玩家实时按键的节奏游戏。时间轴是权威数据源；音频播放时钟只对齐预览进度，不是事件来源。

---

## 目录

- [功能概览](#功能概览)
- [环境要求](#环境要求)
- [构建与运行](#构建与运行)
- [快速上手](#快速上手)
- [编辑器界面](#编辑器界面)
- [建造图层（Build Layer）](#建造图层build-layer)
- [音频分析与 Smart Auto Map](#音频分析与-smart-auto-map)
- [工程文件（.osc）](#工程文件osc)
- [中心模型（三层单向流）](#中心模型三层单向流)
- [项目结构](#项目结构)
- [已知限制](#已知限制)
- [相关文档](#相关文档)
- [许可证](#许可证)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| **ImGui 时间轴编辑器** | 多轨道编辑：动画、建造、摄像机、音频参考轨、标记等；支持撤销/重做、片段拖拽、吸附 |
| **方块选区工具** | 点击、框选、套索、球/立方笔刷、连通魔棒、平面切片、整列等；已归属图层的方块不可重复选入 |
| **建造图层** | 将选区打包为图层，可隐藏/显示、重命名、拖入「建造还原」轨，与 BUILD 序列联动 |
| **方块动画引擎** | 统一的影响维度模型（存在性 / 变换 / 外观 / VFX）；BUILD、BURST、STEP 等派发模式 |
| **摄像机编排** | 关键帧、路径预览、播放时接管相机 |
| **音频参考轨** | Python 分析 BPM、节拍点、段落、频段能量；导入后写入时间轴参考数据，可手改 |
| **Smart Auto Map** | 根据音乐结构自动生成动画、镜头与粒子初稿（编辑时一次性写入，非运行时生成） |
| **工程持久化** | `.osc` 项目文件保存时间线、音频路径、建造图层等 |

三种典型演出（建筑从无到有、跑酷敲击、镜头跟随下落）是同一抽象的不同参数组合：**舞台对象 + 时刻动作 + 相机运动**。详见 [架构文档](docs/architecture.md)。

---

## 环境要求

### 必需

| 组件 | 版本 / 说明 |
|------|-------------|
| **Minecraft** | 1.21.11 |
| **Fabric Loader** | ≥ 0.18.4 |
| **Fabric API** | 与 1.21.11 对应版本（见 `gradle.properties`） |
| **Java** | 21（构建与运行） |

### 推荐（音频与格式转换）

| 组件 | 用途 |
|------|------|
| **Python 3.10–3.12** | 运行内置 `analyze.py` 做 BPM / 节拍 / 段落分析 |
| **pip 依赖** | 见下方 [Python 分析与 Demucs 分轨](#python-分析与-demucs-分轨) |
| **ffmpeg** | MP3 等非 WAV 格式转换与解码兜底 |

**ffmpeg 配置方式（任选其一）：**

- 将 `ffmpeg.exe`（或 Linux/macOS 下的 `ffmpeg`）放到 Minecraft **游戏目录**
- 在 `config/beatblock/ffmpeg_path.txt` 中写入 ffmpeg 可执行文件的**完整路径**
- 或将 ffmpeg 加入系统 `PATH`

---

### Python 分析与 Demucs 分轨

模组启动时会将分析脚本解压到 **`config/beatblock/analyzer/`**（源文件在 `src/main/resources/beatblock/analyzer/`，由 `AnalyzerInstaller` 提取）。分析任务通过 `ProcessBuilder` 调用该目录下的 `analyze.py`。

#### 配置文件（可选）

| 文件 | 作用 |
|------|------|
| `config/beatblock/python_path.txt` | 指定 Python 可执行文件完整路径（一行）；留空则自动探测 `python` / `python3` |
| `config/beatblock/ffmpeg_path.txt` | 指定 ffmpeg 路径 |
| `config/beatblock/analyzer/requirements.txt` | 基础分析依赖（首次运行后从 jar 解压，可手动 `pip install -r`） |
| `config/beatblock/analyzer/requirements-demucs.txt` | **可选** Demucs 分轨依赖 |

#### 基础分析模式（必需）

提供 BPM、节拍点、段落、频段能量等，**不含**多轨分离：

```bash
# 使用游戏 config 目录（推荐，与模组运行时一致）
pip install -r config/beatblock/analyzer/requirements.txt

# 或开发仓库内源文件
pip install -r src/main/resources/beatblock/analyzer/requirements.txt
```

验证：

```bash
python -c "import librosa, soundfile, numpy, scipy; print('ok')"
```

#### Demucs 分轨模式（可选）

在基础依赖已安装的前提下，额外安装 `demucs` + `torch`（含 PyTorch，**体积通常 2GB+**，首次下载较慢）：

```bash
pip install -r config/beatblock/analyzer/requirements-demucs.txt
# 等价于: pip install demucs torch
```

验证：

```bash
python -c "import demucs.api, torch; print('ok')"
```

**何时需要 Demucs：**

- 音频解析面板勾选 **Demucs 分轨**（或分析选项启用 Demucs）
- `analyze.py --demucs` 会分离 drums / bass / vocals / other 四条茎，写入 beatmap 供时间轴多轨预览

**注意：**

- 仅支持 **Python 3.10–3.12**（3.13+ 可能与 torch  wheels 不兼容）
- CPU 可运行但较慢；有 NVIDIA GPU 时 torch 会自动使用 CUDA（视 pip 安装的 torch 版本而定）
- 模组可在分析前**自动尝试** `pip install` 缺失依赖；失败时面板会给出上述手动命令
- 不需要 Demucs 时**不要**安装 `requirements-demucs.txt`，可显著减少磁盘占用

#### 游戏内检测

打开 **视图 → 面板 → 音频解析**，面板会显示 Python / librosa / Demucs / torch / ffmpeg 健康状态，并支持一键触发依赖安装（基础或 Demucs 模式）。

---

## 构建与运行

```bash
# 编译
./gradlew compileJava

# 完整构建（含测试、JaCoCo 覆盖率、SpotBugs）
./gradlew build

# 或仅验证（测试 + 质量报告，不打包 remapJar 以外的产物）
./gradlew check

# 启动带模组的开发客户端（Windows）
./gradlew runClient
```

构建产物位于 `build/libs/beatblock-<version>.jar`，放入 Fabric 模组的 `mods` 文件夹即可。

**跨平台：** ImGui JNI 已包含 Windows / Linux / macOS 三平台 natives，发布包可在对应系统上运行。

---

## 快速上手

1. **进入游戏**  
   在创造模式「工具」物品栏中找到 **BeatBlock 控制器**，手持后**右键**打开编辑器；或按 **B** 切换时间轴播放/暂停。

2. **导入音乐**  
   菜单 **文件 → 导入音乐**（`Ctrl+O`），填写本地 **WAV** 绝对路径。非 WAV 格式需配置 ffmpeg，模组会转换为可播放格式。

3. **分析音频（可选）**  
   打开 **视图 → 面板 → 音频解析**，运行分析后将 BPM、节拍点等写入参考轨，便于对齐与自动生成初稿。

4. **选中方块**  
   在 **工具** / **选择属性** 面板切换选区模式，在世界中框选、笔刷或魔棒选取要参与演出的方块。

5. **创建建造图层（可选，用于「建筑出现」类演出）**  
   见下方 [建造图层](#建造图层build-layer) 工作流。

6. **编排时间轴**  
   在动画轨添加事件，或拖入图层到 **建造还原** 轨；编辑 **事件属性**、**摄像机** 轨与 **Smart Auto Map** 生成初稿。

7. **预览**  
   按 **B** 播放；相机会按摄像机轨采样，方块事件由播放器按时间派发。

8. **保存工程**  
   **文件 → 保存工程 (.osc)**（`Ctrl+S`），下次用 **Ctrl+Shift+O** 打开。

---

## 编辑器界面

编辑器基于 **ImGui Dockspace** 布局，可通过 **视图** 菜单开关各面板、重置或保存窗口布局（写入 `config/beatblock/imgui.ini`）。

| 面板 | 作用 |
|------|------|
| **时间线** | 主编辑区：轨道、片段、事件、播放头 |
| **工具** | 选区模式、笔刷半径、操作方式（加选/减选/替换） |
| **选择属性** | 当前选区信息与 StageObject 相关选项 |
| **事件属性** | 选中事件的动画 preset、BUILD 参数、STEP 烘焙等 |
| **动画库** | 内置 BlockInfluence preset 预览与选用 |
| **建造图层** | 图层列表、可见性、重命名、拖入时间线 |
| **音频解析** | 分析进度、beatmap、环境健康检查 |
| **标记与调试** | 时间轴标记与调试信息 |

### 常用快捷键

| 快捷键 | 功能 |
|--------|------|
| **B** | 切换时间轴播放 / 暂停 |
| **Ctrl+O** | 导入音乐 |
| **Ctrl+S** | 保存工程 (.osc) |
| **Ctrl+Shift+O** | 打开工程 (.osc) |
| **Ctrl+Z / Ctrl+Y** | 撤销 / 重做 |
| **Esc** | 关闭 BeatBlock 编辑器 |

---

## 建造图层（Build Layer）

建造图层用于管理「待建造」的静态方块集合，并与时间轴上的 **BUILD** 序列联动，实现「建筑从无到有」类演出。

### 图层状态

| 状态 | 含义 |
|------|------|
| **FREE_VISIBLE** | 图层内方块在世界中可见（默认） |
| **FREE_HIDDEN** | 隐藏：世界中方块变为空气，状态快照保存在图层内 |
| **BOUND_TO_TRACK** | 已绑定到时间线「建造还原」轨上的片段，不可删除；播放时由 `BuildSequencer` 按 BUILD 事件还原 |

### 推荐工作流

```
选区 → 创建图层 (FREE_VISIBLE)
         ↓ 隐藏图层 (FREE_HIDDEN，世界清空该组方块)
         ↓ 拖到时间线「建造还原」轨 (BOUND_TO_TRACK)
         ↓ 播放 BUILD 事件 → 方块按序出现
```

### 面板操作

- **从选区创建**：自动命名 `layer`、`layer_2` …；同一方块只能属于一个图层
- **重命名**：行内编辑名称（支持撤销）
- **可见性**：眼睛 / 隐藏图标切换（会**真实写入** Minecraft 世界方块）
- **删除**：右键行 → 确认删除（已绑定轨道的图层不可删）
- **拖入时间线**：将图层拖到 **建造还原** 轨，生成 BUILD 片段并绑定

图层数据随 `.osc` 工程 **v2** 格式持久化；打开工程时会恢复隐藏图层的空气状态。

---

## 音频分析与 Smart Auto Map

### 音频参考轨（第 1 层）

- 分析脚本：运行时 `config/beatblock/analyzer/analyze.py`（jar 内 `src/main/resources/beatblock/analyzer/`）
- Python 依赖：见 [Python 分析与 Demucs 分轨](#python-分析与-demucs-分轨)
- 输出契约：`audio.beatmap.Beatmap`（磁盘缓存，**不进入播放器**）
- 导入时间轴：`AudioAnalysisEngine.fillTimelineFromBeatmap` 一次性写入参考数据

### Smart Auto Map（第 2 层初稿）

菜单 **演出 → Smart Auto Map...**：根据段落、节奏等生成动画轨、摄像机与粒子事件的**可编辑初稿**，经 `TimelineDraftWriter` 写入并支持 Undo。属于创作辅助，不是播放路径。

---

## 工程文件（.osc）

`.osc` 为 JSON 格式的轻量工程文件（当前 schema **version 2**），主要包含：

- 项目 ID、时间线名称、关联音频路径
- 时间轴轨道、片段、事件、标记
- **buildLayers**：建造图层 id、名称、方块快照、可见性状态、绑定 clip 等

读写入口：`timeline.project.OscProjectStore`。

---

## 中心模型（三层单向流）

```
第 1 层 音频参考轨（只读，可手改）  →  BPM / 节拍点 / 段落 / 能量曲线
第 2 层 时间轴事件（权威编辑层）    →  StageEvent + CameraEvent
第 3 层 播放器（只消费第 2 层）      →  BlockAnimationEngine + 相机系统
```

**禁止反向依赖：** 播放器不得读取分析 beatmap、不得运行时重新分析音频、不得根据频段能量即时生成方块事件。

```
┌─────────────────────────────────────────┐
│ 第 1 层：音频参考轨（只读导入）           │
└──────────────────┬──────────────────────┘
                   │ 编辑时导入 / 自动映射初稿
                   ▼
┌─────────────────────────────────────────┐
│ 第 2 层：时间轴事件（SoT，人编辑）        │
└──────────────────┬──────────────────────┘
                   │ 播放时钟推进时派发
                   ▼
┌─────────────────────────────────────────┐
│ 第 3 层：播放器（只消费第 2 层）          │
└─────────────────────────────────────────┘
```

概念类型与 Java 类映射、三种演出参数表见 **[架构文档](docs/architecture.md)**。  
重构阶段与验收标准见 **[重构路线图](docs/REFACTOR_ROADMAP.md)**。

---

## 项目结构

```
beatblock/
├── src/main/java/com/beatblock/
│   ├── audio/          # 加载、播放、Python 分析、beatmap
│   ├── automap/        # Smart Auto Map 引擎
│   ├── client/         # 客户端驱动、渲染、选区交互、权威世界写入
│   ├── engine/         # 动画、建造序列、StageObject、建造图层
│   ├── selection/      # 选区管理
│   ├── timeline/       # 时间轴、命令、渲染、.osc 持久化
│   └── ui/             # ImGui 编辑器与各面板
├── src/main/resources/
│   ├── assets/beatblock/   # 语言、图标、物品模型
│   └── beatblock/analyzer/ # analyze.py 与 Python 依赖清单
└── docs/               # 架构与设计文档
```

**世界方块写入：** 单机模式下，BUILD / PLACE / CLEAR 及图层隐藏/显示通过 `BeatBlockAuthoritativeWorldMutator` 在整合服务器线程上写入 `ServerWorld`，保证与存档一致。专用多人服务器尚未支持网络同步包。

---

## 已知限制

- **单机权威写入**：图层可见性与建造播放的世界变更当前针对**单人整合服务器**；专用多人服需后续网络协议。
- **演出导向**：不适合作为实时音游或玩家交互玩法框架。
- **音频格式**：导入对话框默认 WAV；其他格式依赖 ffmpeg 转换。
- **Demucs 分轨**：可选、体积大，需 Python 3.10–3.12 与足够磁盘空间。
- **工程格式**：`.osc` 仍在演进，跨版本打开旧工程请注意备份。

---

## 相关文档

| 文档 | 内容 |
|------|------|
| [docs/architecture.md](docs/architecture.md) | 三层架构、概念 ↔ 代码映射、三种演出参数 |
| [docs/REFACTOR_ROADMAP.md](docs/REFACTOR_ROADMAP.md) | 重构阶段、进度快照、验收标准 |
| [OPTIMIZATION_GUIDE.md](OPTIMIZATION_GUIDE.md) | 代码优化清单与 **完成进度**（P0/P1/P2） |
| [REVIEW_SUMMARY.md](REVIEW_SUMMARY.md) | 2026-06 代码审查结论与路线图 |
| [docs/step-phase-animation-and-cleanup.md](docs/step-phase-animation-and-cleanup.md) | STEP 三段式动画与参数 |
| [docs/block-influence-dimensions.md](docs/block-influence-dimensions.md) | 方块影响维度统一抽象 |

---

## 许可证

本项目采用 [MIT](LICENSE) 许可证（见 `fabric.mod.json` 与仓库 `LICENSE` 文件）。
