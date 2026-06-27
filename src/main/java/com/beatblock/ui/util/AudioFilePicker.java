package com.beatblock.ui.util;

import imgui.type.ImString;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** 跨平台音频文件选择（AWT → Swing → PowerShell 回退链）。 */
public final class AudioFilePicker {

	private AudioFilePicker() {
	}

	public static String choose(ImString importPath, Consumer<String> onError) {
		try {
			String nativePath = openNativeDialog(importPath);
			if (nativePath != null && !nativePath.isBlank()) {
				return nativePath;
			}
		} catch (Throwable nativeErr) {
			try {
				String swingPath = openSwingDialog(importPath);
				if (swingPath != null && !swingPath.isBlank()) {
					return swingPath;
				}
			} catch (Throwable swingErr) {
				try {
					String psPath = openWindowsPowerShellDialog(importPath);
					if (psPath != null && !psPath.isBlank()) {
						return psPath;
					}
				} catch (Throwable psErr) {
					if (onError != null) {
						onError.accept("打开文件选择器失败: "
							+ describeThrowable(nativeErr)
							+ " | 备用方案失败: "
							+ describeThrowable(swingErr)
							+ " | PowerShell 方案失败: "
							+ describeThrowable(psErr));
					}
					return null;
				}
				if (onError != null) {
					onError.accept("打开文件选择器失败: "
						+ describeThrowable(nativeErr)
						+ " | 备用方案失败: "
						+ describeThrowable(swingErr));
				}
				return null;
			}
			if (onError != null) {
				onError.accept("打开文件选择器失败: " + describeThrowable(nativeErr));
			}
			return null;
		}
		return null;
	}

	private static String openNativeDialog(ImString importPath) throws Exception {
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
				if (!seed.getName().isBlank()) {
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

	private static String openSwingDialog(ImString importPath) throws Exception {
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

	private static String openWindowsPowerShellDialog(ImString importPath) throws Exception {
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
}
