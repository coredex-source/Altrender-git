/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import java.util.List;

import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Harness only: the compile queue (to know when terrain has settled) and the cloud/tick counter it pins. */
@Mixin(WorldRenderer.class)
public interface WorldRendererAccess {

	@Accessor("dirtyChunks")
	List<RenderChunk> altrender$dirtyChunks();

	@Accessor("ticks")
	void altrender$setTicks(int ticks);
}
