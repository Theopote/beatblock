package com.beatblock.client.imgui;

import com.beatblock.timeline.rendering.TimelineLayout;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

/**
 * ImGui 多语言字体：优先系统字体（CJK + 西里尔等），可选内置资源，最后回退默认字体。
 * 目标：支持英文、中文、日韩、西里尔等，方便国际玩家。
 */
public final class ImGuiFontManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiFontManager.class);
	private static final float FONT_SIZE = 16.0f;

	/** 纯图标按钮用字号，与轨道行高一致，使字形高度接近方形按钮边长。 */
	public static final float ICON_BUTTON_FONT_PX = TimelineLayout.ROW_HEIGHT;

	/** 独立烘焙的 BeatBlock 图标字体（仅私用区），供方形图标按钮 {@code pushFont}；可能为 null。 */
	private static ImFont iconButtonFont;

	/** 系统字体路径：Windows / macOS / Linux 常见 CJK + 多语言字体 */
	private static final String[] SYSTEM_FONT_PATHS = {
		// Windows
		"C:/Windows/Fonts/msyh.ttc",
		"C:/Windows/Fonts/msyhbd.ttc",
		"C:/Windows/Fonts/simhei.ttf",
		"C:/Windows/Fonts/simsun.ttc",
		"C:/Windows/Fonts/meiryo.ttc",
		"C:/Windows/Fonts/malgun.ttf",
		// macOS
		"/System/Library/Fonts/PingFang.ttc",
		"/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
		"/Library/Fonts/Arial Unicode.ttf",
		// Linux
		"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
		"/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
	};

	/** 模组内置字体（可选）：放入 assets/beatblock/fonts/ 即可生效 */
	private static final String BUNDLED_FONT_PATH = "/assets/beatblock/fonts/NotoSansSC-Regular.ttf";

	/** 模组内置图标字体（替代 emoji）：放入 assets/beatblock/fonts/ 即可生效 */
	private static final String ICON_FONT_PATH = "/assets/beatblock/fonts/BeatBlock.ttf";

	/** 图标字体私用区范围（BeatBlock.ttf）：U+F000 ~ U+F500 */
	private static final short[] ICON_GLYPH_RANGES = { (short) 0xF000, (short) 0xF500, 0 };

	/**
	 * 初始化多语言字体：合并 Default + 中文常用字 + 标点/假名小块，避免图集过大导致部分字显示为问号。
	 */
	public static void initializeFonts(ImGuiIO io) {
		ImFontAtlas atlas = io.getFonts();
		try {
			atlas.clear();
		} catch (Throwable t) {
			try { atlas.clearFonts(); } catch (Throwable e) {
				LOGGER.debug("ImGui font atlas clearFonts failed during reset", e);
			}
			try { atlas.clearTexData(); } catch (Throwable e) {
				LOGGER.debug("ImGui font atlas clearTexData failed during reset", e);
			}
		}
		iconButtonFont = null;

		short[] glyphRanges = buildMultiLanguageGlyphRanges(io);
		ImFontConfig config = new ImFontConfig();
		config.setPixelSnapH(true);
		config.setOversampleH(2);
		config.setOversampleV(2);

		config.setGlyphRanges(glyphRanges);
		// 1) 优先系统字体
		boolean loaded = tryLoadSystemFonts(io, config);
		// 2) 再试内置资源
		if (!loaded) {
			loaded = tryLoadBundledFont(io, config, glyphRanges);
		}
		// 3) 回退默认（仅拉丁等）
		if (!loaded) {
			LOGGER.warn("[BeatBlock] No CJK system/bundled font found; UI will show ? for Chinese etc.");
			atlas.addFontDefault();
		}

		// 关键：合并自定义图标字体，避免依赖系统 emoji。
		tryLoadIconFontMerged(atlas);
		tryLoadIconButtonFontStandalone(atlas);

		if (!atlas.isBuilt()) {
			atlas.build();
		}
		config.destroy();
		LOGGER.info("[BeatBlock] ImGui fonts initialized (multi-language support)");
	}

	/** 供 {@link com.beatblock.ui.imgui.IconButtonStyle} 使用；未加载图标文件时为 null。 */
	public static ImFont getIconButtonFont() {
		return iconButtonFont;
	}

	/**
	 * 使用「常用字」范围而非「全量」，避免请求字形过多导致字体图集溢出，未烘焙的字显示为 ?。
	 * 图集纹理有上限（如 2048×2048），ChineseFull + 整块 0x4E00-0x9FFF 会超出，只补标点+addText。
	 */
	private static short[] buildMultiLanguageGlyphRanges(ImGuiIO io) {
		ImFontAtlas a = io.getFonts();
		try {
			imgui.ImFontGlyphRangesBuilder builder = new imgui.ImFontGlyphRangesBuilder();
			builder.addRanges(a.getGlyphRangesDefault());
			// 用常用字范围，保证能全部进图集，减少问号
			try {
				builder.addRanges(a.getGlyphRangesChineseSimplifiedCommon());
			} catch (Throwable ignored) {
				builder.addRanges(a.getGlyphRangesChineseFull());
			}
			// 只补标点/假名等小块，不补整块 0x4E00-0x9FFF（已在 Common/Full 里，再补会重复且撑爆图集）
			builder.addRanges(CJK_PUNCT_AND_KANA);
			// 强制包含模组 UI 用字，避免漏字
			// 注意：这里的字符会直接参与 ImGui 字形图集烘焙；缺字会在 UI 中显示为 '?'。
			builder.addText(
				"工具时间线事件属性动画库导入音乐智能映射设置确定取消打开保存新建编辑删除复制粘贴撤销重做播放暂停频段低频中频高频" +
				// 时间线轨道折叠/展开提示（Tooltip/文案）
				"展开子轨道折叠子轨道" +
				// 兼容：如果 UI 实际文案是“子选项”而非“子轨道”
				"展开子选项折叠子选项" +
				// 音频解析面板：详情区折叠/展开（按钮 Tooltip）
				"展开详情面板折叠详情展开详情" +
				// 时间线轨道类型列 + 默认轨道名
				"音频波形低频中频高频方块自动摄像机"
			);
			tryAddRanges(builder, a, "getGlyphRangesJapanese");
			tryAddRanges(builder, a, "getGlyphRangesKorean");
			tryAddRanges(builder, a, "getGlyphRangesCyrillic");
			tryAddRanges(builder, a, "getGlyphRangesThai");
			tryAddRanges(builder, a, "getGlyphRangesVietnamese");
			return builder.buildRanges();
		} catch (Throwable t) {
			LOGGER.debug("[BeatBlock] GlyphRangesBuilder fallback");
			return buildFallbackRanges(a);
		}
	}

	/** 仅标点与假名等小块，不包含整块汉字（避免图集过大）。 */
	private static final short[] CJK_PUNCT_AND_KANA = {
		(short) 0x2000, (short) 0x206F,  // 通用标点
		(short) 0x3000, (short) 0x303F,  // CJK 符号与标点（、。「」…—）
		(short) 0x3040, (short) 0x309F,  // 平假名
		(short) 0x30A0, (short) 0x30FF,  // 片假名
		(short) 0xFF00, (short) 0xFFEF,  // 全角
		0
	};

	private static short[] buildFallbackRanges(ImFontAtlas a) {
		try {
			imgui.ImFontGlyphRangesBuilder b = new imgui.ImFontGlyphRangesBuilder();
			b.addRanges(a.getGlyphRangesDefault());
			try {
				b.addRanges(a.getGlyphRangesChineseSimplifiedCommon());
			} catch (Throwable ignored) {
				b.addRanges(a.getGlyphRangesChineseFull());
			}
			b.addRanges(CJK_PUNCT_AND_KANA);
			return b.buildRanges();
		} catch (Throwable t2) {
			return a.getGlyphRangesChineseFull();
		}
	}

	private static void tryAddRanges(imgui.ImFontGlyphRangesBuilder builder, ImFontAtlas atlas, String methodName) {
		try {
			java.lang.reflect.Method m = atlas.getClass().getMethod(methodName);
			short[] ranges = (short[]) m.invoke(atlas);
			if (ranges != null && ranges.length > 0) {
				builder.addRanges(ranges);
			}
		} catch (Throwable e) {
			LOGGER.trace("ImFontAtlas.{} unavailable", methodName, e);
		}
	}

	private static boolean tryLoadSystemFonts(ImGuiIO io, ImFontConfig config) {
		for (String path : SYSTEM_FONT_PATHS) {
			File f = new File(path);
			if (!f.exists() || !f.canRead()) continue;
			try {
				io.getFonts().addFontFromFileTTF(path, FONT_SIZE, config);
				LOGGER.info("[BeatBlock] Loaded system font: {}", path);
				return true;
			} catch (Throwable e) {
				LOGGER.trace("[BeatBlock] Skip font {}: {}", path, e.getMessage());
			}
		}
		return false;
	}

	private static boolean tryLoadBundledFont(ImGuiIO io, ImFontConfig config, short[] glyphRanges) {
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(BUNDLED_FONT_PATH)) {
			if (in == null) return false;
			byte[] data = in.readAllBytes();
			if (data.length == 0) return false;
			io.getFonts().addFontFromMemoryTTF(data, FONT_SIZE, config, glyphRanges);
			LOGGER.info("[BeatBlock] Loaded bundled font: {} ({} bytes)", BUNDLED_FONT_PATH, data.length);
			return true;
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] No bundled font: {}", e.getMessage());
			return false;
		}
	}

	private static boolean tryLoadIconFontMerged(ImFontAtlas atlas) {
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(ICON_FONT_PATH)) {
			if (in == null) {
				LOGGER.debug("[BeatBlock] No icon font found: {}", ICON_FONT_PATH);
				return false;
			}
			byte[] data = in.readAllBytes();
			if (data.length == 0) return false;

			ImFontConfig iconConfig = new ImFontConfig();
			// MergeMode=true：把图标 glyph 合并到当前已加载的默认字体中（与 chronoblocks 的做法一致）。
			iconConfig.setMergeMode(true);
			iconConfig.setPixelSnapH(true);
			iconConfig.setOversampleH(2);
			iconConfig.setOversampleV(2);

			atlas.addFontFromMemoryTTF(data, FONT_SIZE, iconConfig, ICON_GLYPH_RANGES);
			iconConfig.destroy();
			LOGGER.info("[BeatBlock] Loaded icon font (merged): {} ({} bytes)", ICON_FONT_PATH, data.length);
			return true;
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] Load icon font failed: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * 第二份 BeatBlock.ttf：不合并，字号与轨道行高一致，专供图标按钮在 {@code pushFont} 后尽量铺满方形区域。
	 * 与合并加载分开读字节，避免 atlas 接管第一份 buffer 后引用失效。
	 */
	private static void tryLoadIconButtonFontStandalone(ImFontAtlas atlas) {
		iconButtonFont = null;
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(ICON_FONT_PATH)) {
			if (in == null) return;
			byte[] data = in.readAllBytes();
			if (data.length == 0) return;

			ImFontConfig cfg = new ImFontConfig();
			cfg.setMergeMode(false);
			cfg.setPixelSnapH(true);
			cfg.setOversampleH(2);
			cfg.setOversampleV(2);

			iconButtonFont = atlas.addFontFromMemoryTTF(data, ICON_BUTTON_FONT_PX, cfg, ICON_GLYPH_RANGES);
			cfg.destroy();
			LOGGER.info("[BeatBlock] Loaded icon button font: {} px={}", ICON_FONT_PATH, ICON_BUTTON_FONT_PX);
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] Icon button font skipped: {}", e.getMessage());
		}
	}
}
