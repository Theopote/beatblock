package com.beatblock.audio.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PythonRuntimeHealthMonitorTest {

	@Test
	void returnsDefaultSummaryWithoutBlocking() {
		PythonRuntimeHealthMonitor monitor = new PythonRuntimeHealthMonitor(new PythonEnvironmentDiagnostics());
		try {
			String summary = monitor.getRuntimeSummary();
			assertFalse(summary.isBlank());
			assertNotNull(monitor.getRuntimeHealthSnapshot());
		} finally {
			monitor.shutdown();
		}
	}
}
