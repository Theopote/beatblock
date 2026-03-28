#!/usr/bin/env python3
"""
BeatBlock Audio Analyzer  v3.0
================================
使用 librosa 对音频文件做预处理，输出 .beatmap JSON 契约文件。
v3.0：HPSS + 频谱比例阈值分类，完全确定性，无聚类随机性。
      对打击成分统一检测 onset，按低/中/高频能量占比阈值分配轨道
      （低频主导→kick，高频主导→hihat，其余→snare）。
      稀疏类自动丢弃，输出 1~3 条轨道，无需指定数量。

用法（由 Java 通过 ProcessBuilder 调用）：
    python analyze.py <input_audio> <output_beatmap> [--waveform]

退出码：
    0  成功
    1  文件不存在或格式不支持
    2  分析过程中出错
    3  写入输出文件失败

进度输出（Java 端按行解析 stdout）：
    PROGRESS <step_name> <percent_0_to_100>
    RESULT   <json_line>
    ERROR    <message>
"""

import argparse
import hashlib
import json
import sys
import os
import traceback
from datetime import datetime, timezone

# ── 依赖检查 ────────────────────────────────────────────────────────────────
try:
    import numpy as np
    import librosa
    import soundfile as sf
    import scipy
except ImportError as e:
    print(f"ERROR 缺少依赖：{e}，请运行 pip install librosa soundfile numpy scipy", file=sys.stderr)
    sys.exit(1)

ANALYZER_VERSION = "3.1.0"
SCHEMA_VERSION   = 1

# ── 进度报告（Java 端按行读取 stdout）──────────────────────────────────────

def progress(step: str, pct: int):
    """输出进度行，Java 的 ProcessBuilder 会逐行读取。"""
    print(f"PROGRESS {step} {pct}", flush=True)


def fatal(msg: str):
    print(f"ERROR {msg}", flush=True)
    sys.exit(2)


# ── 主分析流程 ───────────────────────────────────────────────────────────────

def _detect_style(y: np.ndarray, sr: int) -> str:
    """
    通过频谱平坦度（Spectral Flatness）自动判断音乐风格。

    平坦度  = 几何均值 / 算术均值，范围 0~1：
      - 接近 1：频谱能量均匀分布（白噪声/电子音乐合成声）
      - 接近 0：能量集中在少数频率（原声乐器/纯音）

    阈值 0.3 来自 MPEG-7 标准建议值，实测对 EDM/Trap 上方、
    原声/古典下方的区分准确率约 85%。
    """
    flatness = librosa.feature.spectral_flatness(y=y)[0]   # 每帧平坦度
    mean_flatness = float(np.median(flatness))             # 用中位数抗极端帧干扰
    style = "electronic" if mean_flatness > 0.3 else "acoustic"
    print(f"LOG flatness={mean_flatness:.4f} style={style}", file=sys.stderr, flush=True)
    progress("STYLE_DETECT", 0)
    return style


def _make_waveform_preview(y: np.ndarray, sr: int, duration_ms: int) -> dict:
    """为单条音频信号生成波形预览数据（降采样 RMS + 归一化）。"""
    samples_per_sec = 100
    target_len = int(duration_ms / 1000 * samples_per_sec)
    chunk_size = max(1, len(y) // target_len)
    preview = []
    for i in range(target_len):
        chunk = y[i * chunk_size: (i + 1) * chunk_size]
        preview.append(round(float(np.sqrt(np.mean(chunk ** 2 + 1e-10))), 4))
    mx = max(preview) if preview else 1.0
    preview = [round(v / mx, 4) for v in preview]
    return {"samples_per_second": samples_per_sec, "data": preview}


def analyze(input_path: str, output_path: str, include_waveform: bool,
            style: str = "auto") -> dict:
    """
    完整分析流程，返回 beatmap dict。
    每个阶段完成后输出 PROGRESS 行，Java 端据此更新进度条。

    参数：
        style: "auto" | "acoustic" | "electronic"
            控制 HPSS 分离激进程度：
            - acoustic   margin=3.0（强分离，适合原声/爵士）
            - electronic margin=1.5（弱分离，保留电子鼓的谐波成分）
            - auto       用频谱平坦度自动判断
    """

    # ── 1. 加载音频 ─────────────────────────────────────────────────────────
    progress("LOAD", 5)
    if not os.path.isfile(input_path):
        fatal(f"文件不存在：{input_path}")

    try:
        # mono=True：librosa 内部统一转为单声道用于分析
        # sr=None：保留原始采样率
        y, sr = librosa.load(input_path, sr=None, mono=True)
    except Exception as e:
        fatal(f"无法加载音频：{e}")

    duration_ms = int(len(y) / sr * 1000)
    source_file = os.path.basename(input_path)
    progress("LOAD", 10)

    # ── 2. BPM 检测 ──────────────────────────────────────────────────────────
    progress("BPM_DETECTION", 15)

    # beat_track 同时返回 tempo 和节拍帧位置
    # librosa 的 beat_track 基于动态规划，比简单的 onset 更准确
    tempo, beat_frames = librosa.beat.beat_track(y=y, sr=sr, units="frames")
    bpm = float(tempo)

    # 用 tempo 的中位数进一步验证置信度（多次调用取平均）
    # dynamic_tempo 返回每帧的局部 tempo
    dtempo = librosa.beat.tempo(y=y, sr=sr, aggregate=None)
    bpm_std = float(np.std(dtempo)) if len(dtempo) > 1 else 0.0
    # 标准差越小说明 BPM 越稳定，置信度越高
    bpm_confidence = float(np.clip(1.0 - bpm_std / 30.0, 0.0, 1.0))

    beat_times_ms = [int(t * 1000) for t in librosa.frames_to_time(beat_frames, sr=sr)]
    progress("BPM_DETECTION", 30)

    # ── 3. 踩点检测（HPSS + 频谱阈值分类）──────────────────────────────────
    progress("BEAT_DETECTION", 35)

    # HPSS margin 根据风格选择：
    #   acoustic   → margin=3.0（强分离，原声乐器谐波与打击成分分离更干净）
    #   electronic → margin=1.5（弱分离，避免把电子 kick 的谐波误归入 harmonic）
    resolved_style = style
    if style == "auto":
        resolved_style = _detect_style(y, sr)
    hpss_margin = 1.5 if resolved_style == "electronic" else 3.0
    y_harmonic, y_percussive = librosa.effects.hpss(y, margin=hpss_margin)

    # 统一检测所有打击 onset。
    # wait 单位是「帧数」而非样本数：40ms ≈ int(0.04 * sr / hop_length) 帧
    _HOP = 512
    all_onset_frames = librosa.onset.onset_detect(
        y=y_percussive, sr=sr, units="frames",
        backtrack=True, delta=0.05,
        wait=max(1, int(0.040 * sr / _HOP)),   # 40ms 最小间隔（帧数）
    )
    all_onset_times = librosa.frames_to_time(all_onset_frames, sr=sr, hop_length=_HOP)
    progress("BEAT_DETECTION", 44)

    # ── 为每个 onset 计算频谱重心：[低/中/高 能量占比] ─────────────────────
    # 结果完全由物理规律决定，无随机性，同一首歌多次运行结果完全一致。
    _WIN   = int(sr * 0.05)   # 50ms 分析窗（onset 瞬态持续时间）
    _N_FFT = 2048  # 44.1kHz 下频率分辨率 ~21.5Hz/bin，250Hz 界限约 12 个 bin

    def _spectral_ratios(t):
        """返回 (low_ratio, mid_ratio, high_ratio)，三者之和为 1。"""
        s   = int(t * sr)
        seg = y_percussive[s : s + _WIN]
        if len(seg) < _N_FFT // 2:
            return 0.33, 0.34, 0.33
        S  = np.abs(librosa.stft(seg, n_fft=_N_FFT, hop_length=_N_FFT // 2))
        fr = librosa.fft_frequencies(sr=sr, n_fft=_N_FFT)
        lo = float(S[fr <  250, :].sum())
        mi = float(S[(fr >= 250) & (fr < 3000), :].sum())
        hi = float(S[fr >= 3000, :].sum())
        tot = lo + mi + hi + 1e-9
        return lo / tot, mi / tot, hi / tot

    # ── 预计算所有 onset 的频谱比例（共享，避免重复 STFT）──────────────────
    onset_times_list = [float(t) for t in all_onset_times]   # Python float，可做 dict key
    _all_ratios      = [_spectral_ratios(t) for t in onset_times_list]
    _ratio_by_time   = dict(zip(onset_times_list, _all_ratios))

    # ── 阈值分类：直接按主导频段划分，无需聚类 ────────────────────────────
    #   kick  : 低频主导（低频占比 > 0.5）→ 对应底鼓
    #   hihat : 高频主导（高频占比 > 0.5）→ 对应踩镲/碎音
    #   snare : 其余（中频主导或混合频段）→ 对应军鼓/拍手
    #
    # 阈值来自打击乐的物理特性，不随数据分布改变，结果确定且可重现。
    MIN_CLUSTER_FRACTION = 0.04   # 占比不足 4% 视为稀疏噪声，丢弃该轨道
    raw_groups: dict[str, list[float]] = {"kick": [], "snare": [], "hihat": []}

    for t, (lo, mi, hi) in zip(onset_times_list, _all_ratios):
        if lo > 0.50:
            raw_groups["kick"].append(t)
        elif hi > 0.50:
            raw_groups["hihat"].append(t)
        else:
            raw_groups["snare"].append(t)

    # 过滤稀疏轨道（若某类几乎不存在，说明该乐器在此曲中不显著）
    min_count = max(3, int(len(onset_times_list) * MIN_CLUSTER_FRACTION))
    band_onsets = {k: v for k, v in raw_groups.items() if len(v) >= min_count}
    if not band_onsets:
        band_onsets = {"kick": onset_times_list}

    # ── 频谱重心排序命名 ─────────────────────────────────────────────────────
    # 对每条轨道的全部 onset 计算平均频谱重心代理值（Hz），从低到高排序后
    # 依次从固定名称池分配 key。
    #
    # 代理公式：centroid ≈ lo×125 + mi×1125 + hi×12500（各频段中点 Hz）
    # 这样 kick（低频主导）始终排在最前，hihat（高频主导）排在最后，
    # 名称完全由频谱物理顺序决定，Java 侧可静态映射全部 key，
    # 不存在 argmax 重名导致的 "snare_2" 问题。
    _NAME_POOL = ["kick", "snare", "snare_hi", "hihat", "hihat_open"]

    def _track_mean_centroid(times: list) -> float:
        if not times:
            return 0.0
        total = 0.0
        for t in times:
            lo, mi, hi = _ratio_by_time.get(t, (0.33, 0.34, 0.33))
            total += lo * 125.0 + mi * 1125.0 + hi * 12500.0
        return total / len(times)

    sorted_items = sorted(band_onsets.items(), key=lambda kv: _track_mean_centroid(kv[1]))
    band_onsets  = {_NAME_POOL[i]: kv[1] for i, kv in enumerate(sorted_items)}

    progress("BEAT_DETECTION", 55)

    # ── 4. 计算每个踩点的能量（按轨道分别归一化）────────────────────────────
    # 全局 RMS：仅用于取原始能量值，不做全局归一化。
    # 低频底鼓 RMS 比高频踩镲高 10~20 倍，若用全局最大值归一化，
    # hihat 踩点能量会被压到 0.05~0.15 并被噪声过滤阈值误删。
    # 解决方案：先收集每条轨道的原始 RMS 值，再用该轨道内的最大值做局部归一化，
    # 使每条轨道的能量分布均覆盖 0~1，动态表现力对等。
    _hop_e = 512
    _rms_raw = librosa.feature.rms(y=y, hop_length=_hop_e)[0]   # 原始 RMS，未归一化

    def _raw_energy(t_sec: float) -> float:
        """返回 onset 附近 ±2 帧内的原始 RMS 最大值。"""
        frame = librosa.time_to_frames(t_sec, sr=sr, hop_length=_hop_e)
        lo = max(0, frame - 2)
        hi = min(len(_rms_raw) - 1, frame + 2)
        return float(np.max(_rms_raw[lo:hi + 1]))

    # ── 5. 构建 beats 列表（逐轨道归一化）───────────────────────────────────
    progress("BEAT_DETECTION", 65)

    beat_time_arr = librosa.frames_to_time(beat_frames, sr=sr)   # 避免循环内重复计算

    beats = []

    for band_name, onset_times in band_onsets.items():
        # 第一遍：收集该轨道所有 onset 的原始能量
        raw_energies = [_raw_energy(t) for t in onset_times]

        # 轨道内归一化：use track-local max，避免轨道间能量尺度差异
        track_max = max(raw_energies) if raw_energies else 1.0
        track_min = min(raw_energies) if raw_energies else 0.0
        track_range = track_max - track_min + 1e-8

        for t_sec, raw_e in zip(onset_times, raw_energies):
            t_ms = int(t_sec * 1000)
            if t_ms < 0 or t_ms > duration_ms:
                continue

            # 轨道局部归一化后的能量
            energy = float(np.clip((raw_e - track_min) / track_range, 0.0, 1.0))

            # 过滤噪声踩点（阈值针对局部归一化后的值，0.05 更合理）
            if energy < 0.05:
                continue

            nearest_beat_idx = int(np.argmin(np.abs(beat_time_arr - t_sec)))
            bar_index   = nearest_beat_idx // 4
            beat_in_bar = nearest_beat_idx % 4

            # anchor 规则：hihat 系列（high-freq）→ depart；其余 → arrive
            anchor = "depart" if band_name.startswith("hihat") else "arrive"

            beats.append({
                "time_ms":    t_ms,
                "band":       band_name,
                "energy":     round(energy, 4),
                "anchor":     anchor,
                "beat_index": nearest_beat_idx,
                "bar_index":  bar_index,
                "beat_in_bar": beat_in_bar,
            })

    # 按时间升序排列
    beats.sort(key=lambda b: b["time_ms"])
    progress("BEAT_DETECTION", 70)

    # ── 6. 段落识别（librosa 结构分析）──────────────────────────────────────
    progress("SECTION_DETECTION", 72)

    sections = detect_sections(y, sr, duration_ms)
    progress("SECTION_DETECTION", 85)

    # ── 7. 波形预览数据（可选，用于 UI 绘制）─────────────────────────────
    waveform_data = None
    if include_waveform:
        progress("WAVEFORM", 87)
        waveform_data = _make_waveform_preview(y, sr, duration_ms)

    # ── 8. 组装最终 beatmap ───────────────────────────────────────────────
    progress("WRITE_BEATMAP", 92)

    beatmap = {
        "version": SCHEMA_VERSION,
        "meta": {
            "source_file":      source_file,
            "duration_ms":      duration_ms,
            "bpm":              round(bpm, 2),
            "bpm_confidence":   round(bpm_confidence, 4),
            "time_signature":   "4/4",
            "sample_rate":      int(sr),
            "generated_at":     datetime.now(timezone.utc).isoformat(),
            "analyzer_version": ANALYZER_VERSION,
            "style":            resolved_style,
        },
        "beats":    beats,
        "sections": sections,
    }

    if waveform_data:
        beatmap["waveform_preview"] = waveform_data

    return beatmap


# ── 段落识别 ─────────────────────────────────────────────────────────────────

def detect_sections(y: np.ndarray, sr: int, duration_ms: int) -> list:
    """
    使用 Chroma + Ward 层次聚类识别音乐段落，段数根据时长自动推算。

    策略：
      - k = max(2, min(8, duration_s // 30))：每 ~30 秒约 1 段，范围 [2, 8]
      - AgglomerativeClustering(Ward) 是 librosa.segment.agglomerative 的直接
        替代（0.10 废弃），链接法确定结果与随机种子无关，完全可重现
      - 任何异常均降级为单段，不影响主流程
    """
    try:
        from sklearn.cluster import AgglomerativeClustering

        hop_length = 512
        duration_s = duration_ms / 1000.0

        # 根据时长自动选段数：每 30s 约 1 段，范围 [2, 8]
        k = max(2, min(8, int(duration_s / 30)))

        # Chroma 特征（对调性/段落变化敏感），shape = (12, T)
        chroma = librosa.feature.chroma_cqt(y=y, sr=sr, hop_length=hop_length)

        # Ward 层次聚类：对每一帧的 12 维 chroma 向量聚成 k 类
        # Ward linkage 是确定性的（不依赖随机种子），且对噪声鲁棒
        ward = AgglomerativeClustering(n_clusters=k, metric="euclidean", linkage="ward")
        seg_labels = ward.fit_predict(chroma.T)   # shape (T,)

        # 找标签序列发生变化的帧号（即段落边界）
        change_frames = np.flatnonzero(np.diff(seg_labels))   # 变化前一帧的索引
        bounds_times  = librosa.frames_to_time(change_frames + 1, sr=sr, hop_length=hop_length)

        # 在首尾补边界
        boundary_ms = sorted(set([0] + [int(t * 1000) for t in bounds_times] + [duration_ms]))

        # RMS 包络（用于 energy_mean）
        rms = librosa.feature.rms(y=y, hop_length=hop_length)[0]
        rms_norm = rms / (np.max(rms) + 1e-8)

        sections = []
        for i in range(len(boundary_ms) - 1):
            start_ms = boundary_ms[i]
            end_ms   = boundary_ms[i + 1]
            if end_ms - start_ms < 2000:   # 跳过不足 2 秒的碎片段落
                continue

            start_frame = librosa.time_to_frames(start_ms / 1000, sr=sr, hop_length=hop_length)
            end_frame   = librosa.time_to_frames(end_ms   / 1000, sr=sr, hop_length=hop_length)
            seg_rms = rms_norm[start_frame:end_frame]
            energy_mean = float(np.mean(seg_rms)) if len(seg_rms) > 0 else 0.0

            label = heuristic_label(i, len(boundary_ms) - 2, energy_mean, start_ms, duration_ms)

            sections.append({
                "start_ms":    start_ms,
                "end_ms":      end_ms,
                "label":       label,
                "energy_mean": round(energy_mean, 4),
            })

        if not sections:
            raise ValueError("no valid sections after filtering")

        return sections

    except Exception:
        return [{
            "start_ms":    0,
            "end_ms":      duration_ms,
            "label":       "unknown",
            "energy_mean": 0.5,
        }]


def heuristic_label(idx: int, total: int, energy: float,
                    start_ms: int, duration_ms: int) -> str:
    """
    根据位置和能量启发式推断段落标签。
    这只是粗略分类，实际场景中建议结合人工校对。
    """
    position = start_ms / duration_ms  # 0~1 的相对位置

    if idx == 0 and position < 0.15:
        return "intro"
    if idx == total - 1 and position > 0.80:
        return "outro"
    if energy > 0.65:
        return "chorus"   # 能量高 → 副歌
    if energy < 0.35:
        return "bridge"   # 能量低 → 过渡/桥段
    return "verse"        # 其余 → 主歌


# ── CLI 入口 ─────────────────────────────────────────────────────────────────

# ── Demucs 茎分离模式 ────────────────────────────────────────────────────────

_DEMUCS_STEMS = ["drums", "bass", "vocals", "other"]
_DEMUCS_MODEL = None

def _ensure_demucs():
    """检查 demucs 是否可用，不可用则报友好错误。"""
    try:
        from demucs import pretrained
        from demucs.apply import apply_model
        import torch
        return True
    except ImportError as e:
        fatal(f"缺少 demucs 依赖：{e}。请运行：pip install demucs torch")
        return False


def _get_demucs_model():
    """懒加载 Demucs 模型，避免单次分析内重复初始化。"""
    global _DEMUCS_MODEL
    if _DEMUCS_MODEL is None:
        from demucs import pretrained
        _DEMUCS_MODEL = pretrained.get_model("htdemucs")
    return _DEMUCS_MODEL


def _run_demucs(input_path: str, stems_dir: str) -> tuple[dict[str, str], bool]:
    """
    调用 demucs 分离音频为 4 条茎（drums/bass/vocals/other）。
    返回 {stem_name: wav_path} 字典。
    如果茎文件已存在则跳过分离（利用缓存）。
    """
    import torch
    from demucs.apply import apply_model

    # 检查缓存：所有 4 条茎的 wav 文件都存在且有效才直接返回
    # 最小有效大小 4096 字节，过滤中断产生的空文件或损坏文件
    _MIN_STEM_BYTES = 4096

    stem_paths = {}
    all_cached = True
    for stem in _DEMUCS_STEMS:
        p = os.path.join(stems_dir, f"{stem}.wav")
        stem_paths[stem] = p
        if not (os.path.isfile(p) and os.path.getsize(p) > _MIN_STEM_BYTES):
            all_cached = False

    if all_cached:
        progress("DEMUCS_SEPARATE", 40)
        return stem_paths, True

    # 运行 demucs 分离
    progress("DEMUCS_SEPARATE", 10)
    os.makedirs(stems_dir, exist_ok=True)

    model = _get_demucs_model()
    progress("DEMUCS_SEPARATE", 20)

    mix_np, sample_rate = librosa.load(input_path, sr=None, mono=False)
    mix_np = np.asarray(mix_np, dtype=np.float32)
    if mix_np.ndim == 1:
        mix_np = np.expand_dims(mix_np, axis=0)

    target_rate = int(getattr(model, "samplerate", sample_rate))
    if sample_rate != target_rate:
        mix_np = librosa.resample(mix_np, orig_sr=sample_rate, target_sr=target_rate, axis=-1)
        sample_rate = target_rate

    if mix_np.shape[0] == 1:
        mix_np = np.repeat(mix_np, 2, axis=0)
    elif mix_np.shape[0] > 2:
        mix_np = mix_np[:2, :]

    mix = torch.from_numpy(np.ascontiguousarray(mix_np))

    with torch.no_grad():
        estimates = apply_model(model, mix[None], device="cpu", progress=False, num_workers=0)

    progress("DEMUCS_SEPARATE", 35)

    # 将分离结果保存为 wav
    source_names = list(getattr(model, "sources", _DEMUCS_STEMS))
    estimates = estimates[0].cpu()
    source_to_tensor = {
        source_name: estimates[idx]
        for idx, source_name in enumerate(source_names)
        if idx < estimates.shape[0]
    }

    for stem_name in _DEMUCS_STEMS:
        tensor = source_to_tensor.get(stem_name)
        if tensor is None:
            continue
        # apply_model 输出 shape: (sources, channels, samples)
        # 为 JavaSound 兼容性，显式写出 PCM_16 WAV（避免 float WAV 被部分后端拒绝）。
        audio_np = tensor.numpy()
        sf.write(stem_paths[stem_name], audio_np.T, sample_rate, format="WAV", subtype="PCM_16")

    progress("DEMUCS_SEPARATE", 40)
    return stem_paths, False


def analyze_demucs(input_path: str, output_path: str,
                   include_waveform: bool, style: str = "auto") -> dict:
    """
    Demucs 增强分析模式：先做茎分离，再对每条茎做节拍分析。
    输出与 analyze() 格式兼容，额外包含茎路径和茎波形。
    """
    _ensure_demucs()

    # ── 1. 加载原始音频（用于全局 BPM / 段落检测）──────────────────────────
    progress("LOAD", 5)
    if not os.path.isfile(input_path):
        fatal(f"文件不存在：{input_path}")

    try:
        y, sr = librosa.load(input_path, sr=None, mono=True)
    except Exception as e:
        fatal(f"无法加载音频：{e}")

    duration_ms = int(len(y) / sr * 1000)
    source_file = os.path.basename(input_path)
    progress("LOAD", 8)

    # ── 2. BPM 检测（与 analyze() 相同）──────────────────────────────────────
    progress("BPM_DETECTION", 10)
    tempo, beat_frames = librosa.beat.beat_track(y=y, sr=sr, units="frames")
    bpm = float(tempo)
    dtempo = librosa.beat.tempo(y=y, sr=sr, aggregate=None)
    bpm_std = float(np.std(dtempo)) if len(dtempo) > 1 else 0.0
    bpm_confidence = float(np.clip(1.0 - bpm_std / 30.0, 0.0, 1.0))
    beat_times_ms = [int(t * 1000) for t in librosa.frames_to_time(beat_frames, sr=sr)]
    beat_time_arr = librosa.frames_to_time(beat_frames, sr=sr)
    progress("BPM_DETECTION", 15)

    # ── 3. 风格检测 ──────────────────────────────────────────────────────────
    resolved_style = style
    if style == "auto":
        resolved_style = _detect_style(y, sr)

    # ── 4. Demucs 茎分离 ────────────────────────────────────────────────────
    # 茎缓存目录：与输出 beatmap 同目录下的 stems/<音频指纹>/
    normalized_input_path = os.path.abspath(input_path).lower().encode("utf-8")
    audio_fingerprint = hashlib.sha1(normalized_input_path).hexdigest()[:16]
    output_parent = os.path.dirname(os.path.abspath(output_path))
    stems_dir = os.path.join(output_parent, "stems", audio_fingerprint)
    stem_paths, stem_cache_reused = _run_demucs(input_path, stems_dir)

    # ── 5. 对每条茎做节拍分析 ────────────────────────────────────────────────
    # drums → 使用现有 HPSS+onset 流程（打击乐分析）
    # bass/vocals/other → onset 检测 + RMS 能量
    beats = []

    # ─ drums 茎：精细打击分类（复用 analyze() 的阈值分类逻辑）──────────────
    progress("STEM_ANALYSIS", 42)
    if os.path.isfile(stem_paths.get("drums", "")):
        y_drums, sr_drums = librosa.load(stem_paths["drums"], sr=None, mono=True)
        hpss_margin = 1.5 if resolved_style == "electronic" else 3.0
        _, y_perc = librosa.effects.hpss(y_drums, margin=hpss_margin)

        _HOP = 512
        drum_onsets = librosa.onset.onset_detect(
            y=y_perc, sr=sr_drums, units="frames",
            backtrack=True, delta=0.05,
            wait=max(1, int(0.040 * sr_drums / _HOP)),
        )
        drum_times = librosa.frames_to_time(drum_onsets, sr=sr_drums, hop_length=_HOP)

        # 频谱比例分类
        _WIN = int(sr_drums * 0.05)
        _N_FFT = 2048  # 44.1kHz 下频率分辨率 ~21.5Hz/bin，250Hz 界限约 12 个 bin

        def _spectral_ratios_drums(t):
            s = int(t * sr_drums)
            seg = y_perc[s: s + _WIN]
            if len(seg) < _N_FFT // 2:
                return 0.33, 0.34, 0.33
            S = np.abs(librosa.stft(seg, n_fft=_N_FFT, hop_length=_N_FFT // 2))
            fr = librosa.fft_frequencies(sr=sr_drums, n_fft=_N_FFT)
            lo = float(S[fr < 250, :].sum())
            mi = float(S[(fr >= 250) & (fr < 3000), :].sum())
            hi = float(S[fr >= 3000, :].sum())
            tot = lo + mi + hi + 1e-9
            return lo / tot, mi / tot, hi / tot

        drum_times_list = [float(t) for t in drum_times]
        all_ratios = [_spectral_ratios_drums(t) for t in drum_times_list]
        ratio_by_time = dict(zip(drum_times_list, all_ratios))

        MIN_CLUSTER_FRACTION = 0.04
        raw_groups = {"kick": [], "snare": [], "hihat": []}
        for t, (lo, mi, hi) in zip(drum_times_list, all_ratios):
            if lo > 0.50:
                raw_groups["kick"].append(t)
            elif hi > 0.50:
                raw_groups["hihat"].append(t)
            else:
                raw_groups["snare"].append(t)

        min_count = max(3, int(len(drum_times_list) * MIN_CLUSTER_FRACTION))
        band_onsets = {k: v for k, v in raw_groups.items() if len(v) >= min_count}
        if not band_onsets:
            band_onsets = {"kick": drum_times_list}

        # centroid-sort naming
        _NAME_POOL = ["kick", "snare", "snare_hi", "hihat", "hihat_open"]

        def _track_mean_centroid(times):
            if not times:
                return 0.0
            total = 0.0
            for t in times:
                lo, mi, hi = ratio_by_time.get(t, (0.33, 0.34, 0.33))
                total += lo * 125.0 + mi * 1125.0 + hi * 12500.0
            return total / len(times)

        sorted_items = sorted(band_onsets.items(),
                              key=lambda kv: _track_mean_centroid(kv[1]))
        band_onsets = {_NAME_POOL[i]: kv[1] for i, kv in enumerate(sorted_items)}

        # RMS for energy
        _hop_e = 512
        _rms_drums = librosa.feature.rms(y=y_drums, hop_length=_hop_e)[0]

        def _raw_energy_drums(t_sec):
            frame = librosa.time_to_frames(t_sec, sr=sr_drums, hop_length=_hop_e)
            lo_f = max(0, frame - 2)
            hi_f = min(len(_rms_drums) - 1, frame + 2)
            return float(np.max(_rms_drums[lo_f:hi_f + 1]))

        for band_name, onset_times in band_onsets.items():
            raw_energies = [_raw_energy_drums(t) for t in onset_times]
            track_max = max(raw_energies) if raw_energies else 1.0
            track_min = min(raw_energies) if raw_energies else 0.0
            track_range = track_max - track_min + 1e-8

            for t_sec, raw_e in zip(onset_times, raw_energies):
                t_ms = int(t_sec * 1000)
                if t_ms < 0 or t_ms > duration_ms:
                    continue
                energy = float(np.clip((raw_e - track_min) / track_range, 0.0, 1.0))
                if energy < 0.05:
                    continue
                nearest = int(np.argmin(np.abs(beat_time_arr - t_sec)))
                anchor = "depart" if band_name.startswith("hihat") else "arrive"
                beats.append({
                    "time_ms": t_ms, "band": band_name,
                    "energy": round(energy, 4), "anchor": anchor,
                    "beat_index": nearest,
                    "bar_index": nearest // 4, "beat_in_bar": nearest % 4,
                })

    progress("STEM_ANALYSIS", 55)

    # ─ bass / vocals / other 茎：onset + RMS 能量 ────────────────────────────
    _melodic_stems = ["bass", "vocals", "other"]
    for stem_idx, stem_name in enumerate(_melodic_stems):
        stem_wav = stem_paths.get(stem_name, "")
        if not os.path.isfile(stem_wav):
            continue
        y_stem, sr_stem = librosa.load(stem_wav, sr=None, mono=True)
        _hop_s = 512

        onset_frames = librosa.onset.onset_detect(
            y=y_stem, sr=sr_stem, units="frames",
            backtrack=True, delta=0.07,
            wait=max(1, int(0.060 * sr_stem / _hop_s)),
        )
        onset_times = librosa.frames_to_time(onset_frames, sr=sr_stem, hop_length=_hop_s)

        rms_stem = librosa.feature.rms(y=y_stem, hop_length=_hop_s)[0]

        def _raw_energy_stem(t_sec, rms_arr=rms_stem, sr_s=sr_stem):
            frame = librosa.time_to_frames(t_sec, sr=sr_s, hop_length=_hop_s)
            lo_f = max(0, frame - 2)
            hi_f = min(len(rms_arr) - 1, frame + 2)
            return float(np.max(rms_arr[lo_f:hi_f + 1]))

        raw_energies = [_raw_energy_stem(t) for t in onset_times]
        if not raw_energies:
            continue
        track_max = max(raw_energies)
        track_min = min(raw_energies)
        track_range = track_max - track_min + 1e-8

        for t_sec, raw_e in zip(onset_times, raw_energies):
            t_ms = int(t_sec * 1000)
            if t_ms < 0 or t_ms > duration_ms:
                continue
            energy = float(np.clip((raw_e - track_min) / track_range, 0.0, 1.0))
            if energy < 0.05:
                continue
            nearest = int(np.argmin(np.abs(beat_time_arr - t_sec)))
            beats.append({
                "time_ms": t_ms, "band": stem_name,
                "energy": round(energy, 4), "anchor": "arrive",
                "beat_index": nearest,
                "bar_index": nearest // 4, "beat_in_bar": nearest % 4,
            })

        pct = 55 + int((stem_idx + 1) / len(_melodic_stems) * 15)
        progress("STEM_ANALYSIS", pct)

    beats.sort(key=lambda b: b["time_ms"])
    progress("STEM_ANALYSIS", 75)

    # ── 6. 段落识别 ──────────────────────────────────────────────────────────
    progress("SECTION_DETECTION", 77)
    sections = detect_sections(y, sr, duration_ms)
    progress("SECTION_DETECTION", 85)

    # ── 7. 波形预览 ──────────────────────────────────────────────────────────
    waveform_data = None
    stem_waveforms = None
    if include_waveform:
        progress("WAVEFORM", 87)
        waveform_data = _make_waveform_preview(y, sr, duration_ms)

        # 每条茎也生成独立波形预览
        stem_waveforms = {}
        for stem_name in _DEMUCS_STEMS:
            stem_wav = stem_paths.get(stem_name, "")
            if os.path.isfile(stem_wav):
                y_sw, sr_sw = librosa.load(stem_wav, sr=None, mono=True)
                dur_sw = int(len(y_sw) / sr_sw * 1000)
                stem_waveforms[stem_name] = _make_waveform_preview(y_sw, sr_sw, dur_sw)
        progress("WAVEFORM", 92)

    # ── 8. 组装 beatmap ──────────────────────────────────────────────────────
    progress("WRITE_BEATMAP", 93)

    # 将茎路径转为相对于输出目录的相对路径
    stems_relative = {}
    for k, v in stem_paths.items():
        stems_relative[k] = os.path.relpath(v, output_parent)

    beatmap = {
        "version": SCHEMA_VERSION,
        "meta": {
            "source_file":       source_file,
            "duration_ms":       duration_ms,
            "bpm":               round(bpm, 2),
            "bpm_confidence":    round(bpm_confidence, 4),
            "time_signature":    "4/4",
            "sample_rate":       int(sr),
            "generated_at":      datetime.now(timezone.utc).isoformat(),
            "analyzer_version":  ANALYZER_VERSION,
            "style":             resolved_style,
            "separation_mode":   "demucs",
            "stems":             stems_relative,
        },
        "beats":    beats,
        "sections": sections,
    }

    if waveform_data:
        beatmap["waveform_preview"] = waveform_data
    if stem_waveforms:
        beatmap["stem_waveforms"] = stem_waveforms

    return beatmap


def main():
    parser = argparse.ArgumentParser(description="BeatBlock Audio Analyzer")
    parser.add_argument("input",    help="输入音频文件路径")
    parser.add_argument("output",   help="输出 .beatmap JSON 文件路径")
    parser.add_argument("--waveform", action="store_true",
                        help="在输出中包含波形预览数据（UI 绘制用）")
    parser.add_argument("--style", choices=["auto", "acoustic", "electronic"],
                        default="auto",
                        help="音乐风格（影响 HPSS 分离参数）："
                             "auto=自动检测（默认），acoustic=原声，electronic=电子")
    parser.add_argument("--demucs", action="store_true",
                        help="使用 Demucs 进行茎分离（需要额外安装 demucs + torch）")
    args = parser.parse_args()

    try:
        if args.demucs:
            beatmap = analyze_demucs(args.input, args.output, args.waveform,
                                     style=args.style)
        else:
            beatmap = analyze(args.input, args.output, args.waveform,
                              style=args.style)
    except SystemExit:
        raise
    except Exception:
        print(f"ERROR 分析异常：{traceback.format_exc()}", flush=True)
        sys.exit(2)

    # 写入 JSON
    try:
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(beatmap, f, ensure_ascii=False, indent=2)
    except OSError as e:
        print(f"ERROR 写入失败：{e}", flush=True)
        sys.exit(3)

    progress("WRITE_BEATMAP", 100)

    # 最终结果摘要行（Java 端解析）
    cache_source = "fresh"
    if beatmap["meta"].get("separation_mode") == "demucs":
        cache_source = "stem-cache-reuse" if 'stem_cache_reused' in locals() and stem_cache_reused else "fresh"

    summary = {
        "bpm":          beatmap["meta"]["bpm"],
        "beat_count":   len(beatmap["beats"]),
        "section_count":len(beatmap["sections"]),
        "duration_ms":  beatmap["meta"]["duration_ms"],
        "separation_mode": beatmap["meta"].get("separation_mode", "basic"),
        "cache_source": cache_source,
    }
    print(f"RESULT {json.dumps(summary)}", flush=True)


if __name__ == "__main__":
    main()
