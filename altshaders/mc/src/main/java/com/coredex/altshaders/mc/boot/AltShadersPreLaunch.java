/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.mc.boot;

import com.coredex.altshaders.AltShaders;
import com.coredex.altshaders.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class AltShadersPreLaunch implements PreLaunchEntrypoint {

	@Override
	public void onPreLaunch() {
		if (Slf4jSink.available()) {
			Log.setSink(Slf4jSink.create("AltShaders"));
		}
		AltShaders.setGameDir(FabricLoader.getInstance().getGameDir());
	}
}
