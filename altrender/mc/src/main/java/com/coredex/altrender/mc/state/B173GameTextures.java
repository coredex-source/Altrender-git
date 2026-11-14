/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import java.util.Map;
import java.util.function.ToIntFunction;

import net.minecraft.client.Minecraft;

/**
 * The game's textures a shader pack can name by modern resource path (development.md 6.4: {@code texture.* =
 * minecraft:textures/environment/clouds.png}), mapped to b1.7.3's own paths; others have no b1.7.3 counterpart.
 */
public final class B173GameTextures implements ToIntFunction<String> {

	private static final Map<String, String> PATHS = Map.of(
			"textures/environment/clouds.png", "/environment/clouds.png",
			"textures/environment/rain.png", "/environment/rain.png",
			"textures/environment/snow.png", "/environment/snow.png",
			"textures/environment/sun.png", "/terrain/sun.png",
			"textures/environment/moon_phases.png", "/terrain/moon.png",
			"textures/atlas/blocks.png", "/terrain.png",
			"textures/atlas/items.png", "/gui/items.png",
			"textures/particle/particles.png", "/particles.png");

	private final Minecraft minecraft;

	public B173GameTextures(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public int applyAsInt(String path) {
		String p = path.startsWith("minecraft:") ? path.substring("minecraft:".length()) : path;
		String own = PATHS.get(p);
		return own != null && minecraft.textureManager != null ? minecraft.textureManager.load(own) : 0;
	}
}
