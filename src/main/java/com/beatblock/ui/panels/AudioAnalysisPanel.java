package com.beatblock.ui.panels;

import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

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

    // ── 状态字段 ────────────────────────────────────────────────────────────
    private final ImString importPath = new ImString(512);
    private AudioAsset selectedAsset;
    private boolean detailExpanded = true;

    // ── 公共入口 ─────────────────────────────────────────────────────────────

    public void render() {
        if (!ImGui.begin("音频解析###AudioAnalysisPanel", WINDOW_FLAGS)) {
            ImGui.end();
            return;
        }

        renderToolbar();

        List<AudioAsset> assets = AudioAssetManager.getInstance().getAssets();

        float totalW = ImGui.getContentRegionAvailX();
        float totalH = ImGui.getContentRegionAvailY() - 32f; // 为底栏留空间
        float detailW = detailExpanded ? totalW * 0.42f : 0f;
        float listW   = totalW - detailW - (detailExpanded ? 6f : 0f);

        // ── 左侧：列表 ──────────────────────────────────────────────────────
        ImGui.beginChild("##AudioList", listW, totalH, false);
        renderDropZone();
        ImGui.spacing();
        renderAssetList(assets);
        ImGui.endChild();

        // ── 右侧：详情 ──────────────────────────────────────────────────────
        ImGui.sameLine();
        if (detailExpanded) {
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
        if (ImGui.button(Icons.Action.ADD + "##AddAudio", ICON_BTN, ICON_BTN)) {
            importPath.set("");
            ImGui.openPopup("##AddAudioPopup");
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("添加音频文件路径");

        ImGui.sameLine();

        if (ImGui.button((detailExpanded ? Icons.Layout.LEFT_COLLAPSE : Icons.Layout.RIGHT_EXPAND) + "##detail", ICON_BTN, ICON_BTN)) {
            detailExpanded = !detailExpanded;
        }
        IconButtonStyle.popBeatBlockIconButton();
        if (ImGui.isItemHovered()) ImGui.setTooltip(detailExpanded ? "折叠详情" : "展开详情");

        // 添加路径弹窗
        renderAddPopup();

        ImGui.separator();
    }

    private void renderAddPopup() {
        ImGui.setNextWindowSize(360f, 0f, ImGuiCond.Always);
        if (!ImGui.beginPopup("##AddAudioPopup")) return;

        ImGui.text("音频文件路径");
        ImGui.setNextItemWidth(-1f);
        boolean entered = ImGui.inputText("##AudioPathInput", importPath,
                imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);

        ImGui.spacing();
        ImGui.textDisabled("支持 MP3 · WAV · OGG · FLAC");
        ImGui.spacing();

        boolean add = ImGui.button("添加并解析##add", 120f, 0f) || entered;
        ImGui.sameLine();
        boolean cancel = ImGui.button("取消##cancel");

        if (add) {
            String path = importPath.get().trim();
            if (!path.isEmpty()) {
                AudioAsset asset = AudioAssetManager.getInstance().addFromPath(path);
                if (asset != null) {
                    selectedAsset = asset;
                    AudioAssetManager.getInstance().startAnalysis(asset);
                }
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
        float availX = ImGui.getContentRegionAvailX();

        // 用一个带边框的 child window 作为视觉容器
        ImGui.pushStyleColor(ImGuiCol.ChildBg,    0.12f, 0.11f, 0.18f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Border,     0.40f, 0.38f, 0.60f, 0.45f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding,  6f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f);

        ImGui.beginChild("##DropZone", availX, 72f, true);
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(2);

        // 文字垂直居中
        float textH = ImGui.getTextLineHeightWithSpacing() * 2f + ImGui.getTextLineHeight();
        ImGui.setCursorPosY((72f - textH) * 0.5f);

        centerText("拖入音频文件 / 点击 + 添加");
        centerText("MP3 · WAV · OGG · FLAC");

        // ── 接收系统文件拖放（OS → ImGui window）──────────────────────────
        // imgui-java 通过 ImGui.acceptDragDropPayload 接收来自操作系统的文件路径，
        // 具体路径字符串由宿主层（GLFW dragAndDropCallback）注入到名为
        // "BB_OS_FILE_PATH" 的 payload 中。
        if (ImGui.beginDragDropTarget()) {
            byte[] raw = ImGui.acceptDragDropPayload("BB_OS_FILE_PATH");
            if (raw != null) {
                String filePath = new String(raw).trim();
                if (!filePath.isEmpty()) {
                    AudioAsset asset = AudioAssetManager.getInstance().addFromPath(filePath);
                    if (asset != null) {
                        selectedAsset = asset;
                        AudioAssetManager.getInstance().startAnalysis(asset);
                    }
                }
            }
            ImGui.endDragDropTarget();
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
            case ANALYZING -> lineH * 2f + 12f
                    + AudioAnalysisStep.values().length * lineH; // 步骤列表
            case COMPLETED -> lineH * 2f + 24f;            // 文件名 + 拖拽提示
            case FAILED    -> lineH * 3f + 28f;            // 文件名 + 错误 + 按钮
        };
    }

    private void renderItemHeader(AudioAsset asset) {
        // 状态点（DrawList 绘制，不占光标位置）
        float dotX = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX() - 12f;
        float dotY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
        int dotColor = switch (asset.getStatus()) {
            case COMPLETED -> COLOR_DOT_DONE;
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

    private void renderCompletedContent(AudioAsset asset) {
        // BPM + 踩点数量快速预览
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
        if (ImGui.button(Icons.Layout.LEFT_COLLAPSE + "##collapse", ICON_BTN, ICON_BTN)) {
            detailExpanded = false;
        }
        IconButtonStyle.popBeatBlockIconButton();
        if (ImGui.isItemHovered()) ImGui.setTooltip("折叠详情");
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
            case ANALYZING -> renderDetailAnalyzing(asset);
            case PENDING   -> renderDetailPending(asset);
            case FAILED    -> renderDetailFailed(asset);
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

        // 简易频段占比条
        ImGui.spacing();
        renderBandBar(asset);

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
        ImVec2 cursorStart = ImGui.getCursorStartPos();
        ImGui.textDisabled(key);
        float keyWidth = ImGui.calcTextSize(key).x;
        float valueWidth = ImGui.calcTextSize(value).x;
        float region = ImGui.getContentRegionAvailX() + keyWidth;
        float x = cursorStart.x + region - valueWidth;
        ImGui.sameLine();
        ImGui.setCursorPosX(x);
        ImGui.text(value);
    }

    /** 同 detailRow，但 value 使用指定颜色。 */
    private void detailRowColored(String key, String value, ImVec4 color) {
        ImVec2 cursorStart = ImGui.getCursorStartPos();
        ImGui.textDisabled(key);
        float keyWidth = ImGui.calcTextSize(key).x;
        float valueWidth = ImGui.calcTextSize(value).x;
        float region = ImGui.getContentRegionAvailX() + keyWidth;
        float x = cursorStart.x + region - valueWidth;
        ImGui.sameLine();
        ImGui.setCursorPosX(x);
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
            case WRITE_BEATMAP    -> "写入 Beatmap";
        };
    }
}

