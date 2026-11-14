/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import com.coredex.altrender.Log;
import com.coredex.altrender.mc.harness.SceneDriver;
import com.coredex.altrender.natives.NativesBoot;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs before the game's main class loads: where the natives and the LWJGL replacement go (0.4, 0.5). */
public final class AltrenderPreLaunch implements PreLaunchEntrypoint {

	@Override
	public void onPreLaunch() {
		if (Slf4jSink.available()) {
			Log.setSink(Slf4jSink.create("Altrender"));
		}
		NativesBoot.prepare(FabricLoader.getInstance().getGameDir());
		// dev-only scene harness (development.md 0.7): inert unless -Daltrender.scene is set
		SceneDriver.preLaunch(FabricLoader.getInstance().getGameDir());
	}
}
