# STEP 三段式动画与参数清理完善文档

## 1. 目标与背景

本次完善聚焦于 Timeline 动画事件在 `dispatchModel=STEP` 与 `dispatchModel=BURST` 两种模式切换时的参数一致性问题，尤其是三段式动画（进场/保持/退场）相关参数的残留风险。

在历史行为中，若一个事件曾以 STEP 模式启用三段式动画，再切回 BURST，部分 STEP 专属参数可能继续残留在事件参数中，导致：

- 事件语义与参数集不一致（BURST 事件却携带 STEP 细分参数）。
- 后续编辑时 UI 回填状态混乱。
- 运行时或调试观察中出现“不可见配置污染”。

本次改动的核心是：

- 明确 STEP 专属参数边界。
- 在切回 BURST 时一次性清理所有 STEP 专属参数。
- 使参数契约与 UI 行为保持可预测的一致性。

## 2. 变更范围

### 2.1 已修改文件

- `src/main/java/com/beatblock/ui/panels/EventPropertiesPanel.java`

### 2.2 本次代码级变更

在 `applyAnimationChanges(...)` 中，当 `stepDispatch=false`（即 `dispatchModel=BURST`）时，新增并确认了如下清理逻辑：

- `usePhaseAnimation`
- `entryDurationPercent`
- `idleDurationPercent`
- `exitDurationPercent`

并与既有 STEP 参数清理逻辑合并为完整集合（见下一节）。

## 3. 参数契约（最终）

## 3.1 通用参数（STEP/BURST 都可存在）

- `actionMode`
- `mode`（兼容别名）
- `durationSeconds`
- `energy`
- `energyThreshold`
- `animationType`
- `targetObject`
- `dispatchModel`
- `inheritGroupSpatial`
- （可选）`spatialMode`、`sequentialDelaySeconds`（仅在 `inheritGroupSpatial=false` 时保留）

## 3.2 STEP 专属参数

以下参数仅在 `dispatchModel=STEP` 语义下有效：

- 步进行为
- `blocksPerBeat`
- `stepStartMode`
- `stepCompletionMode`

- 镜头调制
- `cameraAdaptiveStep`
- `cameraFrustumGating`
- `cameraEdgePriority`
- `cameraNearDistance`
- `cameraFarDistance`
- `cameraNearScale`
- `cameraFarScale`

- 三段式动画
- `usePhaseAnimation`
- `entryDurationPercent`
- `idleDurationPercent`
- `exitDurationPercent`

## 3.3 BURST 模式清理规则

当用户将事件设置为 `dispatchModel=BURST` 后，系统会在保存时移除全部 STEP 专属参数，避免语义污染。清理列表如下：

- `blocksPerBeat`
- `stepStartMode`
- `stepCompletionMode`
- `cameraAdaptiveStep`
- `cameraFrustumGating`
- `cameraEdgePriority`
- `usePhaseAnimation`
- `entryDurationPercent`
- `idleDurationPercent`
- `exitDurationPercent`
- `cameraNearDistance`
- `cameraFarDistance`
- `cameraNearScale`
- `cameraFarScale`

## 4. 关键行为说明

### 4.1 STEP 且启用三段式动画

当 `stepDispatch=true` 且 `usePhaseAnimation=true` 时：

- 写入 `entryDurationPercent`、`idleDurationPercent`、`exitDurationPercent`。

当 `usePhaseAnimation=false` 时：

- 立即移除上述三段比例参数，避免隐藏旧值继续生效。

### 4.2 STEP 且关闭镜头自适应

当 `cameraAdaptiveStep=false` 时：

- 自动移除 `cameraNearDistance`、`cameraFarDistance`、`cameraNearScale`、`cameraFarScale`。

保证仅在“镜头自适应开启”语义下保留这些参数。

### 4.3 切换到 BURST

无论事件之前在 STEP 下配置了多少细节，切换到 BURST 时统一清空全部 STEP 专属参数，事件恢复为纯 BURST 语义。

## 5. UI 与渲染关联

### 5.1 EventPropertiesPanel（已生效）

- STEP 下可配置：步进推进、起始/完成策略、镜头调制、三段式开关与比例。
- BURST 保存后不再保留 STEP 参数，避免下次打开时出现“残留配置误导”。

### 5.2 EventRenderer（当前状态）

- 已存在 `dispatchModel` 徽章（`S`/`B`）用于区分 STEP/BURST。
- 已存在 Frustum Gating（`V`）与 Edge Priority（`E`）相关徽章渲染逻辑。
- “三段式 phase 徽章”在当前代码中尚未引入（属于后续增强项）。

## 6. 兼容性与风险评估

### 6.1 兼容性

- 对已有 Timeline 数据是向前兼容的。
- 本次属于“参数清理增强”，不会改变 BURST 的执行路径，只会移除其不应持有的 STEP 参数。

### 6.2 风险控制

- 清理仅在用户应用编辑并保存参数时触发。
- STEP 模式下参数写入逻辑保持不变，风险主要集中在“切模式时清理正确性”。

## 7. 验证与结果

### 7.1 编译验证

已执行：

- `./gradlew compileJava`

结果：

- `BUILD SUCCESSFUL`
- 无编译错误。

### 7.2 功能性验证建议（手测）

建议按下列步骤回归：

1. 新建动画事件，切换到 STEP，开启三段式并设置三段百分比。
2. 保存后再次打开属性，确认参数回填正确。
3. 切换为 BURST 并保存。
4. 再次打开属性或检查事件参数，确认 STEP 专属字段已全部清空。
5. 反复 STEP/BURST 切换，确认不会出现旧值“复活”。

## 8. 与待办项关系

当前“完善”已完成的是参数一致性和清理闭环。以下能力在当前代码基线上仍属于下一阶段（按你的 todo）：

- 在 `BlockAnimationEngine` 中新增 entry/idle/exit 枚举与参数体系。
- 三段式动画真正的运行时分段推进逻辑。
- EventPropertiesPanel 的 phase assignment（阶段归属）编辑 UI。
- EventRenderer 的 phase 徽章展示。

换言之，本次成果解决的是“参数卫生与模式切换一致性”，为后续“真正三段执行能力”打下稳定基础。

## 9. 结论

本次完善将 STEP/BURST 的参数边界从“约定”提升为“强约束实现”：

- STEP 参数仅在 STEP 语义存在。
- BURST 模式强制清理 STEP 参数。
- 三段式相关字段已纳入清理闭环。

这显著降低了事件参数污染和 UI 回填歧义，是后续推进三段式运行时能力的必要前置修复。
