/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender;

/**
 * Shared, version-independent init. The mc layer gathers what only it can see (game, loader) and hands it over here.
 */
public final class Altrender {

	public static final String MOD_ID = "altrender";

	private Altrender() {
	}

	/**
	 * What the mc layer reports about the running game at startup.
	 *
	 * @param minecraft  the game version as the launcher names it (e.g. {@code b1.7.3})
	 * @param loader     the mod loader's name
	 * @param legacyFix  LegacyFix's version, or {@code null} when it isn't installed
	 * @param mixin      whether Altrender's mixins applied
	 */
	public record Environment(String minecraft, String loader, String legacyFix, boolean mixin) {
	}

	/** Logs the startup banner (development.md 0.1). */
	public static void onGameStart(Environment env) {
		Log.info("loaded mc=" + env.minecraft()
				+ " java=" + Runtime.version()
				+ " loader=" + env.loader()
				+ " legacyfix=" + (env.legacyFix() != null ? env.legacyFix() : "absent")
				+ " mixin=" + (env.mixin() ? "OK" : "MISSING"));
	}
}
