/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import com.coredex.altrender.Altrender;
import com.coredex.altrender.Log;
import com.coredex.altrender.debug.harness.Harness;
import com.coredex.altrender.natives.LwjglProbe;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.lwjgl.opengl.Display;

/** Called from Altrender's mixins at fixed points of the game's startup. */
public final class Boot {

	private Boot() {
	}

	/** The game's own init has started (from a mixin, so reaching here also proves mixins apply). */
	public static void onGameStart() {
		FabricLoader loader = FabricLoader.getInstance();
		// Ornithe passes the launcher's version name (b1.7.3); the minecraft mod holds the normalised one.
		String minecraft = System.getProperty("fabric.gameVersion", version(loader, "minecraft"));
		Altrender.onGameStart(new Altrender.Environment(minecraft, "fabric", version(loader, "legacyfix"), true));
		Log.info(LwjglProbe.describe(Boot.class.getClassLoader()));
		if (loader.isModLoaded("legacyfix")) {
			Log.info("legacyfix: switched off patches the LWJGL2 layer replaces: " + LegacyFixCompat.disabled());
			// LegacyFix's vsync patch (switched off: it can't patch the layer's Display) turns vsync on before
			// Display.create; do the same, unless the user switched that patch off. Automated runs keep it off.
			if (LegacyFixCompat.disabled().contains("vsync") && !Harness.active()) {
				Display.setVSyncEnabled(true);
				Log.info("vsync: on (as LegacyFix's vsync patch)");
			}
		}
	}

	private static String version(FabricLoader loader, String modId) {
		return loader.getModContainer(modId)
				.map(ModContainer::getMetadata)
				.map(m -> m.getVersion().getFriendlyString())
				.orElse(null);
	}
}
