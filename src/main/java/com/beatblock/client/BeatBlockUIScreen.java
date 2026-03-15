package com.beatblock.client;

import com.beatblock.client.imgui.ImGuiRenderer;
import com.beatblock.ui.BeatBlockUIManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock 主界面：手持控制器右键打开。
 * 使用 ImGui Dockspace 布局：顶部菜单栏、左侧工具、右侧事件属性、底部时间线、中间场景；动画库可菜单开关。
 */
public class BeatBlockUIScreen extends Screen {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockUIScreen.class);

	private BeatBlockUIManager uiManager;
	private boolean initFailed;

	public BeatBlockUIScreen() {
		super(Text.translatable("gui.beatblock.title"));
	}

	@Override
	protected void init() {
		super.init();
		if (uiManager != null) return;

		if (!ImGuiRenderer.getInstance().isInitialized()) {
			LOGGER.warn("BeatBlock: ImGui 未初始化，尝试初始化");
			try {
				ImGuiRenderer.getInstance().init();
			} catch (Exception e) {
				LOGGER.error("BeatBlock: ImGui 初始化失败", e);
				initFailed = true;
				return;
			}
		}

		uiManager = new BeatBlockUIManager(this::close);
		LOGGER.info("BeatBlock UI 已打开（Dockspace 布局）");
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (initFailed) {
			super.render(context, mouseX, mouseY, delta);
			context.drawCenteredTextWithShadow(textRenderer, "ImGui 初始化失败", width / 2, height / 2 - 20, 0xFF6666);
			return;
		}
		if (uiManager == null) {
			super.render(context, mouseX, mouseY, delta);
			return;
		}

		ImGuiRenderer renderer = ImGuiRenderer.getInstance();
		renderer.updateDisplaySize();
		renderer.beginFrame();
		try {
			uiManager.render();
		} catch (Exception e) {
			LOGGER.error("BeatBlock UI 渲染异常", e);
		}
		renderer.endFrame();

		// 实际 GL 绘制在 RenderSystemMixin.flipFrame(HEAD) 中执行
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		if (uiManager != null) {
			uiManager.resetLayoutState();
		}
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
