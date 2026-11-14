/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.natives;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reports which LWJGL the game's class loader resolves (development.md 0.4): LWJGL 3 has {@code org.lwjgl.Version},
 * LWJGL2 has {@code org.lwjgl.Sys.getVersion()}. Reflection only, so nothing here links against either.
 */
public final class LwjglProbe {

	private LwjglProbe() {
	}

	/** One log-ready line: version, the jar {@code GL11} comes from, and every jar on the loader that has one. */
	public static String describe(ClassLoader loader) {
		return "lwjgl=" + version(loader)
				+ " gl11=" + source(loader, "org.lwjgl.opengl.GL11")
				+ " candidates=" + candidates(loader, "org/lwjgl/opengl/GL11.class");
	}

	static String version(ClassLoader loader) {
		try {
			// LWJGL 3's Version loads no natives, so initialising it here is harmless.
			return (String) Class.forName("org.lwjgl.Version", true, loader).getMethod("getVersion").invoke(null);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// not LWJGL 3
		}
		try {
			// LWJGL2: Sys's static init loads the natives, so don't initialise it (the game's own failure must stay
			// the game's); its jar name carries the version.
			Class.forName("org.lwjgl.Sys", false, loader);
			return "2(" + source(loader, "org.lwjgl.Sys") + ")";
		} catch (ClassNotFoundException | LinkageError e) {
			return "none";
		}
	}

	static String source(ClassLoader loader, String className) {
		try {
			Class<?> c = Class.forName(className, false, loader);
			var cs = c.getProtectionDomain().getCodeSource();
			return cs != null && cs.getLocation() != null ? shortName(cs.getLocation().toString()) : "?";
		} catch (ClassNotFoundException | LinkageError e) {
			return "none";
		}
	}

	static List<String> candidates(ClassLoader loader, String resource) {
		List<String> out = new ArrayList<>();
		try {
			for (URL url : Collections.list(loader.getResources(resource))) {
				out.add(shortName(url.toString()));
			}
		} catch (IOException e) {
			out.add("error(" + e.getMessage() + ")");
		}
		return out;
	}

	/** The jar or directory name only: logs shouldn't carry full user paths. */
	private static String shortName(String location) {
		String s = location;
		int bang = s.indexOf("!/");
		if (bang >= 0) {
			s = s.substring(0, bang);
		}
		if (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s.substring(s.lastIndexOf('/') + 1);
	}
}
