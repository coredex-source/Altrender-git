/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.world.WorldRenderer;

/** The world renderer's texture manager: the terrain atlas's pixels for the solid/cut-out split (development.md 7.3). */
@Mixin(WorldRenderer.class)
public interface WorldRendererTexturesAccess {

	@Accessor("textureManager")
	TextureManager altrender$textureManager();
}
