/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debugscreen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.Log;
import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionGroup;
import com.coredex.altrender.api.options.OptionPage;
import com.coredex.altrender.api.options.OptionPageProvider;
import com.coredex.altrender.api.options.OptionStorage;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.UiHost;
import com.coredex.altrender.ui.UiRenderer;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.util.Json;

/**
 * Altrender's debug screen (development.md 5.3): entries in two columns as the newer versions lay them out, each
 * shown always, with F3, or never (saved in {@code config/altrender-debug.json}); a frame-time chart with percentile
 * lines (F3+1) and a GPU time per pass breakdown (F3+2). Version adapters and other mods {@link #register} entries.
 * Drawn by Altrender's UI renderer in one batch. Render thread only.
 */
public final class DebugScreen {

	private static final int LINE = 9;
	private static final int BG = 0x90505050;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int HISTORY = 240;

	private static final List<DebugEntry> ENTRIES = new ArrayList<>();
	private static final Map<String, DebugEntry.Visibility> VISIBILITY = new LinkedHashMap<>();
	private static Path file;
	private static boolean frameChart = true;
	private static boolean gpuChart;
	private static final long[] frames = new long[HISTORY];
	private static int frameIndex;
	private static int frameCount;
	private static long lastFrame;
	/** CPU time drawing the debug screen, averaged (the 5.3 cost check). */
	private static double drawMs;
	/** The entries' lines, made again ten times a second (the chart's bars every frame). */
	private static final List<String> left = new ArrayList<>();
	private static final List<String> right = new ArrayList<>();
	private static int[] leftWidths = new int[0];
	private static int[] rightWidths = new int[0];
	private static long linesTime;
	private static boolean linesF3;
	/** The chart's statistics line, four times a second. */
	private static String chartLabel = "";
	private static double p95;
	private static double p99;
	private static long chartTime;

	private DebugScreen() {
	}

	/** Adds an entry (in order); the first registration of an id wins. */
	public static synchronized void register(DebugEntry entry) {
		for (DebugEntry e : ENTRIES) {
			if (e.id().equals(entry.id())) {
				return;
			}
		}
		ENTRIES.add(entry);
	}

	public static List<DebugEntry> entries() {
		return List.copyOf(ENTRIES);
	}

	/**
	 * Through the options page (as F3+F6 shows it): {@code id} set to Never and applied, read back from the file,
	 * then its old visibility restored; returns what failed (the harness's check, development.md 5.3).
	 */
	public static List<String> selfTest(String id) {
		List<String> failed = new ArrayList<>();
		DebugEntry entry = null;
		for (DebugEntry e : ENTRIES) {
			if (e.id().equals(id)) {
				entry = e;
			}
		}
		if (entry == null || file == null) {
			failed.add("no entry " + id + " or no config file");
			return failed;
		}
		DebugEntry.Visibility before = visibility(entry);
		OptionPage page = optionsProvider().pages().get(0);
		for (Option<?> o : page.options()) {
			if (o.id().equals("entry." + id)) {
				@SuppressWarnings("unchecked")
				Option<DebugEntry.Visibility> v = (Option<DebugEntry.Visibility>) o;
				v.setValue(DebugEntry.Visibility.NEVER);
				v.applyChanges();
				v.storage().save(java.util.Set.of());
			}
		}
		VISIBILITY.clear();
		load(file.getParent().getParent());
		if (visibility(entry) != DebugEntry.Visibility.NEVER) {
			failed.add(id + " not saved as never (" + visibility(entry) + ")");
		}
		setVisibility(entry, before);
		save();
		return failed;
	}

	/** Loads saved visibilities from {@code gameDir}'s config. */
	public static void load(Path gameDir) {
		file = gameDir.resolve("config").resolve("altrender-debug.json");
		if (!Files.exists(file)) {
			return;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
			if (j.get("entries") instanceof Map<?, ?> m) {
				for (Map.Entry<?, ?> e : m.entrySet()) {
					try {
						VISIBILITY.put(String.valueOf(e.getKey()),
								DebugEntry.Visibility.valueOf(String.valueOf(e.getValue()).toUpperCase(java.util.Locale.ROOT)));
					} catch (IllegalArgumentException ignored) {
						// unknown value: the entry's default
					}
				}
			}
			frameChart = !(j.get("frameChart") instanceof Boolean b) || b;
			gpuChart = j.get("gpuChart") instanceof Boolean b && b;
		} catch (IOException | RuntimeException e) {
			Log.warn("debug screen: can't read " + file + ": " + e.getMessage());
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		StringBuilder b = new StringBuilder("{\n  \"frameChart\": ").append(frameChart).append(",\n  \"gpuChart\": ")
				.append(gpuChart).append(",\n  \"entries\": {");
		int i = 0;
		for (Map.Entry<String, DebugEntry.Visibility> e : VISIBILITY.entrySet()) {
			b.append(i++ == 0 ? "\n" : ",\n").append("    \"").append(e.getKey()).append("\": \"")
					.append(e.getValue().name().toLowerCase(java.util.Locale.ROOT)).append('"');
		}
		b.append(i > 0 ? "\n  }\n}\n" : "}\n}\n");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Log.warn("debug screen: can't write " + file + ": " + e.getMessage());
		}
	}

	public static DebugEntry.Visibility visibility(DebugEntry e) {
		return VISIBILITY.getOrDefault(e.id(), e.defaultVisibility());
	}

	public static void setVisibility(DebugEntry e, DebugEntry.Visibility v) {
		if (v == e.defaultVisibility()) {
			VISIBILITY.remove(e.id());
		} else {
			VISIBILITY.put(e.id(), v);
		}
	}

	/** F3 + a number: 1 the frame chart, 2 the GPU breakdown. Returns whether it was one of ours. */
	public static boolean combo(int number) {
		switch (number) {
			case 1 -> frameChart = !frameChart;
			case 2 -> gpuChart = !gpuChart;
			default -> {
				return false;
			}
		}
		save();
		return true;
	}

	/** Whether the GPU breakdown wants per-pass timers (they cost a few queries a frame, so only while shown). */
	public static boolean wantsGpuTimers(boolean f3) {
		return f3 && gpuChart;
	}

	/** Called once per frame (frame times for the chart). */
	public static void frame() {
		long now = System.nanoTime();
		if (lastFrame != 0) {
			frames[frameIndex] = now - lastFrame;
			frameIndex = (frameIndex + 1) % HISTORY;
			frameCount = Math.min(HISTORY, frameCount + 1);
		}
		lastFrame = now;
	}

	/**
	 * The last second of frames: average FPS and the lowest (from the longest frame), {fps, minFps}; 0 before any.
	 */
	public static double[] lastSecond() {
		long sum = 0;
		long longest = 0;
		int n = 0;
		for (int i = 1; i <= frameCount && sum < 1_000_000_000L; i++) {
			long t = frames[Math.floorMod(frameIndex - i, HISTORY)];
			sum += t;
			longest = Math.max(longest, t);
			n++;
		}
		if (n == 0 || sum == 0) {
			return new double[] {0, 0};
		}
		return new double[] {n * 1e9 / sum, 1e9 / longest};
	}

	/** Whether anything shows (F3 on, or an entry shown always). */
	public static boolean active(boolean f3) {
		if (f3) {
			return true;
		}
		for (DebugEntry e : ENTRIES) {
			if (visibility(e) == DebugEntry.Visibility.ALWAYS) {
				return true;
			}
		}
		return false;
	}

	/** The average CPU time drawing the screen, ms. */
	public static double drawMs() {
		return drawMs;
	}

	/** Draws the screen over the game's HUD (GUI units {@code width} × {@code height}). */
	public static void render(UiRenderer r, UiHost host, boolean f3, int width, int height) {
		long start = System.nanoTime();
		r.begin(host);
		try {
			long now = System.nanoTime();
			if (now - linesTime >= 100_000_000L || linesF3 != f3) {
				linesTime = now;
				linesF3 = f3;
				lines(f3);
				leftWidths = widths(r, left);
				rightWidths = widths(r, right);
			}
			column(r, left, leftWidths, width, false);
			column(r, right, rightWidths, width, true);
			if (f3 && frameChart) {
				frameChart(r, height);
			}
			if (f3 && gpuChart) {
				gpuChart(r, width, height);
			}
		} finally {
			r.end();
		}
		double ms = (System.nanoTime() - start) / 1e6;
		drawMs = drawMs == 0 ? ms : drawMs * 0.95 + ms * 0.05;
	}

	private static void lines(boolean f3) {
		left.clear();
		right.clear();
		{
			for (DebugEntry e : ENTRIES) {
				DebugEntry.Visibility v = visibility(e);
				if (v == DebugEntry.Visibility.ALWAYS || v == DebugEntry.Visibility.F3 && f3) {
					List<String> target = e.column() == DebugEntry.Column.LEFT ? left : right;
					int before = target.size();
					try {
						e.lines().accept(target);
					} catch (RuntimeException ex) {
						target.add(e.id() + ": " + ex);
					}
					if (target.size() > before) {
						target.add("");
					}
				}
			}
		}
	}

	private static int[] widths(UiRenderer r, List<String> lines) {
		int[] w = new int[lines.size()];
		for (int i = 0; i < w.length; i++) {
			w[i] = r.getStringWidth(lines.get(i));
		}
		return w;
	}

	private static void column(UiRenderer r, List<String> lines, int[] widths, int width, boolean right) {
		for (int i = 0; i < lines.size(); i++) {
			String s = lines.get(i);
			if (s.isEmpty()) {
				continue;
			}
			int w = widths[i];
			int x = right ? width - 2 - w : 2;
			int y = 2 + i * LINE;
			r.fill(x - 1, y - 1, x + w + 1, y + LINE - 1, BG);
			r.drawString(TextComponent.literal(s), x, y, TEXT, false);
		}
	}

	/** The last frames as bars (a unit per frame, 1 unit per ms up to 60), with the 30/60 FPS and percentile lines. */
	private static void frameChart(UiRenderer r, int height) {
		int n = frameCount;
		if (n == 0) {
			return;
		}
		int chartHeight = 60;
		int x0 = 2;
		int y0 = height - 2;
		r.fill(x0 - 1, y0 - chartHeight - 12, x0 + HISTORY + 1, y0 + 1, BG);
		for (int i = 0; i < n; i++) {
			int idx = Math.floorMod(frameIndex - n + i, HISTORY);
			double ms = frames[idx] / 1e6;
			int h = (int) Math.min(chartHeight, Math.max(1, Math.round(ms * 2)));
			int color = ms <= 1000.0 / 60 ? 0xFF00C000 : ms <= 1000.0 / 30 ? 0xFFE0C000 : 0xFFE03030;
			r.fill(x0 + (HISTORY - n) + i, y0 - h, x0 + (HISTORY - n) + i + 1, y0, color);
		}
		long now = System.nanoTime();
		if (now - chartTime >= 250_000_000L) {
			chartTime = now;
			long[] sorted = new long[n];
			long sum = 0;
			for (int i = 0; i < n; i++) {
				sorted[i] = frames[Math.floorMod(frameIndex - n + i, HISTORY)];
				sum += sorted[i];
			}
			Arrays.sort(sorted);
			p95 = sorted[Math.min(n - 1, n * 95 / 100)] / 1e6;
			p99 = sorted[Math.min(n - 1, n * 99 / 100)] / 1e6;
			chartLabel = String.format(java.util.Locale.ROOT, "%.1f ms avg  p50 %.1f  p95 %.1f  p99 %.1f  max %.1f",
					sum / 1e6 / n, sorted[n / 2] / 1e6, p95, p99, sorted[n - 1] / 1e6);
		}
		hline(r, x0, y0, 1000.0 / 60, 0x80FFFFFF);
		hline(r, x0, y0, 1000.0 / 30, 0x80FFFFFF);
		hline(r, x0, y0, p95, 0xC0FFE000);
		hline(r, x0, y0, p99, 0xC0FF5050);
		r.drawString(TextComponent.literal(chartLabel), x0 + 1, y0 - chartHeight - 10, TEXT, false);
	}

	private static void hline(UiRenderer r, int x0, int y0, double ms, int color) {
		int y = y0 - (int) Math.min(60, Math.round(ms * 2));
		r.fill(x0, y, x0 + HISTORY, y + 1, color);
	}

	/** Each pass's GPU time (the last second's average), as bars against the frame's. */
	private static void gpuChart(UiRenderer r, int width, int height) {
		Renderer renderer = Renderer.instance();
		String[] names = Renderer.gpuPassNames();
		double[] ms = renderer != null ? renderer.gpuPassMs() : null;
		int w = 200;
		int x0 = width - w - 2;
		int rows = names.length + 1;
		int y0 = height - 2 - rows * LINE;
		r.fill(x0 - 1, y0 - 1, x0 + w + 1, height - 1, BG);
		if (ms == null) {
			r.drawString(TextComponent.literal("GPU timers: waiting (or not supported)"), x0 + 1, y0, TEXT, false);
			return;
		}
		double frame = Math.max(0.001, ms[0]);
		r.drawString(TextComponent.literal(String.format(java.util.Locale.ROOT, "GPU frame %.2f ms", ms[0])), x0 + 1, y0,
				TEXT, false);
		for (int p = 1; p < names.length; p++) {
			int y = y0 + p * LINE;
			int bar = (int) Math.round(Math.min(1.0, ms[p] / frame) * (w - 90));
			r.fill(x0 + 88, y, x0 + 88 + Math.max(1, bar), y + LINE - 2, 0xFF00CBCB);
			r.drawString(TextComponent.literal(String.format(java.util.Locale.ROOT, "%-9s %5.2f", names[p], ms[p])),
					x0 + 1, y, TEXT, false);
		}
	}

	/** The debug options as a settings page (F3+F6): the charts and each entry's visibility. */
	public static OptionPageProvider optionsProvider() {
		return new OptionPageProvider() {
			@Override
			public String modId() {
				return "altrender";
			}

			@Override
			public String modName() {
				return "Altrender";
			}

			@Override
			public List<OptionPage> pages() {
				OptionStorage storage = flags -> save();
				List<Option<?>> charts = List.of(
						Option.<Boolean>builder("frame_chart").name("altrender.debug.frame_chart")
								.tooltip("altrender.debug.frame_chart.tooltip").control(new Control.TickBox())
								.binding(() -> frameChart, v -> frameChart = v).storage(storage).build(),
						Option.<Boolean>builder("gpu_chart").name("altrender.debug.gpu_chart")
								.tooltip("altrender.debug.gpu_chart.tooltip").control(new Control.TickBox())
								.binding(() -> gpuChart, v -> gpuChart = v).storage(storage).build());
				Map<DebugEntry.Visibility, String> names = new EnumMap<>(DebugEntry.Visibility.class);
				names.put(DebugEntry.Visibility.ALWAYS, "altrender.debug.always");
				names.put(DebugEntry.Visibility.F3, "altrender.debug.with_f3");
				names.put(DebugEntry.Visibility.NEVER, "altrender.debug.never");
				List<Option<?>> entries = new ArrayList<>();
				for (DebugEntry e : ENTRIES) {
					entries.add(Option.<DebugEntry.Visibility>builder("entry." + e.id()).name(e.name())
							.control(new Control.Cycling<>(List.of(DebugEntry.Visibility.values()), names::get))
							.binding(() -> visibility(e), v -> setVisibility(e, v)).storage(storage).build());
				}
				return List.of(new OptionPage("altrender.debug", "altrender.debug.page",
						List.of(OptionGroup.of(charts), OptionGroup.of(entries))));
			}
		};
	}
}
