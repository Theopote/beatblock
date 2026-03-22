#!/usr/bin/env python3
"""
BeatBlock Audio Analyzer  v3.0
================================
使用 librosa 对音频文件做预处理，输出 .beatmap JSON 契约文件。
v3.0：HPSS + 谱聚类自适应轨道分配。
      对打击成分中的所有 onset 提取频谱特征，k-means 聚类自动
      决定轨道数（1~5），无需人工指定 kick/snare/hihat 三种类别。

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

ANALYZER_VERSION = "3.0.0"
SCHEMA_VERSION   = 1

# ── 进度报告（Java 端按行读取 stdout）──────────────────────────────────────

def progress(step: str, pct: int):
    """输出进度行，Java 的 ProcessBuilder 会逐行读取。"""
    print(f"PROGRESS {step} {pct}", flush=True)


def fatal(msg: str):
    print(f"ERROR {msg}", flush=True)
    sys.exit(2)


# ── 主分析流程 ───────────────────────────────────────────────────────────────

def analyze(input_path: str, output_path: str, include_waveform: bool) -> dict:
    """
    完整分析流程，返回 beatmap dict。
    每个阶段完成后输出 PROGRESS 行，Java 端据此更新进度条。
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

    # ── 3. 踩点检测（HPSS + 谱聚类，自动适配轨道数）──────────────────────
    progress("BEAT_DETECTION", 35)

    from scipy.cluster.vq import kmeans as sp_kmeans, vq

    # HPSS：谐波 / 打击分离
    y_harmonic, y_percussive = librosa.effects.hpss(y, margin=3.0)

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

    if len(all_onset_times) < 4:
        # 踩点过少无法聚类，归为单个轨道
        band_onsets = {"kick": list(all_onset_times)}
    else:
        # ── 为每个 onset 提取频谱特征 [低/中/高 能量占比] ──────────────────
        _WIN   = int(sr * 0.05)   # 50ms 分析窗
        _N_FFT = 512

        def _feat(t):
            s   = int(t * sr)
            seg = y_percussive[s : s + _WIN]
            if len(seg) < _N_FFT // 2:
                return np.array([0.33, 0.34, 0.33], dtype=np.float32)
            S  = np.abs(librosa.stft(seg, n_fft=_N_FFT, hop_length=_N_FFT // 2))
            fr = librosa.fft_frequencies(sr=sr, n_fft=_N_FFT)
            lo = float(S[fr <  250, :].sum())
            mi = float(S[(fr >= 250) & (fr < 3000), :].sum())
            hi = float(S[fr >= 3000, :].sum())
            tot = lo + mi + hi + 1e-9
            return np.array([lo / tot, mi / tot, hi / tot], dtype=np.float32)

        feat_mat = np.array([_feat(t) for t in all_onset_times])   # (N, 3)

        # 手动标准化（避免 whiten 对零方差列产生 NaN）
        std = feat_mat.std(axis=0)
        std[std < 1e-6] = 1.0
        X = (feat_mat - feat_mat.mean(axis=0)) / std

        # ── 选最优聚类数 k（1~MAX_TRACKS）用拐点法 ──────────────────────────
        MAX_TRACKS = 5
        MIN_CLUSTER_FRACTION = 0.04   # 占比不足 4% 的聚类视为噪声丢弃

        def _fit(k):
            np.random.seed(42)
            try:
                ctrs, _ = sp_kmeans(X, k, iter=25)
                lbls, _ = vq(X, ctrs)
                sse = float(np.sum((X - ctrs[lbls]) ** 2))
                return ctrs, lbls, sse
            except Exception:
                return X[:k].copy(), np.zeros(len(X), dtype=int), float('inf')

        inertias, fit_cache = [], []
        for k in range(1, MAX_TRACKS + 1):
            c, l, e = _fit(k)
            inertias.append(e)
            fit_cache.append((c, l))

        # 从 k=1 开始；新增一个 k 带来 ≥15% 的相对误差降低时接受
        best_k = 1
        for i in range(1, len(inertias)):
            prev = inertias[i - 1]
            if prev > 1e-9 and (prev - inertias[i]) / prev >= 0.15:
                best_k = i + 1
        _, labels = fit_cache[best_k - 1]

        # ── 按主频段给聚类命名（低→高排列以符合音乐习惯）──────────────────
        raw_ctr = np.array([
            feat_mat[labels == c].mean(axis=0) if np.any(labels == c) else feat_mat[0]
            for c in range(best_k)
        ])  # (best_k, 3): [low_ratio, mid_ratio, high_ratio]

        _BAND_NAMES = ["kick", "snare", "hihat"]
        order = sorted(range(best_k), key=lambda c: float(np.dot(raw_ctr[c], [0.0, 0.5, 1.0])))
        used_names, cluster_key = set(), {}
        for c in order:
            base = _BAND_NAMES[int(np.argmax(raw_ctr[c]))]
            name, sfx = base, 2
            while name in used_names:
                name, sfx = f"{base}_{sfx}", sfx + 1
            used_names.add(name)
            cluster_key[c] = name

        # ── 过滤稀疏聚类（噪声），按聚类归组 onset 时间 ────────────────────
        min_count = max(3, int(len(all_onset_times) * MIN_CLUSTER_FRACTION))
        band_onsets = {}
        for c, key in cluster_key.items():
            times = [t for t, lbl in zip(all_onset_times, labels) if lbl == c]
            if len(times) >= min_count:
                band_onsets[key] = times
        if not band_onsets:
            band_onsets = {"kick": list(all_onset_times)}

    progress("BEAT_DETECTION", 55)

    # ── 4. 计算每个踩点的能量 ────────────────────────────────────────────────
    # 用 RMS 能量包络在踩点前后 20ms 窗口取最大值
    hop_length = 512
    rms = librosa.feature.rms(y=y, hop_length=hop_length)[0]
    rms_norm = rms / (np.max(rms) + 1e-8)  # 归一化到 0~1

    def energy_at_time(t_sec: float) -> float:
        """返回某时刻的归一化能量（0~1）。"""
        frame = librosa.time_to_frames(t_sec, sr=sr, hop_length=hop_length)
        lo = max(0, frame - 2)
        hi = min(len(rms_norm) - 1, frame + 2)
        return float(np.max(rms_norm[lo:hi+1]))

    # ── 5. 构建 beats 列表 ────────────────────────────────────────────────────
    progress("BEAT_DETECTION", 65)

    # 计算小节信息：节拍序号 → 小节序号 + 拍内位置
    # beat_frames 是全部节拍帧，index 就是 beat_index
    beat_time_set = set(beat_times_ms)  # 用于快速查找是否是强拍

    beats = []
    beat_global_index = 0

    for band_name, onset_times in band_onsets.items():
        for t_sec in onset_times:
            t_ms = int(t_sec * 1000)
            if t_ms < 0 or t_ms > duration_ms:
                continue

            energy = energy_at_time(t_sec)

            # 跳过极低能量的噪声踩点
            if energy < 0.05:
                continue

            # 找最近的节拍索引
            nearest_beat_idx = int(np.argmin(np.abs(
                librosa.frames_to_time(beat_frames, sr=sr) - t_sec
            )))
            bar_index  = nearest_beat_idx // 4
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
        samples_per_sec = 100
        target_len = int(duration_ms / 1000 * samples_per_sec)
        # 降采样：把全部样本折叠到 target_len 个点，每点取 RMS
        chunk_size = max(1, len(y) // target_len)
        preview = []
        for i in range(target_len):
            chunk = y[i * chunk_size: (i + 1) * chunk_size]
            preview.append(round(float(np.sqrt(np.mean(chunk ** 2 + 1e-10))), 4))
        # 归一化
        mx = max(preview) if preview else 1.0
        preview = [round(v / mx, 4) for v in preview]
        waveform_data = {
            "samples_per_second": samples_per_sec,
            "data": preview,
        }

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
    使用 librosa 的结构分析（自相似矩阵 + 谱聚类）识别音乐段落。
    返回段落列表，每项包含 start_ms, end_ms, label, energy_mean。
    """
    try:
        # 提取 Chroma 特征（12 维音高类向量，对段落识别很有效）
        hop_length = 512
        chroma = librosa.feature.chroma_cqt(y=y, sr=sr, hop_length=hop_length)

        # 用 Laplacian 谱分割法检测段落边界
        # k=5 表示最多分 5 段（可调）
        bounds_frames = librosa.segment.agglomerative(chroma.T, k=5)
        bounds_times  = librosa.frames_to_time(bounds_frames, sr=sr, hop_length=hop_length)

        # 在首尾补边界
        boundary_ms = [0] + [int(t * 1000) for t in bounds_times] + [duration_ms]
        boundary_ms = sorted(set(boundary_ms))

        # RMS 能量包络用于 energy_mean
        rms = librosa.feature.rms(y=y, hop_length=hop_length)[0]
        rms_norm = rms / (np.max(rms) + 1e-8)

        sections = []
        for i in range(len(boundary_ms) - 1):
            start_ms = boundary_ms[i]
            end_ms   = boundary_ms[i + 1]
            if end_ms - start_ms < 1000:  # 跳过少于 1 秒的段落
                continue

            # 计算该段平均能量
            start_frame = librosa.time_to_frames(start_ms / 1000, sr=sr, hop_length=hop_length)
            end_frame   = librosa.time_to_frames(end_ms   / 1000, sr=sr, hop_length=hop_length)
            seg_rms = rms_norm[start_frame:end_frame]
            energy_mean = float(np.mean(seg_rms)) if len(seg_rms) > 0 else 0.0

            # 用能量和位置启发式打标签
            label = heuristic_label(i, len(boundary_ms) - 2, energy_mean, start_ms, duration_ms)

            sections.append({
                "start_ms":    start_ms,
                "end_ms":      end_ms,
                "label":       label,
                "energy_mean": round(energy_mean, 4),
            })

        return sections

    except Exception as e:
        # 段落识别失败不影响整体，降级为单段
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

def main():
    parser = argparse.ArgumentParser(description="BeatBlock Audio Analyzer")
    parser.add_argument("input",    help="输入音频文件路径")
    parser.add_argument("output",   help="输出 .beatmap JSON 文件路径")
    parser.add_argument("--waveform", action="store_true",
                        help="在输出中包含波形预览数据（UI 绘制用）")
    args = parser.parse_args()

    try:
        beatmap = analyze(args.input, args.output, args.waveform)
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
    summary = {
        "bpm":          beatmap["meta"]["bpm"],
        "beat_count":   len(beatmap["beats"]),
        "section_count":len(beatmap["sections"]),
        "duration_ms":  beatmap["meta"]["duration_ms"],
    }
    print(f"RESULT {json.dumps(summary)}", flush=True)


if __name__ == "__main__":
    main()
