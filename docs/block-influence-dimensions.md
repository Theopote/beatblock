# 方块影响维度：动作类型统一抽象

本文档定义第 3 层播放器里「对方块做什么」的**共同模型**。三种典型演出（建筑、跑酷踩点、镜头跟随下落）以及 `engine/effects/` 下的 Jump / Drop / Rise 等，都应收敛为**同一套维度 + 插值曲线 + 派发策略**，而不是每种效果各写一条执行路径。

产品层抽象见 [architecture.md](architecture.md)「共同抽象」；本文是**播放器内部**如何落地。

## 问题

现状（2026-06）：

| 路径 | 代码 | 实际在改什么 |
|------|------|--------------|
| 渲染层变换 | `AnimationEffect` → `AnimatedBlock` | 位置 / 旋转 / 缩放 |
| 世界写入·建造 | `BuildSequencer` | 按序 `setBlockState`（出现/溶解） |
| 世界写入·控制 | `BlockControlExecutor` | 瞬间换材质 / 放置 / 清除 |
| 派发时序 | `BlockAnimationEngine` STEP 序列 | 每参考轨节拍推进若干块 |

`JumpEffect`、`DropEffect`、`MeteorEffect` 等同属 `AnimationEffect`，但各自硬编码曲线；`MeteorEffect` 已同时改**位置 + 缩放**两个维度。`BuildSequencer` 管**存在性**，却与 `AnimationPlayer` 完全分离。新增一种演出很容易再复制一个 `*Effect` 类或再开一个 Sequencer。

目标：**新增效果 ≈ 新增预设（曲线组合 + 派发参数）**，而不是新增 Java 类。

## 核心模型

### 1. 五个影响维度

每个维度在单块、单事件实例上独立描述「从哪到哪、怎么插值」。未启用的维度不参与求值。

| 维度 | 含义 | 典型取值空间 | 应用层 |
|------|------|--------------|--------|
| **EXISTENCE** | 块是否存在于世界中 / 是否参与渲染 | `false → true`（出现）、`true → false`（消失） | 世界写入或渲染可见性 |
| **TRANSFORM** | 相对原始格位的空间变换 | 位置偏移、旋转、缩放 | 仅渲染层（`AnimatedBlock`） |
| **APPEARANCE** | 方块种类 / BlockState | `AIR → DIAMOND_BLOCK`、`A → B` 短促闪烁 | 世界写入 |
| **VFX** | 粒子、光效、音效强调 | 触发时刻 + 强度包络 | **独立发射器**，不写入 `AnimatedBlock` |
| *(派发)* | 哪些块、何时开始 | STEP / BURST / 空间排序 | `TimelineAnimationEvent` 参数 |

说明：

- **存在性**与**外观**都动世界方块，但语义不同：存在性是「有没有这块」；外观是「有这块但换材质」。跑酷踩点主要是外观短脉冲；建筑主要是存在性按序变 true。
- **TRANSFORM** 拆成 position / rotation / scale 三个**子通道**即可，共享同一时间轴与缓动，不必再拆成三个顶层维度。
- **VFX** 只订阅「某块在某时刻跨阈值」（如 existence 0→1、appearance 切换），不与方块本体状态耦在一个类里。

### 2. 通道与曲线（Channel + Curve）

每个启用的维度（或子通道）由一条**通道规格**描述：

```
ChannelSpec {
  enabled: bool
  from, to          // 维度相关的 typed value
  curve: CurveKind  // LINEAR | EASE_IN | EASE_OUT | SINE_BUMP | GRAVITY(t²) | ...
  path: PathKind    // OFFSET_Y | WORLD_TRAJECTORY | SCALE_UNIFORM | BLOCK_STATE | ...
  durationPolicy    // 占事件总时长比例，或绝对秒数；可接 STEP 三段式 entry/idle/exit
}
```

求值入口（概念）：

```
sample(dimension, blockContext, tNormalized, energy) → partial state
```

- `tNormalized`：0～1，来自 `EngineAnimationInstance.getProgress()` 或 STEP 子进度。
- `energy`：0～1，作为幅度乘子（现有 `AnimationEffect` 已有此约定）。
- `blockContext`：原始 `BlockPos`、相对舞台中心、 per-block 哈希（Meteor 横向散射用）。

**多个通道**对同一 `BlockRenderState` / `BlockWorldState` 顺序合并；同维度多通道时后写覆盖或按规则混合（例如 scale 相乘、position 相加——需在实现里固定一种组合律）。

### 3. 预设（Preset）= 曲线组合 + 派发

**`BlockInfluencePreset`**（第 2 层可引用的模板 id，第 3 层解析执行）：

```
BlockInfluencePreset {
  id, displayName
  defaultDurationSeconds
  channels: { EXISTENCE?, TRANSFORM.position?, TRANSFORM.scale?, APPEARANCE?, vfxTriggers? }
  defaultDispatch: { dispatchModel, spatialMode, blocksPerBeat, ... }  // 可选默认值
}
```

第 2 层 `TimelineAnimationEvent` 仍携带：

- `animationTypeId` → 解析为 Preset（替代现 `AnimationDefinition` + `List<AnimationEffect>`）
- 事件级参数覆盖 preset 默认值（`meteorHeight`、`placeBlock` 等）

三种典型演出 = **三个 preset**，不是三套引擎：

| 预设意图 | 主维度 | 通道组合（示意） | 派发 |
|----------|--------|------------------|------|
| **建筑从无到有** | EXISTENCE | `false→true`，可叠加 TRANSFORM.scale `0→1` 渐显 | `BUILD` 或 STEP：顺序由 `buildMode` / 空间排序决定 |
| **跑酷踩点** | APPEARANCE (+ 可选 TRANSFORM) | APPEARANCE 短脉冲 `A→B→A`；TRANSFORM.position `sin` 小跳；VFX 在 appearance 边沿触发 | BURST 或单块 STEP；时间由事件/参考轨对齐 |
| **镜头跟随下落** | TRANSFORM.position | 轨迹 `fromOffset → origin`，curve=GRAVITY；可选 scale 远小近大 | BURST 或带 `sequentialDelay` 的波浪 |

现有 `AnimationLibrary` 注册可逐步改为 preset 表：

| 现 `AnimationDefinition` | 可收敛为 |
|--------------------------|----------|
| `BlockJump` | TRANSFORM.position, SINE_BUMP, height=2 |
| `BlockDrop` / `BlockRise` | TRANSFORM.position, LINEAR ±Y |
| `Pulse` | TRANSFORM.scale, SINE_BUMP |
| `Meteor` | TRANSFORM.position GRAVITY + TRANSFORM.scale linear |
| `BuildSequencer` 行为 | EXISTENCE step + APPEARANCE placeBlock |

## 与现有代码的映射

```
TimelineAnimationEvent
        │
        ▼
BlockInfluenceEvaluator          ◄── 目标：单一求值器
        │
        ├── EXISTENCE / APPEARANCE ──► WorldMutationPlanner ──► world.setBlockState
        │
        ├── TRANSFORM ──► AnimatedBlock（每帧）
        │
        └── VFX triggers ──► ParticleEmitter / SoundEmitter（旁路）
```

| 现类 | 迁移角色 |
|------|----------|
| `AnimatedBlock` | TRANSFORM 的运行时载体；增加 `visible` / `alpha` 若要做纯渲染层存在性 |
| `AnimationPlayer` | 改为对 preset 调 `BlockInfluenceEvaluator`，而非遍历 `AnimationEffect` |
| `AnimationEffect` | **过渡**：每个旧 effect 改写成 preset 的工厂或内置 curve；最终删除接口 |
| `BuildSequencer` | 并入 EXISTENCE 维度的「分块 step 调度」；排序逻辑保留为 `DispatchPolicy` |
| `BlockControlExecutor` | 并入 APPEARANCE / EXISTENCE 的「瞬时突变」分支 |
| `BeatBlockAnimatedBlocksRenderer` | 只读 TRANSFORM + 渲染层 visible/alpha |

### 世界写入 vs 渲染层

| 维度 | 预览/播放 | 原因 |
|------|-----------|------|
| EXISTENCE（建造） | 写世界 | 建筑最终态必须在世界里 |
| APPEARANCE（踩点变色） | 写世界或客户端 overlay | 短促反馈可 overlay，持久变更写世界 |
| TRANSFORM | 仅渲染 | 不改变 schematic 格位，便于撤销与编辑 |

同一 preset 可声明 `applicationMode: RENDER | WORLD | HYBRID`，避免 BUILD 与 ANIMATE 在驱动层分裂成两个 tick 入口（长期目标）。

## 派发策略（与维度正交）

维度回答「每块状态怎么变」；派发回答「哪块何时开始变」。

| 策略 | 代码现状 | 与维度关系 |
|------|----------|------------|
| **BURST** | 事件时刻全体同时 sample | 共用同一 `t` |
| **STEP** | `BlockAnimationEngine` + 参考轨节拍 | 每块独立 `t` 起点；EXISTENCE 建造与 ANIMATE 步进共用排序器 |
| **空间序** | `spatialMode`、`buildMode` | 只影响块顺序，不改变曲线形状 |

建筑 = **EXISTENCE 维度** + **STEP/BUILD 派发** + 排序策略。  
跑酷 = **APPEARANCE（+ 可选 TRANSFORM）** + **BURST/单块事件时间**。  
下落 = **TRANSFORM.position** + **BURST 或 sequentialDelay**。

## 实现路线（建议分三期）

### 期 1：文档 + 曲线库（低风险） ✅

- 新增 `engine/influence/`：`CurveKind`、`ChannelSpec`、`BlockInfluencePreset`、`BlockInfluencePresets`
- 新增 `CurveLibrary`：`sample(curve, t)` 及 Jump/Drop/Meteor/Pulse 组合公式
- `AnimationEffect` 实现类委托 `CurveLibrary`，行为不变
- 单元测试：`CurveLibraryTest`

### 期 2：统一求值器 ✅

- `BlockInfluenceEvaluator` + `InfluenceFrame` + `BlockInfluenceOrchestrator`
- `AnimationPlayer` 与 `BuildSequencer` 经 orchestrator 单 tick；`BeatBlockClientDriver` 不再单独调 build tick
- `AnimationLibrary` 由 `BlockInfluencePresets` 注册；`AnimationDefinition` 持有 preset

### 期 3：VFX 解耦与第 2 层编辑 ✅

- `VfxEmitter`（客户端）消费 `InfluenceFrame.vfxTriggers`
- `AppearancePulseTracker`：APPEARANCE 通道中点闪烁 + 实例结束还原
- 建造 EXISTENCE mutation 附带 `existence_place` / `existence_dissolve` VFX
- 新 preset `BlockTap`（跑酷踩点：跳起 + 缩放 + APPEARANCE）
- 事件属性面板：Preset 通道预览、`vfxEnabled`、`flashBlock`
- 删除期 1 遗留 `AnimationEffect` 及 `engine/effects/*` 实现类

## 新增效果检查清单

添加一种演出前，先问：

1. 动哪些**维度**？（多数情况 1～2 个）
2. 每维 **from/to + curve** 是否已有 `CurveKind`？没有则加曲线，不加 Effect 类
3. **派发**用 BURST、STEP 还是 BUILD 序？
4. 是否需要 **VFX**？若需要，只加 trigger 规则，不进 transform 类
5. 写世界还是只渲染？

若 1～4 都能用现有 channel 表达 → **只加 preset**（及 Timeline 参数默认值）。

## 相关文档

- [architecture.md](architecture.md) — 三层数据流与三种演出参数表
- [step-phase-animation-and-cleanup.md](step-phase-animation-and-cleanup.md) — STEP 派发与三段式时间参数
