# BeatBlock 架构：三层单向数据流

本文档定义 BeatBlock 的**中心模型**。所有新功能应能回答：自己属于哪一层、数据从哪来、往哪流、播放器是否需要知道。

产品定义见 [README](../README.md) 首段。

## 共同抽象

三种典型演出（建筑从无到有、跑酷敲击、镜头跟随下落）不是三套机制，而是同一抽象的参数组合：

| 效果 | 时间轴上发生什么 | 对象（舞台） | 相机角色 |
|------|------------------|--------------|----------|
| **建筑从无到有** | 方块按顺序「出现」（瞬间或带特效） | 固定、海量静态方块（最终建筑） | 通常固定或缓慢环绕，让人看清整体在变多 |
| **跑酷敲击** | 移动主体经过时，方块在被踩瞬间触发反馈（变色/弹起/粒子） | 少量、沿路径排列的方块 | 跟随移动主体（第三人称或第一人称） |
| **镜头跟随下落** | 方块带物理感运动到目标位置 | 中等数量、有起点终点的方块 | 主动牵引视线，相机运动本身是演出的一部分 |

共同抽象：

> **舞台对象（一组方块）** + **在时间轴某一刻发生的动作** + **相机在同一时间轴上的运动**

## 三层模型（信息只能单向流动）

```
┌─────────────────────────────────────────────────────────────┐
│ 第 1 层：音频参考轨（只读导入，创作者可手动修正）              │
│   BPM / 节拍点 / 段落 / 频段能量曲线 / 波形预览               │
│   不驱动播放；仅供对齐、可视化和自动生成初稿                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ 一次性导入 + 可选自动映射（编辑时）
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ 第 2 层：时间轴事件（权威编辑层，人编辑，机器辅助生成初稿）    │
│   StageEvent  → 方块何时对谁做什么                           │
│   CameraEvent → 相机何时处于何种姿态/路径                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ 播放时钟推进时按时间派发
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ 第 3 层：播放器（只消费第 2 层，对第 1 层完全无感知）          │
│   在指定时间点把 StageEvent / CameraEvent 应用到世界里       │
└─────────────────────────────────────────────────────────────┘
```

**禁止反向依赖**：播放器不得读取分析 beatmap、不得运行时重新分析音频、不得根据频段能量即时生成方块事件。

音频**播放时钟**只用于对齐预览进度，不是事件来源。

## 概念类型 ↔ 现有代码映射

命名上保留历史类名；概念上的 `StageEvent` / `CameraEvent` 对应如下实现。

### 第 1 层：音频参考轨

| 概念 | 代码 | 说明 |
|------|------|------|
| 分析契约（磁盘缓存） | `audio.beatmap.Beatmap` | Python `analyze.py` 输出，**不进入播放器** |
| 导入到时间轴 | `AudioAnalysisEngine.fillTimelineFromBeatmap` | 一次性写入参考数据 |
| 节拍点 / 能量点 | `FeatureEvent` + `FeatureTrack` | 时间轴上的参考轨，可手改 |

### 第 2 层：时间轴事件（SoT）

| 概念 | 代码 | 说明 |
|------|------|------|
| **舞台对象** | `engine.StageObject` + `StageObjectSystem` | 一组方块 + 中心点 + 分组规格 |
| **动作模板** | `AnimationDefinition` + `AnimationLibrary` | 出现/变形/移动等效果的可组合定义 |
| **StageEvent** | `TimelineAnimationEvent` | 时间点、目标对象 ID、动作类型、参数、`energy` |
| 动作模式 | `TimelineAnimationActionMode` | `ANIMATE` / `BUILD` / 控制类变更 |
| **CameraEvent** | 摄像机轨 `Clip` + `TimelineEvent` + 参数 | 姿态、路径段类型见 `CameraSegmentKind` |
| 关键帧时间点 | `CameraKeyframe` | 轨上的时间标记；姿态由 clip 内事件解析 |
| 工程容器 | `Timeline` | 轨道、片段、元数据、持久化（`.osc` 等） |

自动映射（`AnimationBindingEngine`、`TimelineRenderer.populateAnimationTrackFromAudioFeatures`）在**编辑/导入时**从第 1 层生成第 2 层初稿，属于创作辅助，不是播放路径。

### 第 3 层：播放器

| 职责 | 代码 | 只认识 |
|------|------|--------|
| 编排时钟与派发 | `BeatBlockClientDriver` | `TimelineAnimationEvent`、播放时间 |
| 舞台动作执行 | `BlockAnimationEngine` | `TimelineAnimationEvent` → `EngineAnimationInstance` |
| 建造序列 | `BuildSequencer` | 同上（`BUILD` 模式） |
| 方块控制 | `BlockControlExecutor` | 同上（变色等） |
| 相机采样与应用 | `TimelineCameraController`、`TimelineCameraEvaluator` | 摄像机轨上的 clip / 事件 |
| 世界渲染 | `BeatBlockAnimatedBlocksRenderer` | `BlockAnimationEngine` 当前帧状态 |

无论事件是创作者手拖还是自动映射生成，**播放逻辑只有一套**。

## 三种效果 → 参数组合示例

均在第 2 层用 `TimelineAnimationEvent` + 摄像机轨表达，第 3 层同一套播放器执行。

### 建筑从无到有

- **StageEvent**：`actionMode=BUILD` 或 `ANIMATE` + `dispatchModel=STEP`，`blocksPerBeat` 控制每拍出现数量；目标为海量方块的 `StageObject`
- **CameraEvent**：固定关键帧或 `CameraSegmentKind` 环绕段，慢速 orbit
- **参考轨**：可选从 kick 轨自动映射初稿，创作者再细调顺序与节奏

### 跑酷敲击

- **StageEvent**：`ANIMATE` + 短时长 + 高 `energy` 阈值；目标为路径上的少量 `StageObject`
- **CameraEvent**：跟随段（follow / 第三人称），与主体移动时间轴对齐
- **触发**：事件时间由创作者或参考轨踩点放置，**非**实时音频触发

### 镜头跟随下落

- **StageEvent**：`ANIMATE` + 位移动画定义 + 较长 `durationSeconds`；中等规模对象
- **CameraEvent**：主动设计的下落/牵引路径（segment + 关键帧），相机是演出主体之一
- **参数**：`usePhaseAnimation`、空间派发等见 STEP 文档

## 相关文档

- [STEP 三段式动画与参数](step-phase-animation-and-cleanup.md)
- [README](../README.md)
