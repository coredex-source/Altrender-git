/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug.harness;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import javax.imageio.ImageIO;

/** Writes the harness's files: screenshots (PNG), {@code .meta} (key=value) and benchmark CSVs. */
public final class Output {

	private Output() {
	}

	/** {@code rgb}: bottom row first, as read from GL. */
	public static void png(Path file, ByteBuffer rgb, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			int src = (height - 1 - y) * width * 3;
			for (int x = 0; x < width; x++) {
				int o = src + x * 3;
				row[x] = (rgb.get(o) & 0xFF) << 16 | (rgb.get(o + 1) & 0xFF) << 8 | rgb.get(o + 2) & 0xFF;
			}
			image.setRGB(0, y, width, 1, row, 0, width);
		}
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}

	public static void meta(Path file, Map<String, ?> values) throws IOException {
		StringBuilder sb = new StringBuilder();
		values.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
		Files.createDirectories(file.getParent());
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
	}

	/** Benchmark summary of frame times (ns): frames, average FPS, 1% low (FPS over the slowest 1%), p50, p99 (ms). */
	public static Map<String, String> benchStats(long[] frameNanos) {
		long[] sorted = frameNanos.clone();
		Arrays.sort(sorted);
		long total = 0;
		for (long f : frameNanos) {
			total += f;
		}
		int slow = Math.max(1, sorted.length / 100);
		long slowTotal = 0;
		for (int k = sorted.length - slow; k < sorted.length; k++) {
			slowTotal += sorted[k];
		}
		return Map.of(
				"bench_frames", String.valueOf(frameNanos.length),
				"bench_avg_fps", String.format("%.1f", frameNanos.length * 1e9 / Math.max(1, total)),
				"bench_low1_fps", String.format("%.1f", slow * 1e9 / Math.max(1, slowTotal)),
				"bench_p50_ms", String.format("%.3f", sorted[sorted.length / 2] / 1e6),
				"bench_p99_ms", String.format("%.3f", sorted[Math.min(sorted.length - 1, sorted.length * 99 / 100)] / 1e6));
	}

	/**
	 * The CPU side of the frames: each frame's time without its time in the buffer swap (where the render thread waits
	 * for the GPU), median and average (development.md 7.1).
	 */
	public static Map<String, String> cpuStats(long[] frameNanos, long[] swapNanos) {
		long[] cpu = new long[frameNanos.length];
		long total = 0;
		long swaps = 0;
		for (int k = 0; k < cpu.length; k++) {
			cpu[k] = Math.max(0, frameNanos[k] - swapNanos[k]);
			total += cpu[k];
			swaps += swapNanos[k];
		}
		long[] sorted = cpu.clone();
		Arrays.sort(sorted);
		int n = Math.max(1, cpu.length);
		return Map.of(
				"bench_cpu_p50_ms", String.format("%.3f", sorted.length > 0 ? sorted[sorted.length / 2] / 1e6 : 0),
				"bench_cpu_avg_ms", String.format("%.3f", total / 1e6 / n),
				"bench_swap_avg_ms", String.format("%.3f", swaps / 1e6 / n));
	}

	public static void csv(Path file, long[] frameNanos) throws IOException {
		StringBuilder sb = new StringBuilder("frame,ms\n");
		for (int k = 0; k < frameNanos.length; k++) {
			sb.append(k).append(',').append(String.format("%.4f", frameNanos[k] / 1e6)).append('\n');
		}
		Files.createDirectories(file.getParent());
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
	}
}
