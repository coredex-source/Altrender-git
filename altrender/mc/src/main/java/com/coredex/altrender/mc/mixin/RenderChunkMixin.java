/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.debug.FrameStats;
import net.minecraft.client.render.world.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the game's section compiles for FrameStats (only dirty sections reach the counter it bumps). */
@Mixin(RenderChunk.class)
abstract class RenderChunkMixin {

	@Inject(method = "compile", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/render/world/RenderChunk;updateCounter:I", opcode = org.objectweb.asm.Opcodes.PUTSTATIC))
	private void altrender$countCompile(CallbackInfo ci) {
		FrameStats.sectionsBuilt++;
	}
}
