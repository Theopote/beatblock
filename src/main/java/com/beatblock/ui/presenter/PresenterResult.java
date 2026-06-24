package com.beatblock.ui.presenter;

/**
 * Presenter 操作结果：成功可带提示文案，失败带错误信息。
 */
public sealed interface PresenterResult {

	record Success(String message) implements PresenterResult {
		public Success {
			if (message == null) {
				message = "";
			}
		}

		public static Success silent() {
			return new Success("");
		}
	}

	record Failure(String message) implements PresenterResult {}

	static Success success(String message) {
		return new Success(message != null ? message : "");
	}

	static Failure failure(String message) {
		return new Failure(message != null ? message : "");
	}

	default boolean ok() {
		return this instanceof Success;
	}

	default String messageOrEmpty() {
		return switch (this) {
			case Success s -> s.message();
			case Failure f -> f.message();
		};
	}
}
