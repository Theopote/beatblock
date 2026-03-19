package com.beatblock.ui.icons;

/**
 * BeatBlock UI Icons (BeatBlock.ttf).
 * <p>
 * This mod uses a custom icon font and references icons via PUA codepoints (U+Fxxx),
 * instead of relying on system emoji rendering.
 */
public final class Icons {
	private Icons() {
		throw new AssertionError("Cannot instantiate Icons");
	}

	// 以下 codepoint 来自 demo.html 的 glyph 列表（BeatBlock.ttf / IcoMoon 生成）。
	// demo.html 内的 icon-bb-* -> 对应 U+Fxxx。
	public static final String VISIBLE = "\uF067";      // icon-bb-visible
	public static final String LOCK = "\uF095";         // icon-bb-lock
	public static final String CHECK = "\uF054";        // icon-bb-check
	public static final String MORE_HORIZ = "\uF06B";   // icon-bb-more-horiz (用于 ☰)
	public static final String NOTE = "\uF04D";         // icon-bb-note (用于 ♪)

	// 与现有 UI 命名保持兼容：语义化别名
	public static final String EYE = VISIBLE;            // 可见图标
	public static final String MENU = MORE_HORIZ;       // 菜单/拖拽提示图标
	public static final String MUSIC_NOTE = NOTE;       // 音符图标

	// --- grouped icons (BeatBlock.ttf) ---
	public static final class Play {
		public static final String PLAY = "\uF024"; // play
		public static final String PAUSE = "\uF02E"; // pause
		public static final String STOP = "\uF094"; // stop
		public static final String RECORD = "\uF008"; // record
		public static final String REWIND_START = "\uF100"; // rewind-start
		public static final String FORWARD_END = "\uF0EF"; // forward-end
		public static final String REWIND = "\uF103"; // rewind
		public static final String FORWARD = "\uF0F2"; // forward
		public static final String LOOP = "\uF08F"; // loop
		public static final String LOOP_ONE = "\uF08C"; // loop-one
		public static final String SHUFFLE = "\uF0C7"; // shuffle
		public static final String METRONOME = "\uF074"; // metronome
		private Play() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Action {
		public static final String ADD = "\uF105"; // add
		public static final String REMOVE = "\uF002"; // remove
		public static final String CLOSE = "\uF049"; // close
		public static final String CHECK = "\uF054"; // check
		public static final String UNDO = "\uF070"; // undo
		public static final String REDO = "\uF006"; // redo
		public static final String COPY = "\uF03F"; // copy
		public static final String PASTE = "\uF034"; // paste
		public static final String CUT = "\uF03B"; // cut
		public static final String SEARCH = "\uF0DF"; // search
		public static final String SETTINGS = "\uF0CD"; // settings
		public static final String REFRESH = "\uF004"; // refresh
		public static final String MORE_HORIZ = "\uF06B"; // more-horiz
		public static final String MORE_VERT = "\uF068"; // more-vert
		public static final String COLLAPSE = "\uF045"; // collapse
		public static final String EXPAND = "\uF00B"; // expand
		public static final String ARROW_LEFT = "\uF0DB"; // arrow-left
		public static final String ARROW_RIGHT = "\uF0D8"; // arrow-right
		public static final String ARROW_UP = "\uF0D5"; // arrow-up
		public static final String ARROW_DOWN = "\uF0DE"; // arrow-down
		public static final String DRAG_HANDLE = "\uF025"; // drag-handle
		public static final String PIN = "\uF028"; // pin
		public static final String LOCK = "\uF095"; // lock
		public static final String UNLOCK = "\uF06D"; // unlock
		public static final String VISIBLE = "\uF067"; // visible
		public static final String HIDDEN = "\uF0CB"; // hidden
		public static final String WARNING = "\uF061"; // warning
		public static final String INFO = "\uF0BF"; // info
		public static final String HELP = "\uF0CE"; // help
		public static final String LINK = "\uF09B"; // link
		private Action() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Tool {
		public static final String SELECT = "\uF0D3"; // select
		public static final String MARQUEE = "\uF080"; // marquee
		public static final String PENCIL = "\uF02C"; // pencil
		public static final String ERASER = "\uF00F"; // eraser
		public static final String SLICE = "\uF0C1"; // slice
		public static final String STRETCH = "\uF091"; // stretch
		public static final String BEAT_EDIT = "\uF0B7"; // beat-edit
		public static final String TAP = "\uF088"; // tap
		public static final String SNAP_GRID = "\uF0B8"; // snap-grid
		public static final String ZONE = "\uF055"; // zone
		public static final String BLUEPRINT = "\uF096"; // blueprint
		public static final String CAMERA_EDIT = "\uF075"; // camera-edit
		private Tool() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Timeline {
		public static final String TIMELINE = "\uF07F"; // timeline
		public static final String PLAYHEAD = "\uF022"; // playhead
		public static final String BEAT_LINE = "\uF0B1"; // beat-line
		public static final String BAR_MARKER = "\uF0BD"; // bar-marker
		public static final String ADD_TRACK = "\uF102"; // add-track
		public static final String MUTE = "\uF05F"; // mute
		public static final String SOLO = "\uF0AF"; // solo
		public static final String TRACK_LOCK = "\uF076"; // track-lock
		public static final String WAVEFORM = "\uF05B"; // waveform
		public static final String FIT_VIEW = "\uF104"; // fit-view
		public static final String SEGMENT = "\uF0D9"; // segment
		public static final String SEGMENT_GROUP = "\uF0D6"; // segment-group
		public static final String KEYFRAME = "\uF0B3"; // keyframe
		public static final String KEYFRAME_GROUP = "\uF0B0"; // keyframe-group
		public static final String MARKER = "\uF083"; // marker
		public static final String ZOOM_IN_TIME = "\uF04F"; // zoom-in-time
		public static final String ZOOM_OUT_TIME = "\uF04C"; // zoom-out-time
		public static final String TRACK_COLLAPSE = "\uF07C"; // track-collapse
		public static final String TRACK_EXPAND = "\uF079"; // track-expand
		private Timeline() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Anim {
		public static final String ARRIVE = "\uF0E1"; // arrive
		public static final String DEPART = "\uF035"; // depart
		public static final String WAVE_RISE = "\uF058"; // wave-rise
		public static final String EXPLODE = "\uF009"; // explode
		public static final String DROP_BUILD = "\uF01F"; // drop-build
		public static final String SPIRAL = "\uF0A3"; // spiral
		public static final String DOMINO = "\uF027"; // domino
		public static final String MIRROR = "\uF06E"; // mirror
		public static final String RAIN = "\uF00C"; // rain
		public static final String RING = "\uF0FD"; // ring
		public static final String RADIAL = "\uF010"; // radial
		public static final String CRESCENDO = "\uF03D"; // crescendo
		public static final String PULSE = "\uF012"; // pulse
		public static final String ROTATE = "\uF0FA"; // rotate
		public static final String SCALE_IN = "\uF0EB"; // scale-in
		public static final String SCALE_OUT = "\uF0E8"; // scale-out
		private Anim() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Beat {
		public static final String BAND_LOW = "\uF0C3"; // band-low
		public static final String BAND_MID = "\uF0C0"; // band-mid
		public static final String BAND_HIGH = "\uF0C6"; // band-high
		public static final String BEAT_POINT = "\uF0AB"; // beat-point
		public static final String BEAT_GROUP = "\uF0B4"; // beat-group
		public static final String ANCHOR_ARRIVE = "\uF0F0"; // anchor-arrive
		public static final String ANCHOR_DEPART = "\uF0ED"; // anchor-depart
		public static final String BPM = "\uF08D"; // bpm
		public static final String ENERGY = "\uF011"; // energy
		public static final String SECTION = "\uF0DC"; // section
		public static final String INTRO = "\uF0B9"; // intro
		public static final String VERSE = "\uF06A"; // verse
		public static final String CHORUS = "\uF051"; // chorus
		public static final String BRIDGE = "\uF08A"; // bridge
		public static final String OUTRO = "\uF042"; // outro
		private Beat() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Block {
		public static final String BLOCK = "\uF0A8"; // block
		public static final String BLOCK_GROUP = "\uF0A5"; // block-group
		public static final String BLOCK_SELECT = "\uF099"; // block-select
		public static final String BLOCK_PLACE = "\uF09F"; // block-place
		public static final String BLOCK_REMOVE = "\uF09C"; // block-remove
		public static final String BLOCK_MATERIAL = "\uF0A2"; // block-material
		public static final String STAGE_ZONE = "\uF097"; // stage-zone
		public static final String BLUEPRINT_FILE = "\uF093"; // blueprint-file
		public static final String SCENE = "\uF0E2"; // scene
		public static final String THEME = "\uF085"; // theme
		public static final String PALETTE = "\uF040"; // palette
		private Block() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Camera {
		public static final String CAMERA = "\uF07B"; // camera
		public static final String CAMERA_KEYFRAME = "\uF072"; // camera-keyframe
		public static final String CAMERA_TRACK = "\uF06F"; // camera-track
		public static final String CAM_PUSH_IN = "\uF060"; // cam-push-in
		public static final String CAM_PULL_OUT = "\uF063"; // cam-pull-out
		public static final String CAM_ORBIT = "\uF066"; // cam-orbit
		public static final String CAM_TOP = "\uF05A"; // cam-top
		public static final String CAM_LOW = "\uF069"; // cam-low
		public static final String CAM_FOV = "\uF06C"; // cam-fov
		public static final String CAM_SHAKE = "\uF05D"; // cam-shake
		public static final String CAM_CUT = "\uF07E"; // cam-cut
		public static final String CAM_AUTO = "\uF081"; // cam-auto
		private Camera() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Audio {
		public static final String AUDIO_FILE = "\uF0D2"; // audio-file
		public static final String NOTE = "\uF04D"; // note
		public static final String NOTES = "\uF04A"; // notes
		public static final String WAVE = "\uF05E"; // wave
		public static final String VOLUME = "\uF064"; // volume
		public static final String MUTE_AUDIO = "\uF05C"; // mute-audio
		public static final String HEADPHONES = "\uF0D7"; // headphones
		public static final String MIC = "\uF071"; // mic
		public static final String BEATMAP = "\uF0AE"; // beatmap
		public static final String ANALYZING = "\uF0F3"; // analyzing
		public static final String ANALYZED = "\uF0F6"; // analyzed
		public static final String MEDIA_BIN = "\uF07A"; // media-bin
		private Audio() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Io {
		public static final String IMPORT = "\uF0C2"; // import
		public static final String EXPORT = "\uF007"; // export
		public static final String DRAG_IN = "\uF023"; // drag-in
		public static final String DRAG_OUT = "\uF021"; // drag-out
		public static final String SAVE = "\uF0F1"; // save
		public static final String SAVE_AS = "\uF0EE"; // save-as
		public static final String OPEN_FILE = "\uF046"; // open-file
		public static final String NEW_PROJECT = "\uF059"; // new-project
		public static final String PROJECT_FILE = "\uF014"; // project-file
		public static final String EXPORT_VIDEO = "\uF003"; // export-video
		public static final String SHARE = "\uF0CA"; // share
		public static final String CLOUD_SYNC = "\uF047"; // cloud-sync
		private Io() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Fx {
		public static final String INTENSITY = "\uF0BC"; // intensity
		public static final String EASE_IN = "\uF019"; // ease-in
		public static final String EASE_OUT = "\uF015"; // ease-out
		public static final String EASE_IN_OUT = "\uF017"; // ease-in-out
		public static final String LINEAR = "\uF0A1"; // linear
		public static final String BOUNCE = "\uF090"; // bounce
		public static final String RADIUS = "\uF00E"; // radius
		public static final String HEIGHT = "\uF0D4"; // height
		public static final String DURATION = "\uF01B"; // duration
		public static final String RANDOM = "\uF00A"; // random
		public static final String AXIS = "\uF0C9"; // axis
		public static final String OFFSET = "\uF048"; // offset
		public static final String DELAY = "\uF039"; // delay
		public static final String REPEAT = "\uF000"; // repeat
		private Fx() { throw new AssertionError("Cannot instantiate" ); }
	}

	public static final class Layout {
		public static final String PANEL_LEFT = "\uF03A"; // panel-left
		public static final String PANEL_RIGHT = "\uF038"; // panel-right
		public static final String PANEL_BOTTOM = "\uF03C"; // panel-bottom
		public static final String FULLSCREEN = "\uF0EC"; // fullscreen
		public static final String EXIT_FULLSCREEN = "\uF00D"; // exit-fullscreen
		public static final String SPLIT_VIEW = "\uF09D"; // split-view
		public static final String GRID_VIEW = "\uF0DD"; // grid-view
		public static final String LIST_VIEW = "\uF098"; // list-view
		public static final String DOCK = "\uF029"; // dock
		public static final String FLOAT = "\uF0FE"; // float
		private Layout() { throw new AssertionError("Cannot instantiate" ); }
	}
}

