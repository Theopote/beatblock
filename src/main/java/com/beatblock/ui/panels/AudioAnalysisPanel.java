package com.beatblock.ui.panels;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelHost;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelRenderer;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelUiState;
import com.beatblock.ui.presenter.AudioAnalysisPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 音频解析面板 / 媒体箱
 *
 * <p>布局：左侧列表 | 右侧详情（可折叠）</p>
 */
public final class AudioAnalysisPanel implements AudioAnalysisPanelHost {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final AudioAnalysisPanelPresenter presenter;
	private final AudioAnalysisPanelUiState uiState = new AudioAnalysisPanelUiState();

	public AudioAnalysisPanel() {
		this(PresenterFactories.audioAnalysisPanelPresenter());
	}

	AudioAnalysisPanel(AudioAnalysisPanelPresenter presenter) {
		this.presenter = presenter;
	}

	@Override
	public AudioAnalysisPanelPresenter presenter() {
		return presenter;
	}

	@Override
	public AudioAnalysisPanelUiState uiState() {
		return uiState;
	}

    public void render(ImBoolean pOpen) {
        if (!pOpen.get()) {
            BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.AUDIO_ANALYSIS_WINDOW);
            return;
        }
		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
			AudioAnalysisPanelRenderer.outerPaddingX(),
			AudioAnalysisPanelRenderer.outerPaddingY());
        if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.AUDIO_ANALYSIS_WINDOW, pOpen, WINDOW_FLAGS)) {
            ImGui.popStyleVar();
            return;
        }
        ImGui.popStyleVar();
        try {
			AudioAnalysisPanelRenderer.renderContent(this);
        } finally {
            BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.AUDIO_ANALYSIS_WINDOW);
        }
    }

	@Override
	public boolean handleIncomingAudioPath(String path) {
        if (path == null || path.isBlank()) return false;
        AudioAssetManager manager = AudioAssetManager.getInstance();
        if (!manager.isSupportedAudioPath(path)) {
			uiState.setPanelHint("仅支持 " + manager.getSupportedAudioExtensionsLabel(), true);
            return false;
        }
		AudioAsset asset = manager.addFromPath(path);
        if (asset != null) {
			uiState.setSelectedAsset(asset);
			manager.startAnalysis(asset);
			uiState.setPanelHint("已添加并开始解析: " + asset.getFileName(), false);
            return true;
        }
		uiState.setPanelHint("路径无效或文件不存在", true);
        return false;
    }

	@Override
	public String chooseAudioFilePath() {
		return AudioAnalysisPanelRenderer.chooseFilePath(this);
    }
}
