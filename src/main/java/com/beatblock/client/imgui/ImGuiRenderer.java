package com.beatblock.client.imgui;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock ImGui 渲染器：初始化、帧生命周期、最终绘制。
 * 参考 ChronoBlocks ImGuiRenderer；在 RenderSystemMixin.flipFrame(HEAD) 中调用 renderPendingDrawData()。
 */
public class ImGuiRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderer.class);
	private static volatile ImGuiRenderer INSTANCE;

	private ImGuiImplGlfw imGuiGlfw;
	private ImGuiImplGl3 imGuiGl3;
	private long windowHandle;
	private boolean initialized;
	private boolean frameInProgress;
	private boolean drawDataReady;

	private ImGuiRenderer() {}

	public static ImGuiRenderer getInstance() {
		if (INSTANCE == null) {
			synchronized (ImGuiRenderer.class) {
				if (INSTANCE == null) INSTANCE = new ImGuiRenderer();
			}
		}
		return INSTANCE;
	}

	public void init() {
		if (initialized) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			throw new RuntimeException("Minecraft client or window is null");
		}
		windowHandle = client.getWindow().getHandle();
		if (windowHandle == 0) throw new RuntimeException("Invalid window handle");

		ImGui.createContext();
		ImGuiIO io = ImGui.getIO();
		int configFlags = ImGuiConfigFlags.NavEnableKeyboard | ImGuiConfigFlags.DockingEnable;
		io.setConfigFlags(configFlags);
		io.getFonts().addFontDefault();
		io.getFonts().build();
		io.setIniFilename("config/beatblock/imgui.ini");

		ImGui.styleColorsDark();
		resetPixelStoreState();
		String glsl = pickGlslVersion();
		imGuiGlfw = new ImGuiImplGlfw();
		if (!imGuiGlfw.init(windowHandle, true)) {
			throw new RuntimeException("ImGui GLFW init failed");
		}
		imGuiGl3 = new ImGuiImplGl3();
		imGuiGl3.init(glsl);

		// 参考 ChronoBlocks：首帧前必须设置显示尺寸，否则坐标/裁剪错误导致“全黑无内容”
		updateDisplaySize();
		initialized = true;
		LOGGER.info("BeatBlock ImGui 初始化完成");
	}

	private static void resetPixelStoreState() {
		RenderSystem.assertOnRenderThread();
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
	}

	/** 参考 ChronoBlocks：GLSL 版本过高在部分环境会导致 shader 编译失败，界面全黑。 */
	private static String pickGlslVersion() {
		try {
			String sl = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
			if (sl != null) {
				String[] parts = sl.trim().split("[^0-9.]+");
				if (parts.length > 0) {
					String[] v = parts[0].split("\\.");
					int major = v.length >= 1 ? Integer.parseInt(v[0]) : 0;
					int minor = v.length >= 2 ? Integer.parseInt(v[1]) : 0;
					if (major <= 1) return "#version 150";
					if (major == 3) return "#version 330 core";
					if (major >= 4) return (major > 4 || minor >= 10) ? "#version 410 core" : "#version 330 core";
				}
			}
		} catch (Throwable ignored) {}
		return "#version 150";
	}

	public void updateDisplaySize() {
		if (!initialized) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) return;
		Window window = client.getWindow();
		long h = window.getHandle();
		if (h != windowHandle) {
			windowHandle = h;
			if (imGuiGlfw != null) {
				imGuiGlfw.dispose();
				imGuiGlfw = new ImGuiImplGlfw();
				imGuiGlfw.init(windowHandle, true);
			}
		}
		int w = Math.max(1, window.getWidth());
		int h2 = Math.max(1, window.getHeight());
		int fw = Math.max(1, window.getFramebufferWidth());
		int fh = Math.max(1, window.getFramebufferHeight());
		ImGuiIO io = ImGui.getIO();
		io.setDisplaySize(w, h2);
		io.setDisplayFramebufferScale((float) fw / w, (float) fh / h2);
	}

	public void beginFrame() {
		if (!initialized || frameInProgress) return;
		try {
			imGuiGlfw.newFrame();
			ImGui.newFrame();
			frameInProgress = true;
		} catch (Exception e) {
			LOGGER.error("ImGui beginFrame failed", e);
			frameInProgress = false;
		}
	}

	public void endFrame() {
		if (!initialized || !frameInProgress) return;
		try {
			ImGui.render();
			drawDataReady = true;
		} catch (Exception e) {
			LOGGER.error("ImGui endFrame failed", e);
		} finally {
			frameInProgress = false;
		}
	}

	/** 在 swap 前由 Mixin 调用；参考 ChronoBlocks 保存/恢复 viewport 与 scissor。 */
	public void renderPendingDrawData() {
		if (!initialized || !drawDataReady || imGuiGl3 == null) return;
		try {
			RenderSystem.assertOnRenderThread();
			int[] vp = new int[4];
			GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
			boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			imGuiGl3.renderDrawData(ImGui.getDrawData());
			GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
			if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
		} catch (Exception e) {
			LOGGER.error("ImGui renderDrawData failed", e);
		} finally {
			drawDataReady = false;
		}
	}

	public boolean hasPendingDrawData() {
		return drawDataReady;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void dispose() {
		if (!initialized) return;
		if (frameInProgress) endFrame();
		if (imGuiGl3 != null) { imGuiGl3.dispose(); imGuiGl3 = null; }
		if (imGuiGlfw != null) { imGuiGlfw.dispose(); imGuiGlfw = null; }
		ImGui.destroyContext();
		initialized = false;
		LOGGER.info("BeatBlock ImGui disposed");
	}
}
