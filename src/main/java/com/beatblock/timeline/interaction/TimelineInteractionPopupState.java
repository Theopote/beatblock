package com.beatblock.timeline.interaction;

import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.MARKER_NAME_BUFFER_SIZE;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.PARAM_INPUT_BUFFER_SIZE;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.TIME_INPUT_BUFFER_SIZE;

/** ImGui 弹窗与右键上下文所需的持久状态。 */
public final class TimelineInteractionPopupState {

	public String contextTrackId;
	public String contextClipId;
	public double contextTimeSeconds;
	public String propertiesEventId;
	public String contextMarkerId;

	public final ImString propertiesTimeBuffer = new ImString(TIME_INPUT_BUFFER_SIZE);
	public String propertiesOriginalTime = "0";
	public final Map<String, ImString> propertiesParamBuffers = new HashMap<>();
	public final Map<String, Boolean> propertiesParamAsNumber = new HashMap<>();
	public final Map<String, String> propertiesOriginalParamValues = new HashMap<>();
	public final Map<String, Boolean> propertiesOriginalParamAsNumber = new HashMap<>();
	public final ImString propertiesNewParamKey = new ImString(PARAM_INPUT_BUFFER_SIZE);
	public final ImString propertiesNewParamValue = new ImString(PARAM_INPUT_BUFFER_SIZE);
	public boolean propertiesNewParamAsNumber;
	public String propertiesError;

	public final ImString markerNameBuffer = new ImString(MARKER_NAME_BUFFER_SIZE);
	public final ImBoolean contextCameraShowPath = new ImBoolean(true);
}
