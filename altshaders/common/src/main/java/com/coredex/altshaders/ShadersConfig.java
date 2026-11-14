/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AltShaders' settings, {@code config/altshaders.properties} (as Iris's {@code iris.properties}): the selected pack
 * and whether packs are on. {@code -Daltshaders.pack=<name>} overrides both for a run (development and the harness).
 */
public final class ShadersConfig {

	private static Path file;
	private static String pack = "";
	private static boolean enabled = true;
	/** Shadow back-face culling (owner, 2026-11-14: optional, off as Iris). */
	private static boolean shadowBackFaces;

	private ShadersConfig() {
	}

	static void load(Path gameDir) {
		file = gameDir.resolve("config").resolve("altshaders.properties");
		if (!Files.exists(file)) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			p.load(in);
			pack = p.getProperty("shaderPack", "").strip();
			enabled = !"false".equals(p.getProperty("enableShaders", "true").strip());
			shadowBackFaces = "true".equals(p.getProperty("shadowBackFaceCulling", "false").strip());
		} catch (IOException | IllegalArgumentException e) {
			Log.warn("config: can't read " + file + " (" + e.getMessage() + "); no shader pack");
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		Properties p = new Properties();
		p.setProperty("shaderPack", pack);
		p.setProperty("enableShaders", Boolean.toString(enabled));
		p.setProperty("shadowBackFaceCulling", Boolean.toString(shadowBackFaces));
		try {
			Files.createDirectories(file.getParent());
			try (OutputStream out = Files.newOutputStream(file)) {
				p.store(out, "AltShaders");
			}
		} catch (IOException e) {
			Log.warn("config: can't write " + file + ": " + e.getMessage());
		}
	}

	/** The pack to use (a name in {@code shaderpacks/}), or {@code null} for none. */
	public static String selected() {
		String override = System.getProperty("altshaders.pack");
		if (override != null) {
			return override.isBlank() ? null : override;
		}
		return enabled && !pack.isEmpty() ? pack : null;
	}

	public static String pack() {
		return pack;
	}

	public static void setPack(String name) {
		pack = name == null ? "" : name;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
	}

	/**
	 * Whether the shadow pass leaves out terrain faces turned away from the sun or moon (Iris draws both sides; off by
	 * default). {@code -Daltshaders.shadowBackFaceCulling} sets it for a run.
	 */
	public static boolean shadowBackFaces() {
		String override = System.getProperty("altshaders.shadowBackFaceCulling");
		return override != null ? Boolean.parseBoolean(override) : shadowBackFaces;
	}

	public static void setShadowBackFaces(boolean on) {
		shadowBackFaces = on;
	}
}
