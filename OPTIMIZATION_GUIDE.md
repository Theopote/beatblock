# BeatBlock 代码优化指南

本文档提供具体的代码优化建议和实施步骤。  
**最近更新**: 2026-06-27 — 见下方 [优化进度快照](#优化进度快照2026-06-27)。

---

## 优化进度快照（2026-06-27）

| 类别 | 状态 | 说明 |
|------|------|------|
| P0 测试失败 | ✅ 已完成 | `CurveLibraryTest` 与 `AnimationLibrary` 动态对齐 |
| P0 空异常捕获 | ✅ 已完成 | 全项目 `catch (... ignored)` 已替换为特定异常 + 日志 |
| P0 静态字段 @Deprecated | ✅ 已完成 | `BeatBlock` 10 个可变静态字段已标记 `forRemoval` |
| P0 静态状态迁移 | ✅ 已完成 | 已删除 10 个 legacy 静态字段与 `fromLegacyStatics`；`getContext()` 未初始化时抛 `IllegalStateException` |
| P1 JSpecify 依赖 | ✅ 已完成 | `build.gradle` 引入 `org.jspecify:jspecify:1.0.0` |
| P1 空值注解（核心 API） | 🟡 进行中 | timeline/audio 约 **22** 个文件；见 [已注解文件清单](#已注解文件清单) |
| P1 Timeline 并发模型 | ✅ 已完成 | 类文档明确线程模型；`animationCachesDirty` 改为 `volatile` |
| P1 资源管理 | ✅ 已完成 | `AudioAnalysisOrchestrator` + `AudioConversionService`：`AutoCloseable`、非 daemon、优雅 shutdown |
| P2 Map 强类型化 | ⬜ 未开始 | |
| P2 长方法拆分 | ⬜ 未开始 | |
| P2 Magic Numbers | ⬜ 未开始 | |
| P3 文档/命名/覆盖率 | ⬜ 未开始 | |
| CI NullAway | 🟡 已配置 | 可选：`./gradlew compileJava -PenableNullaway=true`（需下载 errorprone/nullaway） |

**测试基线**: `./gradlew test` — **749/749 通过**（2026-06-27）

### 已注解文件清单

**runtime**
- `BeatBlockContext`（`package-info.java` 标记 `@NullMarked`）

**timeline**
- `Track`, `Clip`, `TimelineEvent`, `Timeline`, `TimelineMarker`, `TimelineAnimationEvent`
- `TimelineEditor`, `TimelineOperations`
- `command/*`（`CommandManager`, `MergeableCommand`, 全部 Command 实现）
- `editing/AnimationEventSnapshot`, `editing/ClipDragStateSnapshot`
- `binding/AnimationBindingEngine`（部分）

**audio**
- `IAudioAnalyzer`, `AudioAnalysisService`, `AudioAnalysisOrchestrator`
- `AudioLoader`, `MusicPlayer`, `StemMixer`, `AudioConversionService`
- `AnalysisProgressCallback`, `process/AnalyzerProcessIo`
- `beatmap/Beatmap`

---

## 1. 移除全局静态状态

### 当前问题
`BeatBlock.java` 包含大量 `public static` 可变字段，导致测试困难、线程不安全。

### 优化方案
完全移除静态字段，强制使用 `BeatBlockContext` 依赖注入。

### 迁移步骤
1. ✅ 标记所有静态字段为 `@Deprecated`
2. ✅ 逐个模块迁移到 Context 访问（生产路径已用 `BeatBlock.getContext()`）
3. ✅ 运行测试确保功能正常
4. ✅ 完全移除静态字段 — 2026-06-27 删除 deprecated 字段与 `fromLegacyStatics`

---

## 2. 添加空值安全注解

### 建议
引入 JSpecify 注解，为所有公共 API 添加 `@Nullable` / `@NonNull` 标注。

优先级：公共API > 内部API > 私有方法

### 进度
- ✅ 依赖与 `BeatBlockContext`、timeline/audio 核心模型
- 🟡 UI / engine / client 包待扩展
- 🟡 NullAway：`build.gradle` 已配置，通过 `-PenableNullaway=true` 启用；当前仅 `com.beatblock.runtime` 为 `@NullMarked`

---

## 3. 修复并发控制

### 方案A: 单线程访问（推荐）— ✅ 已采纳
`Timeline` 文档已说明：主线程编辑 + metadata 异步写入；`ConcurrentHashMap` 仅用于 metadata。

### 方案B: 多线程访问
若未来多人服或后台线程大量写 Timeline，再考虑 `CopyOnWriteArrayList` 或同步块。

---

## 4. 改进异常处理 — ✅ 已完成

### 原则
1. ✅ 捕获特定异常类型而非 `Exception`
2. ✅ 记录日志而非静默忽略（热路径用 `trace`）
3. 性能敏感场景避免异常 — 持续遵守

---

## 5. 替换 Map<String, Object>

### 方案A: 参数对象
使用 record 定义强类型参数对象。

### 方案B: Sealed 接口
适合多种参数类型的场景。

**状态**: ⬜ 未开始（P2）

---

## 6. 重构长方法

拆分原则：
- 单一职责
- 5-20行为宜
- 有意义的命名

**状态**: ⬜ 未开始（P2）

---

## 7. 资源管理 — ✅ 核心路径已完成

### 最佳实践
1. ✅ `AudioAnalysisOrchestrator` 实现 `AutoCloseable`
2. ✅ 客户端 `CLIENT_STOPPING` 关闭 timelineEditor / analyzer / conversionService
3. ✅ 分析线程非 daemon + `awaitTermination`
4. ✅ `AudioConversionService` 非 daemon + `awaitTermination` + `AutoCloseable`

---

## 8. 提取 Magic Numbers

为所有魔数添加命名常量和文档注释。

**状态**: ⬜ 未开始（P2）

---

## 9. 改进测试覆盖率

优先级：
1. 核心业务逻辑
2. 并发代码
3. 错误处理路径

**状态**: ⬜ 未开始（P3）；JaCoCo 与 Java 21 仍有兼容性警告

---

## 10. 性能优化

策略：
1. 使用缓存减少重复计算
2. 增量更新而非全量重建
3. 大对象使用不可变快照

**状态**: ⬜ 未开始（P3）

---

## 实施检查清单

### 第1周 (P0)
- [x] 修复测试失败
- [x] 修复空异常捕获
- [x] 标记静态字段为 `@Deprecated`
- [x] 业务代码迁移到 `BeatBlockContext`（生产路径）
- [x] 删除 legacy 静态字段

### 第2-3周 (P1)
- [x] 引入 JSpecify 依赖
- [x] 核心 timeline/audio 公共 API 空值注解
- [x] Timeline 线程模型文档 + `volatile` 脏标记
- [x] `AudioAnalysisOrchestrator` 资源关闭
- [x] `AudioConversionService` 关闭语义对齐
- [ ] 扩展注解至 ui/engine/client 包
- [ ] 全量启用 NullAway（当前：`-PenableNullaway=true` 可选，仅 `runtime` 包 `@NullMarked`）

### 第4-6周 (P2)
- [ ] 替换 `Map<String,Object>`
- [ ] 重构长方法
- [ ] 提取常量

### 长期 (P3)
- [ ] 统一文档语言
- [ ] 改进命名
- [ ] 提高覆盖率

---

## 相关文档

| 文档 | 用途 |
|------|------|
| [REVIEW_SUMMARY.md](REVIEW_SUMMARY.md) | 审查结论与路线图（含完成标记） |
| [CODE_REVIEW_REPORT.md](CODE_REVIEW_REPORT.md) | 详细问题分析与代码示例 |
| [docs/REFACTOR_ROADMAP.md](docs/REFACTOR_ROADMAP.md) | 架构重构阶段（与本文互补） |

详细的代码示例请参考 **CODE_REVIEW_REPORT.md**。
