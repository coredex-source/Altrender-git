/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import java.util.ArrayList;
import java.util.List;

/**
 * LegacyFix (recommended, never bundled) patches LWJGL2 classes that Altrender's LWJGL2 layer replaces, and those
 * patches fail on the layer's classes (docs/decisions.md 2026-11-12). Each one fixes something the layer already does
 * its own way, so it is switched off with LegacyFix's {@code -Dlf.<id>.disable} property:
 * <ul>
 * <li>{@code bitdepth}, {@code a1.1.1}: patch {@code Display} (pixel format, display configuration): the layer requests
 * its own pixel format through SDL3;</li>
 * <li>{@code vsync}: turns vsync on before {@code Display.create}: {@link Boot} does the same through the layer;</li>
 * <li>{@code deawt}: replaces the AWT frame: the layer starts the game frameless;</li>
 * <li>{@code mouse}: patches {@code Mouse}: the layer uses SDL3's relative mouse mode;</li>
 * <li>{@code disable-controllers}: patches {@code Controllers}: the layer's is a no-op;</li>
 * <li>{@code texture-pack-folder}: patches {@code Sys.openURL}: the layer's uses SDL3 (file URLs work).</li>
 * </ul>
 * LegacyFix's other patches (proxy, java-modules, game-dir, screenshot, …) keep running. The properties must be set
 * before LegacyFix's mixin config plugin loads: {@link EarlyHook} does it.
 */
public final class LegacyFixCompat {

	static final String[] REPLACED_PATCHES = {"bitdepth", "a1.1.1", "vsync", "deawt", "mouse", "disable-controllers",
			"texture-pack-folder"};

	private static List<String> disabled = List.of();

	private LegacyFixCompat() {
	}

	static synchronized void disableReplacedPatches() {
		List<String> set = new ArrayList<>();
		for (String id : REPLACED_PATCHES) {
			String key = "lf." + id + ".disable";
			if (System.getProperty(key) == null) {
				System.setProperty(key, "true");
				set.add(id);
			}
		}
		disabled = List.copyOf(set);
	}

	/** For the startup log (the log isn't routed yet when the plugin runs). */
	public static List<String> disabled() {
		return disabled;
	}
}
