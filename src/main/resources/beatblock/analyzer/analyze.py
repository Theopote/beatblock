#!/usr/bin/env python3
"""
BeatBlock Audio Analyzer  v2.0
================================
使用 librosa 对音频文件做预处理，输出 .beatmap JSON 契约文件。
v2.0：改用 HPSS（谐波-打击成分分离）+ 感知子频带拆分，
      輸出 band 字段为 "kick" / "snare" / "hihat"。

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

ANALYZER_VERSION = "2.0.0"
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

    # ── 3. 踩点检测（HPSS 感知分离 + 子频带拆分）─────────────────────────
    progress("BEAT_DETECTION", 35)

    # HPSS：将音频分为谐波成分（旋律/和弦）与打击成分（鼓/打击乐）
    # margin 参数影响分离质量；默认值 1 已足够
    y_harmonic, y_percussive = librosa.effects.hpss(y, margin=3.0)

    # 对打击成分做子频带拆分，分离 kick / snare / hihat
    from scipy.signal import butter, sosfilt

    def lowpass(signal, hi_hz, sample_rate):
        nyq = sample_rate / 2.0
        hi  = min(hi_hz / nyq, 0.999)
        sos = butter(4, hi, btype="low", output="sos")
        return sosfilt(sos, signal)

    def bandpass(signal, lo_hz, hi_hz, sample_rate):
        nyq = sample_rate / 2.0
        lo  = lo_hz / nyq
        hi  = min(hi_hz / nyq, 0.999)
        sos = butter(4, [lo, hi], btype="band", output="sos")
        return sosfilt(sos, signal)

    def highpass(signal, lo_hz, sample_rate):
        nyq = sample_rate / 2.0
        lo  = lo_hz / nyq
        sos = butter(4, lo, btype="high", output="sos")
        return sosfilt(sos, signal)

    # kick：打击成分的低频段（20~200 Hz），对应底鼓
    # snare：打击成分的中频段（200~3000 Hz），对应军鼓/拍手
    # hihat：打击成分的高频段（3000~16000 Hz），对应踩镲/碎音
    bands = {
        "kick":  lowpass(y_percussive, 200,          sr),
        "snare": bandpass(y_percussive, 200,  3000,  sr),
        "hihat": highpass(y_percussive, 3000,         sr),
    }

    # 每个感知频带的 onset 检测参数（kick 间隔更长，hihat 更密）
    onset_params = {
        "kick":  {"delta": 0.06, "wait_ms": 100, "backtrack": True},
        "snare": {"delta": 0.07, "wait_ms": 80,  "backtrack": True},
        "hihat": {"delta": 0.05, "wait_ms": 40,  "backtrack": False},
    }

    band_onsets = {}
    for band_name, band_signal in bands.items():
        params = onset_params[band_name]
        onset_frames = librosa.onset.onset_detect(
            y=band_signal,
            sr=sr,
            units="frames",
            backtrack=params["backtrack"],
            delta=params["delta"],
            wait=int(sr / 1000 * params["wait_ms"]),
        )
        band_onsets[band_name] = librosa.frames_to_time(onset_frames, sr=sr)

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

            # anchor 规则：
            # kick（底鼓）→ arrive（重音对应落地冲击）
            # snare（军鼓）→ arrive（卡拍）
            # hihat（踩镲）→ depart（轻触起飞）
            anchor = "depart" if band_name == "hihat" else "arrive"

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
