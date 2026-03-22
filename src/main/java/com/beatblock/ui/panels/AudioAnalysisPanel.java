package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.ui.icons.Icons;
import com.beatblock.client.imgui.ImGuiFontManager;
import com.beatblock.client.imgui.ImGuiRenderer;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.audio.beatmap.Beatmap;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 音频解析面板 / 媒体箱
 *
 * <p>布局：左侧列表 | 右侧详情（可折叠）</p>
 *
 * <p>左侧：拖放区 + 资产列表（每项自适应高度）</p>
 * <p>右侧：选中资产的元信息、解析步骤进度、频段分布、拖拽源按钮</p>
 *
 * <h3>修复的问题</h3>
 * <ul>
 *   <li>列表项高度改为自适应，解析中状态不再溢出</li>
 *   <li>点击检测移到 beginChild 内部，使用 isWindowHovered + isMouseClicked</li>
 *   <li>进度条使用 ImGui.progressBar，附带百分比文字</li>
 *   <li>状态颜色改为 ImGui ABGR 格式（addCircleFilled 要求）</li>
 *   <li>拖放区增加 acceptDragDropPayload 接收系统文件路径</li>
 *   <li>预览区增加信息层次：标题用普通文字，数据用彩色标注</li>
 *   <li>解析中的 asset 行高由内容决定，不再写死 80px</li>
 *   <li>全部解析、清除完成的底栏按钮</li>
 * </ul>
 */
public final class AudioAnalysisPanel {

    // ── 颜色常量（ImGui DrawList 格式：0xAABBGGRR）──────────────────────────
    private static final int COLOR_DOT_DONE     = 0xFF57C45D; // 绿
    private static final int COLOR_DOT_QUEUED   = 0xFF3AB6F5; // 橙黄
    private static final int COLOR_DOT_ANALYZING= 0xFF27C4CA; // 青
    private static final int COLOR_DOT_FAILED   = 0xFF4A4BE2; // 红（ABGR）
    private static final int COLOR_DOT_PENDING  = 0xFF888888; // 灰

    // 进度条前景色（ImVec4 方式推送）
    private static final ImVec4 COLOR_PROGRESS_BG  = new ImVec4(0.18f, 0.18f, 0.22f, 1f);
    private static final ImVec4 COLOR_PROGRESS_FG  = new ImVec4(0.50f, 0.47f, 0.87f, 1f); // 紫

    // 选中行背景
    private static final ImVec4 COLOR_SELECTED_BG  = new ImVec4(0.22f, 0.20f, 0.32f, 1f);
    private static final ImVec4 COLOR_HOVER_BG     = new ImVec4(0.18f, 0.17f, 0.24f, 1f);

    // 频段颜色（文字）
    private static final ImVec4 COLOR_LOW  = new ImVec4(0.50f, 0.47f, 0.87f, 1f); // 紫
    private static final ImVec4 COLOR_MID  = new ImVec4(0.36f, 0.79f, 0.65f, 1f); // 青绿
    private static final ImVec4 COLOR_HIGH = new ImVec4(0.94f, 0.62f, 0.16f, 1f); // 琥珀

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

    /** 与轨道行高一致，图标按钮方形边长 */
    private static final float ICON_BTN = TimelineLayout.ROW_HEIGHT;
    private static final float MIN_LIST_PANEL_WIDTH = 96f;
    private static final float MIN_DETAIL_PANEL_WIDTH = 96f;

    // ── 状态字段 ────────────────────────────────────────────────────────────
    private final ImString importPath = new ImString(512);
    private AudioAsset selectedAsset;
    private boolean detailExpanded = true;
    private float detailRatio = 0.40f;
	private String panelHintText;
	private boolean panelHintError;
	private long panelHintExpireAtMs;
    private final ImBoolean demucsToggle = new ImBoolean(false);

    // ── 公共入口 ─────────────────────────────────────────────────────────────

    public void render() {
        if (!ImGui.begin("音频解析###AudioAnalysisPanel", WINDOW_FLAGS)) {
            ImGui.end();
            return;
        }

        renderToolbar();
		renderPythonRuntimeHint();

        List<AudioAsset> assets = AudioAssetManager.getInstance().getAssets();

        float totalW = ImGui.getContentRegionAvailX();
        float totalH = ImGui.getContentRegionAvailY() - 32f; // 为底栏留空间
        float splitterW = detailExpanded ? 3f : 0f;

        float detailW = 0f;
        float listW = totalW;
        if (detailExpanded) {
            float minRatio = MIN_DETAIL_PANEL_WIDTH / Math.max(1f, totalW);
            float maxRatio = (totalW - MIN_LIST_PANEL_WIDTH - splitterW) / Math.max(1f, totalW);
            if (maxRatio < minRatio) {
                minRatio = 0.5f;
                maxRatio = 0.5f;
            }
            detailRatio = clamp(detailRatio, minRatio, maxRatio);
            detailW = totalW * detailRatio;
            listW = totalW - detailW - splitterW;
        }

        // ── 左侧：列表 ──────────────────────────────────────────────────────
        ImGui.beginChild("##AudioList", listW, totalH, false);
        renderDropZone();
        ImGui.spacing();
        renderAssetList(assets);
        ImGui.endChild();

        // ── 右侧：详情 ──────────────────────────────────────────────────────
        ImGui.sameLine(0f, 0f);
        if (detailExpanded) {
            ImGui.invisibleButton("##detail_splitter", splitterW, totalH);
            if (ImGui.isItemHovered() || ImGui.isItemActive()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("左右拖动调整比例");
            }
            if (ImGui.isItemActive()) {
                float deltaRatio = ImGui.getIO().getMouseDeltaX() / Math.max(1f, totalW);
                float minRatio = MIN_DETAIL_PANEL_WIDTH / Math.max(1f, totalW);
                float maxRatio = (totalW - MIN_LIST_PANEL_WIDTH - splitterW) / Math.max(1f, totalW);
                detailRatio = clamp(detailRatio - deltaRatio, minRatio, maxRatio);
                float listPercent = (1f - detailRatio) * 100f;
                float detailPercent = detailRatio * 100f;
                ImGui.setTooltip(String.format("%.0f : %.0f", listPercent, detailPercent));
            }

            float splitX = ImGui.getItemRectMinX() + splitterW * 0.5f;
            float splitY0 = ImGui.getItemRectMinY() + 4f;
            float splitY1 = ImGui.getItemRectMaxY() - 4f;
            int lineColor = ImGui.isItemActive() ? 0xFFB9B0FF : (ImGui.isItemHovered() ? 0xFF9A90E8 : 0xFF6B6488);
            ImGui.getWindowDrawList().addLine(splitX, splitY0, splitX, splitY1, lineColor, 2f);

            ImGui.sameLine(0f, 0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4f);
            ImGui.beginChild("##AudioDetail", detailW, totalH, true);
            ImGui.popStyleVar();
            renderDetailPanel(selectedAsset);
            ImGui.endChild();
        } else {
            IconButtonStyle.pushBeatBlockIconButton();
            if (ImGui.button(Icons.Layout.RIGHT_EXPAND + "##expand", ICON_BTN, ICON_BTN)) {
                detailExpanded = true;
            }
            IconButtonStyle.popBeatBlockIconButton();
            if (ImGui.isItemHovered()) ImGui.setTooltip("展开详情面板");
        }

        // ── 底栏：批量操作 ──────────────────────────────────────────────────
        ImGui.separator();
        renderFooter(assets);

        ImGui.end();
    }

    // ── 工具栏（+ 按钮 / 弹窗）────────────────────────────────────────────

    private void renderToolbar() {
        // 与轨道同高的方形槽 + BeatBlock 大字重：添加用 icon-bb-add，避免 ASCII “+” 仍走 16px 主字体显小
        IconButtonStyle.pushBeatBlockIconButton();
        if (ImGui.button(iconLabel(Icons.Action.ADD, "+") + "##AddAudio", ICON_BTN, ICON_BTN)) {
            importPath.set("");
            ImGui.openPopup("##AddAudioPopup");
        }
        if (ImGui.isItemHovered()) setTooltipWithDefaultFont("选择音频文件");

        ImGui.sameLine();

        String detailIcon = detailExpanded ? Icons.Layout.LEFT_COLLAPSE : Icons.Layout.RIGHT_EXPAND;
        String detailFallback = detailExpanded ? "<" : ">";
        if (ImGui.button(iconLabel(detailIcon, detailFallback) + "##detail", ICON_BTN, ICON_BTN)) {
            detailExpanded = !detailExpanded;
        }
        IconButtonStyle.popBeatBlockIconButton();
        if (ImGui.isItemHovered()) ImGui.setTooltip(detailExpanded ? "折叠详情" : "展开详情");

        // Demucs 茎分离开关
        if (BeatBlock.externalAudioAnalyzer != null) {
            ImGui.sameLine();
            ImGui.spacing();
            ImGui.sameLine();
            demucsToggle.set(BeatBlock.externalAudioAnalyzer.isUseDemucs());
            if (ImGui.checkbox("Demucs##demucsToggle", demucsToggle)) {
                BeatBlock.externalAudioAnalyzer.setUseDemucs(demucsToggle.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用 Demucs 高质量茎分离\n将音频拆分为鼓/贝斯/人声/其他四个独立轨道\n需要额外安装 demucs + torch，分析耗时更长");
            }
        }

        // 添加路径弹窗
        renderAddPopup();

        ImGui.separator();
    }

    private void renderPythonRuntimeHint() {
        if (BeatBlock.externalAudioAnalyzer == null) return;
        String py = BeatBlock.externalAudioAnalyzer.getPythonRuntimeSummary();
        if (py == null || py.isBlank()) return;
        ImGui.textDisabled(py);
        ImGui.separator();
    }

    private void renderAddPopup() {
        ImGui.setNextWindowSize(460f, 0f, ImGuiCond.Always);
        if (!ImGui.beginPopup("##AddAudioPopup")) return;

        ImGui.text("选择音频文件");
        if (ImGui.button("浏览文件...##browseAudio", 120f, 0f)) {
            String chosenPath = chooseAudioFilePath();
            if (chosenPath != null && !chosenPath.isBlank()) {
                importPath.set(chosenPath);
                handleIncomingAudioPath(chosenPath);
                ImGui.closeCurrentPopup();
            }
        }

        ImGui.spacing();
        if (importPath.get().isBlank()) {
            ImGui.textDisabled("尚未选择文件");
        } else {
            ImGui.textWrapped(importPath.get());
        }

        ImGui.spacing();
        ImGui.textDisabled("支持 MP3 · WAV · OGG · FLAC");
        ImGui.textDisabled("提示：选择文件后会自动开始解析");
        ImGui.spacing();

        boolean add = ImGui.button("手动添加并解析##add", 150f, 0f);
        ImGui.sameLine();
        boolean cancel = ImGui.button("取消##cancel");

        if (add) {
            String path = importPath.get().trim();
            if (!path.isEmpty()) {
                handleIncomingAudioPath(path);
            }
            ImGui.closeCurrentPopup();
        }
        if (cancel) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    // ── 拖放区 ────────────────────────────────────────────────────────────

    private void renderDropZone() {
        prunePanelHint();
        float availX = ImGui.getContentRegionAvailX();
        boolean hasHint = panelHintText != null && !panelHintText.isBlank();
        float zoneH = hasHint ? 92f : 72f;

        // 用一个带边框的 child window 作为视觉容器
        ImGui.pushStyleColor(ImGuiCol.ChildBg,    0.12f, 0.11f, 0.18f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Border,     0.40f, 0.38f, 0.60f, 0.45f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding,  6f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f);

        ImGui.beginChild("##DropZone", availX, zoneH, true);
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(2);

        // 文字垂直居中
        float textH = ImGui.getTextLineHeightWithSpacing() * 2f + ImGui.getTextLineHeight();
        ImGui.setCursorPosY((zoneH - textH) * 0.5f - (hasHint ? 6f : 0f));

        centerText("拖入音频文件 / 点击 + 选择");
        centerText("MP3 · WAV · OGG · FLAC");

        if (hasHint) {
            ImGui.spacing();
            if (panelHintError) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.36f, 0.36f, 1f);
                centerText(panelHintText);
                ImGui.popStyleColor();
            } else {
                centerText(panelHintText);
            }
        }

        // ── 接收系统文件拖放（OS → ImGui window）──────────────────────────
        // imgui-java 通过 ImGui.acceptDragDropPayload 接收来自操作系统的文件路径，
        // 具体路径字符串由宿主层（GLFW dragAndDropCallback）注入到名为
        // "BB_OS_FILE_PATH" 的 payload 中。
        if (ImGui.beginDragDropTarget()) {
            byte[] raw = ImGui.acceptDragDropPayload("BB_OS_FILE_PATH");
            if (raw != null) {
                String filePath = new String(raw).trim();
                handleIncomingAudioPath(filePath);
            }
            ImGui.endDragDropTarget();
        }

        String osDropped;
        while ((osDropped = ImGuiRenderer.getInstance().pollDroppedFilePath()) != null) {
            handleIncomingAudioPath(osDropped);
        }

        ImGui.endChild();
    }

    // ── 资产列表 ──────────────────────────────────────────────────────────

    private void renderAssetList(List<AudioAsset> assets) {
        if (assets.isEmpty()) {
            ImGui.spacing();
            centerText("尚未添加音频文件");
            return;
        }

        for (AudioAsset asset : assets) {
            renderAssetItem(asset);
            ImGui.spacing();
        }
    }

    /**
     * 单条资产行：自适应高度，内部决定渲染内容。
     *
     * <p>选中检测放在 beginChild 内部，用 isWindowHovered + isMouseClicked，
     * 避免 beginChild 返回值误判的问题。</p>
     */
    private void renderAssetItem(AudioAsset asset) {
        boolean isSelected = selectedAsset != null
                && selectedAsset.getId().equals(asset.getId());

        // 根据状态预估行高，让 child window 不截断内容
        float itemH = estimateItemHeight(asset);

        // 选中 / 悬停背景
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg,
                    COLOR_SELECTED_BG.x, COLOR_SELECTED_BG.y, COLOR_SELECTED_BG.z, COLOR_SELECTED_BG.w);
        } else {
            ImGui.pushStyleColor(ImGuiCol.ChildBg,
                    COLOR_HOVER_BG.x, COLOR_HOVER_BG.y, COLOR_HOVER_BG.z, 0f); // 默认透明
        }

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4f);
        // 使用唯一 ID 避免 imgui 合并同名 child
        ImGui.beginChild("##item_" + asset.getId(), 0f, itemH, true);
        ImGui.popStyleVar();
        ImGui.popStyleColor();

        // 点击 → 选中
        if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
            selectedAsset = asset;
        }

        // ── 第一行：文件名 + 状态点 ─────────────────────────────────────
        renderItemHeader(asset);

        // ── 第二行：根据状态渲染不同内容 ───────────────────────────────
        ImGui.spacing();
        switch (asset.getStatus()) {
            case PENDING  -> renderPendingContent(asset);
            case QUEUED   -> renderQueuedContent(asset);
            case ANALYZING-> renderAnalyzingContent(asset);
            case COMPLETED-> renderCompletedContent(asset);
            case FAILED   -> renderFailedContent(asset);
        }

        ImGui.endChild();
    }

    /** 根据状态估算行高，避免内容被截断 */
    private float estimateItemHeight(AudioAsset asset) {
        float lineH = ImGui.getTextLineHeightWithSpacing();
        return switch (asset.getStatus()) {
            case PENDING   -> lineH * 2f + 28f;            // 文件名 + 按钮行
            case QUEUED    -> lineH * 3f + 20f;            // 排队信息 + 操作按钮
            case ANALYZING -> lineH * 2f + 12f
                    + AudioAnalysisStep.values().length * lineH; // 步骤列表
            case COMPLETED -> lineH * 4f + 18f;            // 紧凑信息 + 拖拽提示
            case FAILED    -> lineH * 3f + 28f;            // 文件名 + 错误 + 按钮
        };
    }

    private void renderItemHeader(AudioAsset asset) {
        // 状态点（DrawList 绘制，不占光标位置）
        float dotX = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX() - 12f;
        float dotY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
        int dotColor = switch (asset.getStatus()) {
            case COMPLETED -> COLOR_DOT_DONE;
            case QUEUED    -> COLOR_DOT_QUEUED;
            case ANALYZING -> COLOR_DOT_ANALYZING;
            case FAILED    -> COLOR_DOT_FAILED;
            default        -> COLOR_DOT_PENDING;
        };
        ImGui.getWindowDrawList().addCircleFilled(dotX, dotY, 4f, dotColor);

        // 文件名（预留状态点的右侧空间）
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 20f);
        ImGui.text(asset.getFileName());

        // 文件元信息（灰色，小字）
        ImGui.textDisabled(String.format("%.1fs · %dHz",
                asset.getDurationSeconds(), asset.getSampleRate()));
    }

    // ── 各状态内容渲染 ────────────────────────────────────────────────────

    private void renderPendingContent(AudioAsset asset) {
        if (ImGui.button("解析##" + asset.getId())) {
            AudioAssetManager.getInstance().startAnalysis(asset);
        }
        ImGui.sameLine();
        if (ImGui.button("移除##" + asset.getId())) {
            AudioAssetManager.getInstance().remove(asset.getId());
            if (asset == selectedAsset) selectedAsset = null;
        }
    }

    private void renderAnalyzingContent(AudioAsset asset) {
        // 总进度条
        float progress = computeProgress(asset);
        float barW = ImGui.getContentRegionAvailX();

        ImGui.pushStyleColor(ImGuiCol.PlotHistogram,
                COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,
                COLOR_PROGRESS_BG.x, COLOR_PROGRESS_BG.y, COLOR_PROGRESS_BG.z, COLOR_PROGRESS_BG.w);
        ImGui.progressBar(progress, barW, 6f, "");
        ImGui.popStyleColor(2);

        // 百分比文字
        ImGui.sameLine(0f, 6f);
        ImGui.textDisabled(String.format("%.0f%%", progress * 100f));

        String statusText = asset.getProcessingStatusText();
        if (statusText != null && !statusText.isBlank()) {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
            ImGui.textWrapped("正在处理：" + statusText);
            ImGui.popStyleColor();
            return;
        }

        // 步骤逐行显示
        ImGui.spacing();
        for (AudioAnalysisStep step : AudioAnalysisStep.values()) {
            boolean done = asset.getFinishedSteps().contains(step);
            boolean active = isActiveStep(asset, step);
            String stepLabel = stepLabel(step);

            if (done) {
                ImGui.pushStyleColor(ImGuiCol.Text,
                        COLOR_MID.x, COLOR_MID.y, COLOR_MID.z, COLOR_MID.w); // 绿色完成
                ImGui.text(Icons.CHECK + " " + stepLabel);
                ImGui.popStyleColor();
            } else if (active) {
                ImGui.pushStyleColor(ImGuiCol.Text,
                        COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w); // 紫色当前
                ImGui.text("▷ " + stepLabel + "...");
                ImGui.popStyleColor();
            } else {
                ImGui.textDisabled("  " + stepLabel);
            }
        }
    }

    private void renderQueuedContent(AudioAsset asset) {
        AudioAssetManager manager = AudioAssetManager.getInstance();
        int pos = manager.getQueuePosition(asset.getId());
        ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.78f, 0.38f, 1f);
        if (pos > 0) {
            ImGui.text("排队中 #" + pos);
        } else {
            ImGui.text("排队中");
        }
        ImGui.popStyleColor();

        if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
            ImGui.setDragDropPayload("BB_AUDIO_QUEUE_ID", asset.getId().getBytes(), ImGuiCond.Once);
            ImGui.text("调整队列顺序: " + asset.getFileName());
            ImGui.endDragDropSource();
        }

        if (ImGui.beginDragDropTarget()) {
            byte[] raw = ImGui.acceptDragDropPayload("BB_AUDIO_QUEUE_ID");
            if (raw != null) {
                String movingId = decodePayloadText(raw);
                if (!movingId.isBlank()) {
                    manager.moveQueueBefore(movingId, asset.getId());
                }
            }
            ImGui.endDragDropTarget();
        }

        ImGui.spacing();
        ImGui.textDisabled("当前任务将按顺序自动开始解析");
        ImGui.textDisabled("可拖动队列项到此处以调整优先级");

        ImGui.spacing();
        boolean canMoveUp = manager.canMoveQueueUp(asset.getId());
        boolean canMoveDown = manager.canMoveQueueDown(asset.getId());

        if (canMoveUp) {
            if (ImGui.button("上移##queue_up_" + asset.getId())) {
                manager.moveQueueUp(asset.getId());
            }
        } else {
            ImGui.textDisabled("上移");
        }
        ImGui.sameLine();
        if (canMoveDown) {
            if (ImGui.button("下移##queue_down_" + asset.getId())) {
                manager.moveQueueDown(asset.getId());
            }
        } else {
            ImGui.textDisabled("下移");
        }
        ImGui.sameLine();
        if (ImGui.button("移除##remove_queue_" + asset.getId())) {
            manager.remove(asset.getId());
            if (asset == selectedAsset) selectedAsset = null;
        }
    }

    private void renderCompletedContent(AudioAsset asset) {
        // 紧凑的关键信息：BPM + 踩点数量
        ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
        ImGui.text(String.format("%.1f BPM", asset.getBpm()));
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.textDisabled("·");
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_MID.x, COLOR_MID.y, COLOR_MID.z, COLOR_MID.w);
        ImGui.text(asset.getBeatCount() + " 踩点");
        ImGui.popStyleColor();

        // Demucs 茎分离标识
        Beatmap bm = asset.getBeatmap();
        if (bm != null && bm.meta != null && bm.meta.hasStemSeparation()) {
            ImGui.sameLine();
            ImGui.textDisabled("·");
            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Text, 0.22f, 0.78f, 0.82f, 1f);
            int stemCount = bm.meta.stems() != null ? bm.meta.stems().size() : 4;
            ImGui.text(stemCount + "茎");
            ImGui.popStyleColor();
        }

        // 拖拽提示文字（也是拖拽源的触发区域）
        ImGui.spacing();
        ImGui.textDisabled(Icons.MENU + " 拖动到时间线音频轨道");

        // 整个 child window 作为拖拽源
        if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
            AudioAssetManager.getInstance().setCurrentDragAsset(asset);
            ImGui.setDragDropPayload(
                    "BB_AUDIO_ASSET_ID",
                    asset.getId().getBytes(),
                    ImGuiCond.Once
            );
            // 拖拽预览浮窗
            ImGui.text(Icons.MUSIC_NOTE + " " + asset.getFileName());
            ImGui.textDisabled(String.format("%.1f BPM · %d 踩点",
                    asset.getBpm(), asset.getBeatCount()));
            ImGui.endDragDropSource();
        }
    }

    private void renderFailedContent(AudioAsset asset) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.87f, 0.30f, 0.30f, 1f);
        String msg = asset.getErrorMessage() != null
                ? asset.getErrorMessage()
                : "解析失败，请检查文件格式";
        ImGui.textWrapped(msg);
        ImGui.popStyleColor();

        ImGui.spacing();
        if (ImGui.button("重试##retry_" + asset.getId())) {
            AudioAssetManager.getInstance().startAnalysis(asset);
        }
        ImGui.sameLine();
        if (ImGui.button("转换为MP3##convert_" + asset.getId())) {
            boolean accepted = AudioAssetManager.getInstance().requestConvertToMp3(asset);
            if (!accepted) {
                asset.setErrorMessage("已记录转换请求。当前版本暂未接入自动转换器，请先手动转为 MP3/WAV 后重试。");
            }
        }
        ImGui.sameLine();
        if (ImGui.button("移除##remove_failed_" + asset.getId())) {
            AudioAssetManager.getInstance().remove(asset.getId());
            if (asset == selectedAsset) selectedAsset = null;
        }
    }

    // ── 底栏 ──────────────────────────────────────────────────────────────

    private void renderFooter(List<AudioAsset> assets) {
        float btnW = 110f;
		prunePanelHint();

        AudioAsset runningAsset = null;
        int queuedCount = 0;
        for (AudioAsset a : assets) {
            if (a.getStatus() == AudioAssetStatus.ANALYZING && runningAsset == null) {
                runningAsset = a;
            }
            if (a.getStatus() == AudioAssetStatus.QUEUED) {
                queuedCount++;
            }
        }

        // 全部解析（跳过已完成和正在进行的）
        if (ImGui.button("全部解析##analyzeAll", btnW, 22f)) {
            for (AudioAsset a : assets) {
                if (a.getStatus() == AudioAssetStatus.PENDING
                        || a.getStatus() == AudioAssetStatus.FAILED) {
                    AudioAssetManager.getInstance().startAnalysis(a);
                }
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("解析所有等待中和失败的文件");

        ImGui.sameLine();

        // 清除已完成
        if (ImGui.button("清除已完成##clearDone", btnW, 22f)) {
            assets.stream()
                    .filter(a -> a.getStatus() == AudioAssetStatus.COMPLETED)
                    .map(AudioAsset::getId)
                    .toList()
                    .forEach(id -> AudioAssetManager.getInstance().remove(id));
            if (selectedAsset != null
                    && selectedAsset.getStatus() == AudioAssetStatus.COMPLETED) {
                selectedAsset = null;
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("从列表中移除所有已解析完成的项目（不删除 beatmap 文件）");

        if (panelHintText != null && !panelHintText.isBlank()) {
            ImGui.sameLine();
            if (panelHintError) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.36f, 0.36f, 1f);
                ImGui.text(panelHintText);
                ImGui.popStyleColor();
            } else {
                ImGui.textDisabled(panelHintText);
            }
        }

        if (runningAsset != null || queuedCount > 0) {
            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.74f, 0.92f, 1f);
            String running = runningAsset != null
                ? ("执行中: " + runningAsset.getFileName())
                : "执行中: 无";
            String queueText = "队列: " + queuedCount;
            ImGui.text(running + " · " + queueText);
            ImGui.popStyleColor();
        }

        // 右对齐：资产统计
        long doneCount = assets.stream()
                .filter(a -> a.getStatus() == AudioAssetStatus.COMPLETED).count();
        String countText = String.format("%d / %d 已完成", doneCount, assets.size());
        float textW = ImGui.calcTextSize(countText).x;
        ImGui.sameLine(ImGui.getContentRegionAvailX() - textW + ImGui.getCursorPosX());
        ImGui.textDisabled(countText);
    }

    // ── 右侧详情面板 ─────────────────────────────────────────────────────

    private void renderDetailPanel(AudioAsset asset) {
        // 折叠按钮（自定义图标，与工具栏一致）
        IconButtonStyle.pushBeatBlockIconButton();
        if (ImGui.button(iconLabel(Icons.Layout.LEFT_COLLAPSE, "<") + "##collapse", ICON_BTN, ICON_BTN)) {
            detailExpanded = false;
        }
        IconButtonStyle.popBeatBlockIconButton();
        if (ImGui.isItemHovered()) ImGui.setTooltip("折叠详情");
        ImGui.sameLine();
        if (ImGui.button("重置比例 6:4##resetDetailRatio")) {
            detailRatio = 0.40f;
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("将左右区域恢复为默认 6:4");
        ImGui.sameLine();
        ImGui.text("详情");
        ImGui.separator();

        if (asset == null) {
            ImGui.spacing();
            ImGui.textDisabled("点击左侧列表中的\n音频查看详情");
            return;
        }

        switch (asset.getStatus()) {
            case COMPLETED -> renderDetailCompleted(asset);
            case QUEUED    -> renderDetailQueued(asset);
            case ANALYZING -> renderDetailAnalyzing(asset);
            case PENDING   -> renderDetailPending(asset);
            case FAILED    -> renderDetailFailed(asset);
        }
    }

    private void renderDetailQueued(AudioAsset asset) {
        sectionHeader("队列状态");
        AudioAssetManager manager = AudioAssetManager.getInstance();
        int pos = manager.getQueuePosition(asset.getId());
        detailRow("当前状态", "排队中");
        if (pos > 0) {
            detailRowColored("队列位次", "#" + pos, new ImVec4(0.95f, 0.78f, 0.38f, 1f));
        }
        ImGui.spacing();
        ImGui.textDisabled("当前分析器为串行执行，前序任务完成后将自动开始。\n你可以继续添加文件，系统会按顺序处理。");
        ImGui.textDisabled("提示：左侧列表支持拖动队列项直接改顺序。");
        ImGui.spacing();

        boolean canMoveUp = manager.canMoveQueueUp(asset.getId());
        boolean canMoveDown = manager.canMoveQueueDown(asset.getId());

        float half = (ImGui.getContentRegionAvailX() - 6f) * 0.5f;
        if (canMoveUp) {
            if (ImGui.button("上移优先级##detailQueueUp", half, 26f)) {
                manager.moveQueueUp(asset.getId());
            }
        } else {
            ImGui.beginDisabled();
            ImGui.button("上移优先级##detailQueueUpDisabled", half, 26f);
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (canMoveDown) {
            if (ImGui.button("下移优先级##detailQueueDown", half, 26f)) {
                manager.moveQueueDown(asset.getId());
            }
        } else {
            ImGui.beginDisabled();
            ImGui.button("下移优先级##detailQueueDownDisabled", half, 26f);
            ImGui.endDisabled();
        }
        ImGui.spacing();

        if (ImGui.button("移除队列项##detailRemoveQueued", ImGui.getContentRegionAvailX(), 26f)) {
            manager.remove(asset.getId());
            if (asset == selectedAsset) selectedAsset = null;
        }
    }

    private void renderDetailCompleted(AudioAsset asset) {
        // ── 基本信息 ───────────────────────────────────────────────────
        sectionHeader("基本信息");
        detailRow("文件名",   asset.getFileName());
        detailRow("时长",     String.format("%.1f 秒", asset.getDurationSeconds()));
        detailRow("采样率",   asset.getSampleRate() + " Hz");

        ImGui.spacing();

        // ── 解析结果 ───────────────────────────────────────────────────
        sectionHeader("解析结果");
        detailRowColored("BPM",    String.format("%.1f", asset.getBpm()),       COLOR_PROGRESS_FG);
        detailRow("拍号",           "4/4");
        detailRowColored("踩点数量", asset.getBeatCount() + " 个",               COLOR_MID);
        detailRow("识别段落",        asset.getSectionCount() + " 段");

        ImGui.spacing();

        // ── 频段分布 ───────────────────────────────────────────────────
        sectionHeader("频段踩点分布");
        detailRowColored("低频（鼓点）", asset.getLowCount()  + " 个", COLOR_LOW);
        detailRowColored("中频（旋律）", asset.getMidCount()  + " 个", COLOR_MID);
        detailRowColored("高频（打击）", asset.getHighCount() + " 个", COLOR_HIGH);

        if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
            ImGui.spacing();
            ImGui.textDisabled(asset.getInfoMessage());
        }

        // 简易频段占比条
        ImGui.spacing();
        renderBandBar(asset);

        // ── Demucs 茎分离详情 ─────────────────────────────────────────────
        Beatmap detailBm = asset.getBeatmap();
        if (detailBm != null && detailBm.meta != null && detailBm.meta.hasStemSeparation()) {
            ImGui.spacing();
            sectionHeader("茎分离 (Demucs)");
            detailRowColored("分离模式", detailBm.meta.separationMode(), new ImVec4(0.22f, 0.78f, 0.82f, 1f));
            if (detailBm.meta.stems() != null && !detailBm.meta.stems().isEmpty()) {
                detailRow("茎数量", detailBm.meta.stems().size() + " 条");
                ImGui.spacing();
                ImGui.textDisabled("茎文件路径：");
                for (var entry : detailBm.meta.stems().entrySet()) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.22f, 0.78f, 0.82f, 0.85f);
                    ImGui.text("  " + entry.getKey());
                    ImGui.popStyleColor();
                    ImGui.sameLine();
                    ImGui.textDisabled(entry.getValue());
                }
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // ── 拖拽到时间线 ──────────────────────────────────────────────
        float btnW = ImGui.getContentRegionAvailX();
        ImGui.pushStyleColor(ImGuiCol.Button,        0.28f, 0.26f, 0.45f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.35f, 0.33f, 0.55f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  0.45f, 0.43f, 0.65f, 1f);
        ImGui.button(Icons.MENU + "  拖动到时间线##dragBtn", btnW, 28f);
        ImGui.popStyleColor(3);

        if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
            AudioAssetManager.getInstance().setCurrentDragAsset(asset);
            ImGui.setDragDropPayload(
                    "BB_AUDIO_ASSET_ID",
                    asset.getId().getBytes(),
                    ImGuiCond.Once
            );
            ImGui.text(Icons.MUSIC_NOTE + " " + asset.getFileName());
            ImGui.textDisabled(String.format("%.1f BPM · %d 踩点",
                    asset.getBpm(), asset.getBeatCount()));
            ImGui.endDragDropSource();
        }
    }

    private void renderDetailAnalyzing(AudioAsset asset) {
        sectionHeader("解析进度");
        float progress = computeProgress(asset);

        ImGui.pushStyleColor(ImGuiCol.PlotHistogram,
                COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,
                COLOR_PROGRESS_BG.x, COLOR_PROGRESS_BG.y, COLOR_PROGRESS_BG.z, COLOR_PROGRESS_BG.w);
        ImGui.progressBar(progress, ImGui.getContentRegionAvailX(), 8f,
                String.format("%.0f%%", progress * 100f));
        ImGui.popStyleColor(2);

        String statusText = asset.getProcessingStatusText();
        if (statusText != null && !statusText.isBlank()) {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
            ImGui.textWrapped("正在处理：" + statusText);
            ImGui.popStyleColor();
            return;
        }

        ImGui.spacing();
        for (AudioAnalysisStep step : AudioAnalysisStep.values()) {
            boolean done   = asset.getFinishedSteps().contains(step);
            boolean active = isActiveStep(asset, step);
            String label   = stepLabel(step);
            if (done) {
                ImGui.pushStyleColor(ImGuiCol.Text,
                        COLOR_MID.x, COLOR_MID.y, COLOR_MID.z, COLOR_MID.w);
                ImGui.text(Icons.CHECK + "  " + label);
                ImGui.popStyleColor();
            } else if (active) {
                ImGui.pushStyleColor(ImGuiCol.Text,
                        COLOR_PROGRESS_FG.x, COLOR_PROGRESS_FG.y, COLOR_PROGRESS_FG.z, COLOR_PROGRESS_FG.w);
                ImGui.text("▷  " + label + "...");
                ImGui.popStyleColor();
            } else {
                ImGui.textDisabled("     " + label);
            }
        }
    }

    private void renderDetailPending(AudioAsset asset) {
        sectionHeader("基本信息");
        detailRow("文件名", asset.getFileName());
        detailRow("时长",   String.format("%.1f 秒", asset.getDurationSeconds()));
        ImGui.spacing();
        ImGui.textDisabled("尚未解析，点击「解析」开始。");
        ImGui.spacing();
        if (ImGui.button("开始解析##detailAnalyze", ImGui.getContentRegionAvailX(), 26f)) {
            AudioAssetManager.getInstance().startAnalysis(asset);
        }
    }

    private void renderDetailFailed(AudioAsset asset) {
        sectionHeader("错误信息");
        ImGui.pushStyleColor(ImGuiCol.Text, 0.87f, 0.30f, 0.30f, 1f);
        ImGui.textWrapped(asset.getErrorMessage() != null
                ? asset.getErrorMessage()
                : "未知错误");
        ImGui.popStyleColor();
        if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
            ImGui.spacing();
            ImGui.textDisabled(asset.getInfoMessage());
        }
        ImGui.spacing();
        ImGui.textDisabled("支持格式：MP3 · WAV · OGG · FLAC");
        ImGui.spacing();
        if (ImGui.button("一键转换为 MP3##detailConvert", ImGui.getContentRegionAvailX(), 26f)) {
            boolean accepted = AudioAssetManager.getInstance().requestConvertToMp3(asset);
            if (!accepted) {
                asset.setErrorMessage("已记录转换请求。当前版本暂未接入自动转换器，请先手动转为 MP3/WAV 后重试。");
            }
        }
        ImGui.spacing();
        if (ImGui.button("重试##detailRetry", ImGui.getContentRegionAvailX(), 26f)) {
            AudioAssetManager.getInstance().startAnalysis(asset);
        }
    }

    // ── 辅助：频段比例条 ─────────────────────────────────────────────────

    /**
     * 用 DrawList 画一条三色频段占比条（低/中/高频）。
     * 宽度 = 可用宽度，高度 = 6px。
     */
    private void renderBandBar(AudioAsset asset) {
        int total = asset.getLowCount() + asset.getMidCount() + asset.getHighCount();
        if (total == 0) return;

        float barW = ImGui.getContentRegionAvailX();
        float barH = getBarH(asset, barW, (float) total);

        // 推进光标，使后续控件不重叠
        ImGui.dummy(barW, barH + 2f);

        // 图例
        ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_LOW.x, COLOR_LOW.y, COLOR_LOW.z, COLOR_LOW.w);
        ImGui.text("■ 低");
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_MID.x, COLOR_MID.y, COLOR_MID.z, COLOR_MID.w);
        ImGui.text("■ 中");
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Text,
                COLOR_HIGH.x, COLOR_HIGH.y, COLOR_HIGH.z, COLOR_HIGH.w);
        ImGui.text("■ 高");
        ImGui.popStyleColor();
    }

    private static float getBarH(AudioAsset asset, float barW, float total) {
        float barH = 8f;
        float x0   = ImGui.getCursorScreenPosX();
        float y0   = ImGui.getCursorScreenPosY();

        float lowW  = barW * (asset.getLowCount()  / total);
        float midW  = barW * (asset.getMidCount()  / total);

        var dl = ImGui.getWindowDrawList();
        float r = 3f; // 圆角
        dl.addRectFilled(x0,             y0, x0 + lowW,            y0 + barH, 0xFF7777D0, r);
        dl.addRectFilled(x0 + lowW,      y0, x0 + lowW + midW,     y0 + barH, 0xFF57C4A0);
        dl.addRectFilled(x0 + lowW + midW, y0, x0 + barW,          y0 + barH, 0xFF27A0EF, r);
        return barH;
    }

    // ── 辅助：进度计算 ────────────────────────────────────────────────────

    /**
     * 根据已完成步骤数除以总步骤数算出 0~1 的进度值。
     * 若资产提供了精确的 getAnalysisProgress()，优先使用。
     */
    private float computeProgress(AudioAsset asset) {
		if (asset.getAnalysisProgressPercent() > 0) {
			return Math.max(0f, Math.min(1f, asset.getAnalysisProgressPercent() / 100f));
		}
        int total = AudioAnalysisStep.values().length;
        if (total == 0) return 0f;
        return (float) asset.getFinishedSteps().size() / total;
    }

    /**
     * 判断某步骤是否为当前正在执行的步骤
     * （即第一个未完成的步骤）。
     */
    private boolean isActiveStep(AudioAsset asset, AudioAnalysisStep step) {
        for (AudioAnalysisStep s : AudioAnalysisStep.values()) {
            if (!asset.getFinishedSteps().contains(s)) {
                return s == step;
            }
        }
        return false;
    }

    // ── 辅助：UI 小工具 ───────────────────────────────────────────────────

    /** 居中渲染一行文字（基于可用宽度）。 */
    private void centerText(String text) {
        float textW = ImGui.calcTextSize(text).x;
        float offsetX = (ImGui.getContentRegionAvailX() - textW) * 0.5f;
        if (offsetX > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX);
        ImGui.textDisabled(text);
    }

    /** 节标题（加粗感：先 separator 再文字）。 */
    private void sectionHeader(String title) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.68f, 0.90f, 1f);
        ImGui.text(title);
        ImGui.popStyleColor();
        ImGui.separator();
    }

    /** key-value 一行，key 灰色，value 白色，右对齐。 */
    private void detailRow(String key, String value) {
        ImGui.textDisabled(key + "：");
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + 4f);
        ImGui.text(value);
    }

    /** 同 detailRow，但 value 使用指定颜色。 */
    private void detailRowColored(String key, String value, ImVec4 color) {
        ImGui.textDisabled(key + "：");
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + 4f);
        ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w);
        ImGui.text(value);
        ImGui.popStyleColor();
    }

    /** AudioAnalysisStep → 人类可读标签。 */
    private String stepLabel(AudioAnalysisStep step) {
        return switch (step) {
            case BPM_DETECTION    -> "BPM 检测";
            case BEAT_DETECTION   -> "踩点检测";
            case BAND_SPLIT       -> "频段分离";
            case SECTION_DETECTION-> "段落识别";
            case STEM_SEPARATION  -> "Demucs 茎分离";
            case WRITE_BEATMAP    -> "写入 Beatmap";
        };
    }

    private boolean handleIncomingAudioPath(String path) {
        if (path == null || path.isBlank()) return false;
        AudioAssetManager manager = AudioAssetManager.getInstance();
        if (!manager.isSupportedAudioPath(path)) {
            setPanelHint("仅支持 " + manager.getSupportedAudioExtensionsLabel(), true);
            return false;
        }
        AudioAsset asset = AudioAssetManager.getInstance().addFromPath(path);
        if (asset != null) {
            selectedAsset = asset;
            AudioAssetManager.getInstance().startAnalysis(asset);
            setPanelHint("已添加并开始解析: " + asset.getFileName(), false);
            return true;
        }
        setPanelHint("路径无效或文件不存在", true);
        return false;
    }

    private void setPanelHint(String text, boolean isError) {
        this.panelHintText = text;
        this.panelHintError = isError;
        this.panelHintExpireAtMs = System.currentTimeMillis() + 5000L;
    }

    private void prunePanelHint() {
        if (panelHintText == null) return;
        if (System.currentTimeMillis() >= panelHintExpireAtMs) {
            panelHintText = null;
            panelHintError = false;
        }
    }

    private static String iconLabel(String icon, String fallback) {
        if (ImGuiFontManager.getIconButtonFont() == null) {
            return fallback;
        }
        return icon;
    }

    private static void setTooltipWithDefaultFont(String text) {
        if (ImGuiFontManager.getIconButtonFont() == null) {
            ImGui.setTooltip(text);
            return;
        }
        ImGui.popFont();
        ImGui.setTooltip(text);
        ImGui.pushFont(ImGuiFontManager.getIconButtonFont());
    }

    private String chooseAudioFilePath() {
        try {
            String nativePath = openNativeAudioFileDialog();
            if (nativePath != null && !nativePath.isBlank()) {
                return nativePath;
            }
        } catch (Throwable nativeErr) {
            try {
                String swingPath = openSwingAudioFileDialog();
                if (swingPath != null && !swingPath.isBlank()) {
                    return swingPath;
                }
            } catch (Throwable swingErr) {
                try {
                    String psPath = openWindowsPowerShellFileDialog();
                    if (psPath != null && !psPath.isBlank()) {
                        return psPath;
                    }
                } catch (Throwable psErr) {
                    setPanelHint("打开文件选择器失败: "
                            + describeThrowable(nativeErr)
                            + " | 备用方案失败: "
                            + describeThrowable(swingErr)
                            + " | PowerShell 方案失败: "
                            + describeThrowable(psErr), true);
                    return null;
                }

                setPanelHint("打开文件选择器失败: "
                        + describeThrowable(nativeErr)
                        + " | 备用方案失败: "
                        + describeThrowable(swingErr), true);
                return null;
            }

            setPanelHint("打开文件选择器失败: " + describeThrowable(nativeErr), true);
            return null;
        }

        return null;
    }


    private String openNativeAudioFileDialog() throws Exception {
        final String[] selected = new String[1];

        Runnable dialogTask = () -> {
            FileDialog dialog = new FileDialog((Frame) null, "选择音频文件", FileDialog.LOAD);
            dialog.setMultipleMode(false);
            dialog.setFilenameFilter((dir, name) -> {
                String lower = name == null ? "" : name.toLowerCase();
                return lower.endsWith(".mp3") || lower.endsWith(".wav")
                        || lower.endsWith(".ogg") || lower.endsWith(".flac");
            });

            String current = importPath.get().trim();
            if (!current.isEmpty()) {
                File seed = new File(current);
                File parent = seed.getParentFile();
                if (parent != null && parent.exists()) {
                    dialog.setDirectory(parent.getAbsolutePath());
                }
                if (seed.getName() != null && !seed.getName().isBlank()) {
                    dialog.setFile(seed.getName());
                }
            }

            dialog.setVisible(true);
            String file = dialog.getFile();
            String dir = dialog.getDirectory();
            dialog.dispose();

            if (file != null && dir != null) {
                selected[0] = new File(dir, file).getAbsolutePath();
            }
        };

        if (EventQueue.isDispatchThread()) {
            dialogTask.run();
        } else {
            EventQueue.invokeAndWait(dialogTask);
        }

        return selected[0];
    }

    private String openSwingAudioFileDialog() throws Exception {
        final String[] selected = new String[1];

        Runnable chooserTask = () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择音频文件");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "音频文件 (*.mp3, *.wav, *.ogg, *.flac)", "mp3", "wav", "ogg", "flac"));

            String current = importPath.get().trim();
            if (!current.isEmpty()) {
                chooser.setSelectedFile(new File(current));
            }

            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                selected[0] = chooser.getSelectedFile().getAbsolutePath();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            chooserTask.run();
        } else {
            SwingUtilities.invokeAndWait(chooserTask);
        }

        return selected[0];
    }

    private String openWindowsPowerShellFileDialog() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            throw new UnsupportedOperationException("当前系统不是 Windows");
        }

        String script = String.join("; ",
                "Add-Type -AssemblyName System.Windows.Forms",
                "$dlg = New-Object System.Windows.Forms.OpenFileDialog",
                "$dlg.Title = '选择音频文件'",
                "$dlg.Filter = '音频文件 (*.mp3;*.wav;*.ogg;*.flac)|*.mp3;*.wav;*.ogg;*.flac'",
                "$dlg.Multiselect = $false",
                "$seed = $env:BB_AUDIO_PICKER_SEED",
                "if (-not [string]::IsNullOrWhiteSpace($seed)) { try { $dir=[System.IO.Path]::GetDirectoryName($seed); if (-not [string]::IsNullOrWhiteSpace($dir)) { $dlg.InitialDirectory = $dir } } catch {} }",
            "if ($dlg.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {",
            "$bytes = [System.Text.Encoding]::UTF8.GetBytes($dlg.FileName)",
            "$b64 = [Convert]::ToBase64String($bytes)",
            "[Console]::Out.Write('B64:' + $b64)",
            "}"
        );

        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Sta", "-Command", script)
                .redirectErrorStream(true);
        String current = importPath.get().trim();
        if (!current.isEmpty()) {
            pb.environment().put("BB_AUDIO_PICKER_SEED", current);
        }

        Process process = pb.start();
        String output;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            output = sb.toString().trim();
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("PowerShell 退出码=" + exit + (output.isEmpty() ? "" : ("; " + output)));
        }

        if (output.isEmpty()) return null;
        if (output.startsWith("B64:")) {
            String b64 = output.substring(4).trim();
            if (b64.isEmpty()) return null;
            byte[] bytes = Base64.getDecoder().decode(b64);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return output;
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) return "未知错误";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "无详细信息";
        }
        return root.getClass().getSimpleName() + "(" + msg + ")";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String decodePayloadText(byte[] payload) {
        if (payload == null || payload.length == 0) return "";
        String raw = new String(payload);
        int zero = raw.indexOf('\0');
        if (zero >= 0) raw = raw.substring(0, zero);
        return raw.trim();
    }
}

