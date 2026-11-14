/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.options.OptionPageProvider;
import com.coredex.altrender.ui.options.AltrenderPages;
import com.coredex.altrender.ui.options.OptionsScreen;
import net.minecraft.client.Minecraft;

/** Opens Altrender's video settings over the current screen: Altrender's pages, then other mods' (API). */
public final class B173Settings {

	private B173Settings() {
	}

	/** The shader pack screen (a shader pack addon's menu), over the current screen or the world. */
	public static void openShaderPacks(Minecraft mc) {
		com.coredex.altrender.api.shaderpacks.ShaderPackMenu menu = AltrenderApi.shaderPackMenu();
		if (menu != null) {
			B173UiHost.get(mc).open(new com.coredex.altrender.ui.shaderpacks.ShaderPackScreen(menu));
		}
	}

	public static void open(Minecraft mc) {
		// Until Altrender's frame cap is first set the game's own limiter applies; from here on, ours.
		B173VideoOptions.migrateFrameLimit(mc.options);
		B173VideoOptions game = new B173VideoOptions(mc);
		List<OptionPageProvider> providers = new ArrayList<>();
		providers.add(new AltrenderPages(game));
		providers.addAll(AltrenderApi.optionPageProviders());
		B173UiHost.get(mc).open(new OptionsScreen(providers, game::applyEffects));
	}
}
