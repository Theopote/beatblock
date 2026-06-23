# BeatBlock 重构路线图

> 目标：把"音乐可视化创作工具"这个产品定位，落实成代码里**唯一、无歧义**的数据流。
> 原则：每一阶段结束后项目都必须能编译、能在游戏里跑起来——不做"大爆炸式"重写。

## 进度快照（2026-06）

| 阶段 | 状态 | 说明 |
|------|------|------|
| 0 定基调 | ✅ | `README.md`、`docs/architecture.md` |
| 1 统一数据模型 | ✅ | 已删 `com.beatblock.beat.*`；播放器只消费 `TimelineAnimationEvent` |
| 2 清理死代码 | ✅ | 重复 command、metadata、跨平台 natives |
| 3 跨平台 | ✅ | 三平台 imgui natives + CI JNI 烟雾测试 |
| 4.1 三层边界 | ✅ | 文档 + 禁止播放层读分析轨 |
| 4.2 草稿生成器 | ✅ | `TimelineDraftWriter` 统一 AutoMap / Renderer / BindingEngine / TimelineBuilder；`eventOrigin` + 合并动画缓存 |
| 4.4 维度化效果 | ✅ | `BlockInfluencePresets` + `BlockInfluenceEvaluator` + `VfxEmitter` |
| 4.5 生成式 STEP | ✅ | `PacingStrategy` + `StepSequencePlanner`；调度/烘焙展开；UI「烘焙 STEP」；Timeline 不再需存 `dispatchModel=STEP` |
| 5 测试 | 🟡 | influence / pacing（`StepSequencePlanner`）单测已加；`DistancePacing`、AutoMap 映射测试待补 |
| 6 工程化 | 🟡 | 音频分析已拆；UI 编辑/拖拽已走 Command（Phase A–D）；Demucs requirements 说明待补 |

---

## 阶段 0：定基调（不改代码，先定文档）

在动手改任何类之前，把下面这段话写进 `README.md` 或 `docs/ARCHITECTURE.md` 顶部。这是后续所有删除/合并决策的依据，团队（哪怕只有你一个人）对它有共识之后才动代码：

> BeatBlock 是音乐可视化**创作工具**，不是实时游戏。音频分析的唯一作用是辅助创作者生成初稿；最终呈现的一切（方块变化、相机运动）都是 Timeline 上确定性的、可编辑、可重放的事件数据。播放器只消费 Timeline 数据，对"这条事件是分析出来的还是手动加的"完全无感知。

这句话直接决定了阶段 1～4 要删什么、留什么。

---

## 阶段 1：统一数据模型（消灭三套节拍模型）

### 现状盘点

| 模型 | 文件 | 当前消费方 | 处置 |
|---|---|---|---|
| `audio.beatmap.Beatmap/BeatEvent` | `audio/beatmap/Beatmap.java`, `BeatEvent.java` | UI 面板、`AudioAssetManager`、`AudioAnalysisEngine`、`TimelineRenderer` | **保留**，但重新定位为"只读分析结果"，见下 |
| `com.beatblock.beat.Beatmap/BeatEvent` | `beat/Beatmap.java`, `BeatEvent.java` | `BeatmapGenerator`、`BlockAnimationEngine.onBeatEvent()`、`AnimationManager`、`BeatBlockClientDriver` | **删除**（连同 `BeatScheduler`、`BeatmapGenerator`、`onBeatEvent` 整条调用链） |
| `Timeline.FrequencyEvent` / `TimelineAnimationEvent` | `timeline/*` | `AutoMapGenerator`、`BlockAnimationEngine.scheduleTimelineEvent()` | **确立为唯一的播放层输入** |

### 具体步骤

1. **冻结 `com.beatblock.beat` 包**：给 `Beatmap`、`BeatEvent`、`BeatScheduler` 加 `@Deprecated`，并在类注释写明"将在 vX.X 移除，请使用 `timeline.TimelineAnimationEvent`"。这一步不删代码，只是立一个"禁止新增引用"的标记，方便后续用编译警告追踪剩余引用点。

2. **拔掉 `BlockAnimationEngine.onBeatEvent()` 这条调用路径**（✅ 已完成）：
   - 已删除 `BeatBlockClientDriver` 对 `onBeatEvent()` 的调用。
   - `StepSequenceState` 不保留运行时形态；语义在阶段 4.5 以生成式排布落地（见下）。
   - 已删除 `onBeatEvent`、`advanceStepSequence`、`enqueueStepSequence`、`stepSequences` 及 `DispatchModel`/`StepStartMode`/`StepCompletionMode` 内部枚举；生成参数见 `TimelineAnimationEvent` 的 `dispatchModel`、`stepStartMode`、`blocksPerBeat` 等字段。

3. **删除 `BeatmapGenerator`、`beat/Beatmap.java`、`beat/BeatEvent.java`、`beat/BeatScheduler.java`**，以及 `BeatBlock.java` 里对应的静态字段（`beatmapGenerator`、`beatScheduler`）。

4. **`AnimationManager` 改造**：它目前监听 `BeatScheduler` 的 `BeatEvent`。改为不再独立存在——它的职责（"把节拍事件映射成动画实例"）本质上已经被 `AutoMapGenerator + BlockAnimationEngine` 覆盖，确认后可以整体删除这个类，或者把它仅保留的、`AutoMapGenerator` 没有的能力迁移过去。

5. **`audio.beatmap.Beatmap/BeatEvent` 重新定位**：在类注释明确写"这是音频分析的只读产出，仅供 UI 展示和'生成草稿事件'使用，禁止任何播放逻辑直接消费"。

**验收标准**：全局搜索 `import com.beatblock.beat.` 应该零结果；`BlockAnimationEngine` 里只剩 `TimelineAnimationEvent` 一种输入类型。

---

## 阶段 2：清理纯死代码（低风险，可以最先做，建立信心）

这些改动互相独立、零依赖，建议作为阶段 1 之前的"热身"，或者插空进行：

1. 删除 `timeline/editor/Command.java`、`AddEventCommand.java`、`DeleteEventCommand.java`、`MoveEventCommand.java`（与 `timeline/command/` 包下内容重复，且确认零引用）。
2. 删除或重命名 `visual/BlockDisplayPool.java` 里的 `release()` 方法（当前实现是直接 `discard()`，与 `returnToPool()` 的真实复用语义相悖，且零调用）。如果未来需要"丢弃不复用"的语义，重命名为 `discard()` 更准确。
3. 统一 `fabric.mod.json` 的 `license` 字段为 `MIT`（与仓库 `LICENSE` 文件一致），修正 `homepage`/`sources` 为真实仓库地址。
4. `gradle.properties` 把 `loom_version=1.15-SNAPSHOT` 锁定为最新的正式发布版本。

**验收标准**：`./gradlew build` 通过；`git grep` 确认上述类已不存在。

---

## 阶段 3：补齐跨平台兼容（独立、紧急，可并行处理）

这个问题和数据模型重构无关，但影响所有非 Windows 用户，优先级应该单独提到最前面：

```groovy
// build.gradle，替换原先只有 windows 的写法
runtimeOnly "io.github.spair:imgui-java-natives-windows:1.86.11"
runtimeOnly "io.github.spair:imgui-java-natives-linux:1.86.11"
runtimeOnly "io.github.spair:imgui-java-natives-macos:1.86.11"
```

三个 natives 包都声明为 `runtimeOnly` 不会冲突，ImGui binding 在运行时会根据 `os.name` 自动选择对应的那一个。

同时给 CI 加一个"实际能初始化 ImGui"的烟雾测试（哪怕只是无头模式下创建一次 context 再销毁），不能只验证编译通过——目前 `ubuntu-24.04` 上的 `./gradlew build` 完全无法暴露这类运行时缺失问题。

**验收标准**：在一台 Linux 或 macOS 机器上启动游戏、打开 BeatBlock UI，不崩溃。

---

## 阶段 4：把 Timeline 确立为唯一中心模型，三层数据流落地

这是整个重构的核心，建议拆成可独立验证的子步骤。先看现状：`Timeline.java` 已经有 `Track`、`TimelineAnimationEvent`、`CameraKeyframe`、`GlobalEvent`、`FeatureEvent`、`TimelineMarker` 等结构，这比预想的更接近目标——**不需要推倒重写，只需要把"谁能写入 Timeline、以什么身份写入"这件事规范化**。

### 4.1 明确三层职责边界

```
第1层（只读分析层）   audio.beatmap.Beatmap / FrequencyEvent / BeatGrid / MusicSection
                         ↓ 只能被下面这一层读取，不能被播放层直接读取
第2层（创作层，Timeline）  Track { TimelineAnimationEvent | CameraKeyframe | GlobalEvent }
                         ↓ 唯一对外接口
第3层（播放层）        BlockAnimationEngine.scheduleTimelineEvent(...)
                       TimelineCameraController（消费 CameraKeyframe）
```

**强制规则**：任何新功能如果想让"音乐"影响"画面"，必须经过"生成第2层事件"这一步，不允许新增第三条"音频直接驱动播放"的旁路。

### 4.2 把 `AutoMapGenerator` 降级为"草稿生成器"

当前 `AutoMapGenerator.generate()` 直接调用 `timeline.addAutoAnimationEvent(ev)`，绕开了 `timeline.command.CommandManager` 的 Undo/Redo 体系。改造方向：

- 让自动生成的事件，统一通过 `timeline.command.AddEventCommand` 写入（和创作者手动拖事件到时间轴走同一条代码路径）。
- 好处：自动生成的内容自带撤销能力（创作者生成完一批草稿觉得不满意，可以一键撤销）；同时彻底消除"自动生成的事件"和"手动事件"在数据结构上需要区分对待的情况——它们本来就该是同一种 `TimelineAnimationEvent`。
- `Timeline` 里目前 `blockAnimationCache`/`autoAnimationCache` 这种"手动轨道"和"自动轨道"分两个 List 维护的设计，可以考虑收敛成同一个 List + 一个 `origin: MANUAL | AUTO_GENERATED` 字段，渲染/统计需要分开看时按字段过滤即可，而不是在数据结构层面割裂成两套缓存。

### 4.3 相机轨道与方块事件轨道平级

现状 `CameraDirector`、`TimelineCameraController` 与方块动画相关类是分开的两条逻辑线。改造目标：

- 在 Timeline 编辑器 UI（`TimelinePanel`）里，相机关键帧轨道和方块事件轨道应该在同一个时间刻度上左右对齐显示、可以同时拖拽编辑、共享同一个播放头（playhead）。
- 检查 `CameraKeyframe` 与 `TimelineAnimationEvent` 当前是否共享同一套"时间单位"（采样率/精度一致），确保两条轨道在时间轴上严格对齐，不存在"方块用毫秒、相机用 tick"这种隐性不一致。

### 4.4 引入"维度化"的效果系统，替代效果类各自为战

当前 `engine/effects/` 下 `DropEffect`、`JumpEffect`、`PulseEffect`、`RiseEffect`、`OrbitEffect`、`SpiralEffect`、`WaveEffect`、`ExplosionEffect`、`MeteorEffect` 是独立的类，未来每加一种新效果就要新增一个类，组合能力弱。建议引入一层抽象：

```java
// 新增：每个维度独立描述"如何随时间变化"
interface TransformCurve {
    Vec3d positionAt(double t01);   // 归一化时间 0~1 → 位置偏移
    Vec3d scaleAt(double t01);
    float rotationAt(double t01);
}

interface AppearanceCurve {
    boolean visibleAt(double t01);      // 存在性维度
    BlockState blockStateAt(double t01); // 方块种类替换维度（可选，大多数效果不需要）
}

// 一个"效果"== 一组维度曲线的组合 + 缓动函数，而不是一段命令式代码
record EffectPreset(
    TransformCurve transform,
    AppearanceCurve appearance,
    Easing easing,
    ParticlePreset particles   // 可选附加视觉强调，与方块本体变化解耦
) {}
```

- 现有的 9 个 Effect 类不需要立刻全部重写，可以先挑 2～3 个（比如 `RiseEffect`、`DropEffect`，正好对应你说的"镜头跟随下落"场景）按这套新模型重新实现，验证抽象是否成立，再逐步迁移剩余的。
- 这一步可以和阶段 4.1～4.3 解耦，单独排期，风险较低（属于新增能力，不动现有播放主链路）。

### 4.5 用"生成式排布"取代 `StepSequenceState` 运行时状态机 ✅

> **状态**：主干已完成（2026-06）。原 `StepSequenceState` / `onBeatEvent` / `stepSequences` 已从代码库删除；下文「改造方向」保留为设计依据，「实现状态」描述当前代码。

**原问题**（已解决）：`StepSequenceState` 与阶段 1 要删的三套节拍模型同属一类问题——运行时的「被动反应」（监听 `BeatEvent`，每次推进 `nextIndex`），而「第几个方块对应第几拍」本应在播放前算好。

**改造方向**：从「会随时间变化的状态」变成「一次性算好的、N 个普通 `TimelineAnimationEvent`」。生成/烘焙完成后，播放器不需要知道「这是个序列」——只看到 N 个各自带时间戳的普通事件。

```java
// 生成时调用一次，不是运行时监听（实际 API 见 timeline/generation/PacingStrategy.java）
interface PacingStrategy {
    List<Double> computeTimestamps(PacingRequest request);
}

// 实现：
// - BeatGridPacing   — 贴 audio 特征轨节拍（ReferenceBeatResolver）
// - FixedIntervalPacing — 固定 BPM
// - DistancePacing   — ✅ 按方块路径 3D 距离累加（`pacingMode=DISTANCE`）
```

**写入 Timeline**（4.2 同路径）：`StepSequenceBaker` + `TimelineDraftWriter` → `AddTimelineAnimationEventCommand`（可 Undo）。工具栏 **「烘焙 STEP」** 将 `dispatchModel=STEP` 展开为 N 个 `BURST` + `singleBlockX/Y/Z` 事件。

老概念到新模型的映射：

| 原运行时概念 | 新模型里的角色 | 代码中的位置 |
|---|---|---|
| `BURST` / `STEP` dispatch | 生成时的 pacing 参数（同拍炸开 vs 错开） | 事件参数 `dispatchModel`；`blocksPerBeat` 控制每拍块数 |
| `IMMEDIATE` / `NEXT_BEAT` start mode | 生成时第一个时间戳的对齐规则 | `stepStartMode` → `StepSequencePlanner` + `PacingRequest.immediate` |
| `StepSequenceState.nextIndex` | **不存在** | — |
| `stepSequences` 列表 | **不存在** | — |
| `onBeatEvent` 监听 | **已删除** | 规划/烘焙读 Layer 1 参考轨，不监听实时 beat 流 |

#### 实现状态（2026-06）

| 项 | 状态 | 说明 |
|---|---|---|
| 删除运行时状态机 | ✅ | 无 `StepSequenceState`、`onBeatEvent`、`nextIndex` |
| `PacingStrategy` + `StepSequencePlanner` | ✅ | `timeline/generation/` |
| 烘焙进 Timeline | ✅ | `StepSequenceBaker`、`StepBurstEventFactory`；UI「烘焙 STEP」 |
| 统一草稿写入 + Undo | ✅ | `TimelineDraftWriter` |
| 单测 | 🟡 | `StepSequencePlanner` / `DistancePacing` / `StepBurstEventFactory`；AutoMap 映射待补 |
| **理想态**：文件里只有 N 个普通事件 | 🟡 | 需用户点「烘焙 STEP」；未烘焙时 Timeline 仍可存 `dispatchModel=STEP` |
| **过渡态**：调度时展开 | 🟡 | `BlockAnimationEngine.scheduleExpandedStepSequence` 在**首次调度**时用同一 planner 展开（无状态机，但非持久化） |
| `DistancePacing` | ✅ | `pacingMode=DISTANCE` + 路径累加；UI「跳跃距离」 |
| `stepCompletionMode` | 🟡 | UI 仍可编辑；planner/engine **未使用**，烘焙时 strip |

**推荐工作流**：编辑 STEP 序列 → 点「烘焙 STEP」→ 保存 Timeline（文件中无 `dispatchModel=STEP`）→ 播放器只消费 N 个普通事件。

与 4.2 同属「生成器家族」：`AutoMapGenerator` = 一个特征 → 一个事件；STEP 烘焙 = 一组方块 + 节奏来源 → N 个事件。输出端均为 `TimelineAnimationEvent`。

**预留扩展**（本阶段不做）：生存模式实时跑酷——玩家踩块瞬间触发 `EffectPreset`，不预生成时间戳；与预生成路径共享 4.4 影响层，仅事件来源不同。

### 4.6 三种目标效果到模型的映射（验收用例）

重构阶段4完成后，下面三个场景应该都能在不新增播放器逻辑的前提下，仅通过"组合 Timeline 事件 + EffectPreset 参数"实现，作为整个阶段的最终验收标准：

| 场景 | Timeline 上的表达 |
|---|---|
| 建筑从无到有 | 大量 `TimelineAnimationEvent`，`AppearanceCurve.visibleAt` 从 false→true，时间点由"音频段落能量"或"创作者手动排布"决定顺序；相机轨道是缓慢环绕或固定的 `CameraKeyframe` 序列 |
| 跑酷敲击 | 一组按 `DistancePacing`（4.5）一次性算好时间戳的 `TimelineAnimationEvent`，`AppearanceCurve.blockStateAt` 在踩中瞬间短促变化 + `ParticlePreset` 强调；相机轨道跟随移动主体的位置事件 |
| 镜头跟随下落 | `TimelineAnimationEvent` 的 `TransformCurve.positionAt` 描述从高空到目标点的轨迹；相机轨道主动引导，`CameraKeyframe` 密度更高、带运镜缓动 |

如果某个场景做不到、还要去改播放器底层代码才能实现，说明模型抽象还没到位，需要回头修整 4.4 的维度划分，而不是又开一条特殊逻辑分支。

---

## 阶段 5：补单元测试（与重构同步推进，不是事后补）

建议在阶段 1～4 进行的过程中，每拆出一个不依赖 Minecraft 运行时的纯逻辑类，就顺手补测试，而不是全部重构完再统一补：

- `audio/analysis/*`（BPM 检测、频段能量、FFT 相关的纯计算）
- `timeline/util/SnapSystem.java`、`TimeUtils.java`
- 阶段 4.4 新增的 `TransformCurve`/`AppearanceCurve` 实现——这些是纯函数（输入 t01 输出数值），是整个项目里最容易测、回归收益最高的部分
- 阶段 4.5 新增的 `PacingStrategy` 各实现（`BeatGridPacing`/`FixedIntervalPacing`/`DistancePacing`）——同样是纯函数，输入对象数量+参数，输出时间戳列表，边界条件（0 个对象、1 个对象、时间戳重叠）很容易枚举测试
- `AutoMapGenerator` 改造后的"频段事件 → Timeline 事件"映射逻辑

**验收标准**：每完成一个子模块的重构，对应的测试已经写好并通过，而不是积压到最后。

---

## 阶段 6：收尾的工程化项

优先级低于以上，可以穿插进行：

1. ~~拆分 `AudioAnalysisService.java`（目前 1300+ 行）~~ ✅ 已拆：`FfmpegLocator` / `FfmpegPcmDecoder`、`PythonAudioAnalyzer`（`IAudioAnalyzer`）、`AudioAnalysisOrchestrator`、`PythonRuntimeHealthMonitor`；`AudioAnalysisService` 为门面。
2. ~~UI 属性编辑解耦（`EventPropertiesPanel` / `TimelineInteraction`）~~ ✅ Phase A–D：`AnimationEventPropertiesEditor`、`CameraEventPropertiesEditor`、`GenericEventPropertiesEditor` + `UpdateAnimationEventCommand` / `MoveEventCommand` / `ApplyClipDragCommand` + `TimelineEventEditActions`；属性面板、内联弹窗、事件/片段拖拽均支持 Undo。待办：`AudioAnalysisPanel` 解耦。
3. `analyzer/requirements.txt` 补充说明 `--demucs` 模式所需的可选依赖（`demucs`、`torch`），即使不写进硬性 `requirements.txt`（避免强制所有用户装大体积的 torch），也应该在文件里用注释或单独的 `requirements-demucs.txt` 说明。
4. 补 README：项目简介、构建方式、依赖要求、三种核心使用场景的截图/简述（直接用你刚描述的"建造过程/跑酷敲击/镜头跟随"这三段话就是很好的素材）。

---

## 建议的执行顺序总览

```
阶段 3（跨平台修复）  ─┐ 可立刻单独做，互不阻塞
阶段 2（删死代码）    ─┘

阶段 0（定基调文档）  → 阶段 1（统一数据模型，删 beat.* 旧链路）
                          ↓
                       阶段 4（Timeline 中心化 + 维度化效果系统）
                          ↓ 同步进行
                       阶段 5（补测试）
                          ↓
                       阶段 6（收尾工程化）
```

阶段 3 和阶段 2 可以今天就动手，几乎零风险、零依赖。阶段 0 和阶段 1 是核心（**阶段 1 与 4.5 主干已完成**）。阶段 4 建议顺序：4.2（草稿写入）→ 4.4（维度化效果）→ 4.5（生成式 STEP；与 4.4 可并行，不必严格阻塞）。剩余收尾：**4.3 相机轨 UI 对齐**、**AutoMap 单测**、阶段 6 README / Demucs 说明。
