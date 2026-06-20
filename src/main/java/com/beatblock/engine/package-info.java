/**
 * 第 3 层 — 舞台播放器。
 * <p>
 * 只消费 {@link com.beatblock.timeline.TimelineAnimationEvent} 与
 * {@link com.beatblock.engine.StageObject}，在指定时间把动作应用到世界方块。
 * 不得依赖音频分析、beatmap 或实时频段检测。
 */
package com.beatblock.engine;
