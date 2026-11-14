/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.coredex.altrender.mc.adapter.SnapshotWorldView;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.world.WorldView;

/**
 * The game's per-face shading (bottom 0.5, north/south 0.8, east/west 0.6, in the block, fluid, cactus, door and bed
 * paths) left out when meshing for a shader pack that doesn't keep it (its {@code oldLighting}, Iris's rule): the
 * pack shades the faces itself.
 */
@Mixin(BlockRenderer.class)
abstract class BlockRendererShadeMixin {

	@Shadow
	private WorldView world;

	@ModifyConstant(method = {"tesselateWithMaxAmbientOcclusion", "tesselateWithoutAmbientOcclusion", "tesselateLiquid",
			"tesselateCactus(Lnet/minecraft/block/Block;IIIFFF)Z", "tesselateDoor", "tesselateBed"},
			constant = {@Constant(floatValue = 0.5f), @Constant(floatValue = 0.8f), @Constant(floatValue = 0.6f)})
	private float altrender$faceShade(float shade) {
		return world instanceof SnapshotWorldView v && v.snapshot().extended && !v.snapshot().faceShade ? 1f : shade;
	}
}
