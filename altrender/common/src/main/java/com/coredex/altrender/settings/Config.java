/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.coredex.altrender.Log;
import com.coredex.altrender.util.Json;

/**
 * Altrender's settings, {@code config/altrender.json} (development.md 5.2: the game's own options stay in its
 * {@code options.txt}). Read live by the renderer; the video settings change them and {@link #save} them. Unknown
 * or broken values fall back to the defaults, with a log line. Render thread only.
 */
public final class Config {

	/** {@code auto} (the highest tier the driver supports) or {@code gl33}. */
	private String tier = "auto";
	private boolean vsync = true;
	/** Frame caps when minimised / in the background; 0 = none. */
	private int idleFps = 10;
	private int unfocusedFps = 0;
	/** Meshing threads; 0 = automatic (cores - 2). */
	private int meshThreads = 0;
	/** Vertex bytes uploaded per frame at most, MB. */
	private int uploadBudgetMb = 16;
	private boolean caveCulling = true;
	private boolean entityCulling = true;
	private boolean immediateStream = false;
	private boolean gpuCommands = false;
	/** The FPS counter (development.md 5.4): off, simple, advanced; GPU time; corner; text contrast. */
	private String fpsCounter = "off";
	private boolean fpsCounterGpu = false;
	private String fpsCounterCorner = "top_left";
	private String fpsCounterContrast = "background";
	/** Altrender's debug screen in place of the game's F3 (owner: on by default). */
	private boolean modernDebugScreen = true;
	/** Terrain render distance in chunks; 0 = the game's own setting (its four steps). */
	private int renderDistance = 0;
	/** Frame cap while focused, 10..260 (260 = none); -1 until set (the version adapter takes the game's own cap). */
	private int maxFps = -1;

	public static final Config DEFAULTS = new Config();

	private Path gameDir;

	public static Path file(Path gameDir) {
		return gameDir.resolve("config").resolve("altrender.json");
	}

	/** Reads the file; a missing file is written with the defaults, a broken one is logged and left alone. */
	public static Config load(Path gameDir) {
		Config c = new Config();
		c.gameDir = gameDir;
		Path file = file(gameDir);
		if (!Files.exists(file)) {
			c.save();
			return c;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
			Object tier = j.getOrDefault("tier", c.tier);
			if ("auto".equals(tier) || "gl33".equals(tier)) {
				c.tier = (String) tier;
			} else {
				Log.warn("config: " + file + ": tier must be \"auto\" or \"gl33\", not " + tier + "; using auto");
			}
			c.vsync = bool(j, "vsync", c.vsync);
			c.idleFps = number(j, "idleFps", c.idleFps, 0, 240);
			c.unfocusedFps = number(j, "unfocusedFps", c.unfocusedFps, 0, 240);
			c.meshThreads = number(j, "meshThreads", c.meshThreads, 0, 64);
			c.uploadBudgetMb = number(j, "uploadBudgetMB", c.uploadBudgetMb, 1, 256);
			c.caveCulling = bool(j, "caveCulling", c.caveCulling);
			c.entityCulling = bool(j, "entityCulling", c.entityCulling);
			c.immediateStream = bool(j, "immediateStream", c.immediateStream);
			c.gpuCommands = bool(j, "gpuCommands", c.gpuCommands);
			c.renderDistance = number(j, "renderDistance", c.renderDistance, 0, 32);
			c.modernDebugScreen = bool(j, "modernDebugScreen", c.modernDebugScreen);
			c.fpsCounter = choice(j, "fpsCounter", c.fpsCounter, "off", "simple", "advanced");
			c.fpsCounterGpu = bool(j, "fpsCounterGpu", c.fpsCounterGpu);
			c.fpsCounterCorner = choice(j, "fpsCounterCorner", c.fpsCounterCorner, "top_left", "top_right",
					"bottom_left", "bottom_right");
			c.fpsCounterContrast = choice(j, "fpsCounterContrast", c.fpsCounterContrast, "none", "background", "shadow");
			c.maxFps = j.containsKey("maxFps") ? number(j, "maxFps", 260, 10, 260) : -1;
		} catch (IOException | RuntimeException e) {
			Log.warn("config: can't read " + file + " (" + e.getMessage() + "); using the defaults");
		}
		return c;
	}

	public void save() {
		if (gameDir == null) {
			return;
		}
		Map<String, Object> j = new LinkedHashMap<>();
		j.put("tier", tier);
		j.put("vsync", vsync);
		j.put("idleFps", idleFps);
		j.put("unfocusedFps", unfocusedFps);
		j.put("meshThreads", meshThreads);
		j.put("uploadBudgetMB", uploadBudgetMb);
		j.put("caveCulling", caveCulling);
		j.put("entityCulling", entityCulling);
		j.put("immediateStream", immediateStream);
		j.put("gpuCommands", gpuCommands);
		j.put("renderDistance", renderDistance);
		j.put("modernDebugScreen", modernDebugScreen);
		j.put("fpsCounter", fpsCounter);
		j.put("fpsCounterGpu", fpsCounterGpu);
		j.put("fpsCounterCorner", fpsCounterCorner);
		j.put("fpsCounterContrast", fpsCounterContrast);
		if (maxFps > 0) {
			j.put("maxFps", maxFps);
		}
		StringBuilder b = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, Object> e : j.entrySet()) {
			b.append("  \"").append(e.getKey()).append("\": ");
			b.append(e.getValue() instanceof String s ? "\"" + s + "\"" : String.valueOf(e.getValue()));
			b.append(++i < j.size() ? ",\n" : "\n");
		}
		b.append("}\n");
		Path file = file(gameDir);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Log.warn("config: can't write " + file + ": " + e.getMessage());
		}
	}

	private static int number(Map<String, Object> j, String key, int fallback, int min, int max) {
		Object v = j.get(key);
		return v instanceof Number n ? Math.max(min, Math.min(max, n.intValue())) : fallback;
	}

	private static String choice(Map<String, Object> j, String key, String fallback, String... allowed) {
		Object v = j.get(key);
		for (String a : allowed) {
			if (a.equals(v)) {
				return a;
			}
		}
		return fallback;
	}

	private static boolean bool(Map<String, Object> j, String key, boolean fallback) {
		Object v = j.get(key);
		return v instanceof Boolean b ? b : fallback;
	}

	public String tier() {
		return tier;
	}

	public void setTier(String tier) {
		this.tier = tier;
	}

	public boolean vsync() {
		return vsync;
	}

	public void setVsync(boolean vsync) {
		this.vsync = vsync;
	}

	public int idleFps() {
		return idleFps;
	}

	public void setIdleFps(int idleFps) {
		this.idleFps = idleFps;
	}

	public int unfocusedFps() {
		return unfocusedFps;
	}

	public void setUnfocusedFps(int unfocusedFps) {
		this.unfocusedFps = unfocusedFps;
	}

	public int meshThreads() {
		return meshThreads;
	}

	public void setMeshThreads(int meshThreads) {
		this.meshThreads = meshThreads;
	}

	public int uploadBudgetMb() {
		return uploadBudgetMb;
	}

	public void setUploadBudgetMb(int uploadBudgetMb) {
		this.uploadBudgetMb = uploadBudgetMb;
	}

	/** Cave culling, unless {@code -Daltrender.debug.caveCulling.disable} overrides it. */
	public boolean caveCulling() {
		return caveCulling && !Boolean.getBoolean("altrender.debug.caveCulling.disable");
	}

	public boolean caveCullingSetting() {
		return caveCulling;
	}

	public void setCaveCulling(boolean caveCulling) {
		this.caveCulling = caveCulling;
	}

	/** Entity culling, unless {@code -Daltrender.debug.entityCulling.disable} overrides it. */
	public boolean entityCulling() {
		return entityCulling && !Boolean.getBoolean("altrender.debug.entityCulling.disable");
	}

	public boolean entityCullingSetting() {
		return entityCulling;
	}

	public void setEntityCulling(boolean entityCulling) {
		this.entityCulling = entityCulling;
	}

	/** The immediate-mode stream, or {@code -Daltrender.debug.stream=true}. */
	public boolean immediateStream() {
		return immediateStream || Boolean.getBoolean("altrender.debug.stream");
	}

	public boolean immediateStreamSetting() {
		return immediateStream;
	}

	public void setImmediateStream(boolean immediateStream) {
		this.immediateStream = immediateStream;
	}

	public String fpsCounter() {
		return fpsCounter;
	}

	public void setFpsCounter(String mode) {
		this.fpsCounter = mode;
	}

	public boolean fpsCounterGpu() {
		return fpsCounterGpu;
	}

	public void setFpsCounterGpu(boolean on) {
		this.fpsCounterGpu = on;
	}

	public String fpsCounterCorner() {
		return fpsCounterCorner;
	}

	public void setFpsCounterCorner(String corner) {
		this.fpsCounterCorner = corner;
	}

	public String fpsCounterContrast() {
		return fpsCounterContrast;
	}

	public void setFpsCounterContrast(String contrast) {
		this.fpsCounterContrast = contrast;
	}

	public boolean modernDebugScreen() {
		return modernDebugScreen;
	}

	public void setModernDebugScreen(boolean on) {
		this.modernDebugScreen = on;
	}

	public int renderDistance() {
		return renderDistance;
	}

	public void setRenderDistance(int chunks) {
		this.renderDistance = chunks;
	}

	/** The frame cap while focused (260 = none), or -1 if never set. */
	public int maxFps() {
		return maxFps;
	}

	public void setMaxFps(int maxFps) {
		this.maxFps = maxFps;
	}

	/** GPU-written terrain commands, or {@code -Daltrender.terrain.gpuCommands=true}. */
	public boolean gpuCommands() {
		return gpuCommands || Boolean.getBoolean("altrender.terrain.gpuCommands");
	}

	public boolean gpuCommandsSetting() {
		return gpuCommands;
	}

	public void setGpuCommands(boolean gpuCommands) {
		this.gpuCommands = gpuCommands;
	}
}
