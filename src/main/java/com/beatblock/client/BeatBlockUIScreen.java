package com.beatblock.client;

import com.beatblock.client.imgui.ImGuiRenderer;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.ui.BeatBlockUIManager;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock 主界面：手持控制器右键打开。
 * 打开时禁用原版十字准星、解锁光标使用 ImGui；关闭时恢复。
 * 鼠标在面板内操作 UI，在面板外操作 Minecraft（中键按住移动视角）。
 */
public class BeatBlockUIScreen extends Screen {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockUIScreen.class);

	private BeatBlockUIManager uiManager;
	private boolean initFailed;
	private Double originalMusicVolume;
	private boolean musicMutedByBeatBlock;

	public BeatBlockUIScreen() {
		super(Text.translatable("gui.beatblock.title"));
		MinecraftClient client = MinecraftClient.getInstance();
		muteVanillaBackgroundMusic(client);
		if (client != null && client.mouse != null) {
			client.mouse.unlockCursor();
		}
	}

	@Override
	protected void init() {
		super.init();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.mouse != null) {
			client.mouse.unlockCursor();
		}
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
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// 不绘制半透明模糊背景，保持场景完全可见
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

		// 与 ChronoBlocks 一致：不调用 super.render()，避免默认 Screen 绘制（如暗角/遮罩）覆盖场景
		// ImGui 实际 GL 绘制在 RenderSystemMixin.flipFrame(HEAD) 中执行
	}

	@Override
	public void removed() {
		super.removed();
		MinecraftClient client = MinecraftClient.getInstance();
		restoreVanillaBackgroundMusic(client);
		BeatBlockInputSystem.clearCache();
		BeatBlockWorldPick.clear();
		// 仅当关闭后没有其他 Screen 时恢复锁定（回到游戏视角）
		if (client != null && client.mouse != null && client.currentScreen == null) {
			client.mouse.lockCursor();
		}
	}

	@Override
	public void close() {
		if (uiManager != null) {
			uiManager.resetLayoutState();
		}
		super.close();
	}

	/** 鼠标是否在 BeatBlock 面板内（由 ImGui 判定） */
	public static boolean isMouseOverUI() {
		return ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse();
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
			return super.mouseClicked(click, doubleClick);
		}
		// 在场景区域：交给游戏（攻击/使用）
		handleGameMouseClick(click);
		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
			return super.mouseDragged(click, deltaX, deltaY);
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		return false;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
			return super.keyPressed(input);
		}
		return false;
	}

	private void handleGameMouseClick(Click click) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.interactionManager == null || client.player == null || client.world == null) return;
		HitResult hit = BeatBlockInputSystem.pickTargetFromImGui();
		if (hit == null) return;
		if (click.button() == 0) { // left：不破坏方块，仅记录拾取（工具面板等可用）
			if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
				BeatBlockWorldPick.setLastLeftClickedBlock(blockHit.getBlockPos());
			} else if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
				client.interactionManager.attackEntity(client.player, entityHit.getEntity());
			}
		} else if (click.button() == 1) { // right
			if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
				client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND, blockHit);
			} else {
				client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	private void muteVanillaBackgroundMusic(MinecraftClient client) {
		if (client == null || client.options == null) return;
		if (musicMutedByBeatBlock) return;
		originalMusicVolume = (double) client.options.getSoundVolume(SoundCategory.MUSIC);
		client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(0.0);
		musicMutedByBeatBlock = true;
	}

	private void restoreVanillaBackgroundMusic(MinecraftClient client) {
		if (!musicMutedByBeatBlock) return;
		if (client == null || client.options == null) return;
		if (originalMusicVolume != null) {
			client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(originalMusicVolume);
		}
		musicMutedByBeatBlock = false;
	}
}
