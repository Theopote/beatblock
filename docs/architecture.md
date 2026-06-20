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

播放器内部，「动作」进一步统一为 **对方块的影响维度 + 插值曲线 + 派发策略**（存在性 / 空间变换 / 外观 / 独立 VFX），三种演出是同一系统的参数预设，而非三套实现。详见 [方块影响维度](block-influence-dimensions.md)。

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
| **动作模板** | `AnimationDefinition` + `BlockInfluencePresets` + `BlockInfluenceEvaluator` | preset 通道组合；`AnimationLibrary` 由内置 preset 注册 |
| **StageEvent** | `TimelineAnimationEvent` | 时间点、目标对象 ID、动作类型、参数、`energy` |
| 动作模式 | `TimelineAnimationActionMode` | `ANIMATE` / `BUILD` / 控制类变更 |
| **CameraEvent** | 摄像机轨 `Clip` + `TimelineEvent` + 参数 | 姿态、路径段类型见 `CameraSegmentKind` |
| 关键帧时间点 | `CameraKeyframe` | 轨上的时间标记；姿态由 clip 内事件解析 |
| 工程容器 | `Timeline` | 轨道、片段、元数据、持久化（`.osc` 等） |

自动映射（`AnimationBindingEngine`、`TimelineRenderer.populateAnimationTrackFromAudioFeatures`、`AutoMapGenerator`）在**编辑/导入时**从第 1 层生成第 2 层初稿，经 `TimelineDraftWriter` 写入（可 Undo），属于创作辅助，不是播放路径。

STEP 序列有两种落地方式（均无 `StepSequenceState` 运行时状态机）：

1. **推荐（持久化）**：工具栏「烘焙 STEP」→ `StepSequenceBaker` 将一条 `dispatchModel=STEP` 事件展开为 N 个 `BURST` 普通事件（`singleBlockX/Y/Z`），经 `TimelineDraftWriter` 写入第 2 层；保存后播放器只看到 N 个带绝对时间戳的事件。
2. **过渡（未烘焙）**：Timeline 仍存 `dispatchModel=STEP` 时，`BlockAnimationEngine.scheduleExpandedStepSequence` 在**首次调度**时用 `StepSequencePlanner` + `PacingStrategy` 一次性算出 N 个 `EngineAnimationInstance`（读 Layer 1 参考轨 via `ReferenceBeatResolver`，不监听实时 beat）。

规划/算时间戳：`timeline/generation/PacingStrategy`、`StepSequencePlanner`、`StepBurstEventFactory`。

### 第 3 层：播放器

| 职责 | 代码 | 只认识 |
|------|------|--------|
| 编排时钟与派发 | `BeatBlockClientDriver` + `VfxEmitter` | `TimelineAnimationEvent`、播放时间、`InfluenceFrame` |
| 舞台动作执行 | `BlockAnimationEngine` | `TimelineAnimationEvent` → `EngineAnimationInstance` |
| 建造序列 | `BuildSequencer` | 同上（`BUILD` 模式） |
| 方块控制 | `BlockControlExecutor` | 同上（变色等） |
| 相机采样与应用 | `TimelineCameraController`、`TimelineCameraEvaluator` | 摄像机轨上的 clip / 事件 |
| 世界渲染 | `BeatBlockAnimatedBlocksRenderer` | `BlockAnimationEngine` 当前帧状态 |

无论事件是创作者手拖还是自动映射生成，**播放逻辑只有一套**。

## 三种效果 → 维度预设 + 时间轴参数

均在第 2 层用 `TimelineAnimationEvent` + 摄像机轨表达；第 3 层由 **BlockInfluenceEvaluator**（目标）按维度求值，而非按效果名分支。

| 演出 | 主维度 | 派发 | StageEvent 要点 | CameraEvent |
|------|--------|------|-----------------|-------------|
| **建筑从无到有** | EXISTENCE（可选 TRANSFORM 渐显） | BUILD / STEP + 空间排序 | `buildMode`、`blocksPerBeat`、参考轨初稿 | 固定或慢 orbit |
| **跑酷敲击** | APPEARANCE ± TRANSFORM 短脉冲 | BURST / 单块事件时间 | 短 `durationSeconds`、高 `energy`；VFX 独立触发 | 跟随主体 |
| **镜头跟随下落** | TRANSFORM.position 轨迹 | BURST 或 sequentialDelay | 长 `durationSeconds`、`Meteor`/下落类 preset | 主动下落/牵引路径 |

参数细节与曲线形状见 [block-influence-dimensions.md](block-influence-dimensions.md)。

## 相关文档

- [方块影响维度（动作类型统一抽象）](block-influence-dimensions.md)
- [STEP 三段式动画与参数](step-phase-animation-and-cleanup.md)
- [README](../README.md)
