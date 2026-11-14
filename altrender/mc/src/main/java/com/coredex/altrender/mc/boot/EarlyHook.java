/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * Altrender's earliest hook. Fabric Loader creates every mod's language adapters while it sets up mods, before it
 * bootstraps Mixin, so this runs before any mixin config plugin, including LegacyFix's (which decides its patches
 * there). Used only for that timing: no entrypoint uses this adapter.
 */
public final class EarlyHook implements LanguageAdapter {

	static {
		LegacyFixCompat.disableReplacedPatches();
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new LanguageAdapterException("altrender_early is not an entrypoint adapter");
	}
}
