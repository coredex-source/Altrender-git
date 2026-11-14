/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import com.coredex.altrender.terrain.MeshThread;
import net.minecraft.block.PistonHeadBlock;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The moving-piston renderer (main thread) sets a sprite on the shared piston head block while it draws; a worker
 * meshing a still piston head at that moment must not see it (development.md 2.2).
 */
@Mixin(PistonHeadBlock.class)
abstract class PistonHeadBlockMixin {

	@Shadow
	private int forcedSprite;

	@Redirect(method = "getSprite", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonHeadBlock;forcedSprite:I",
			opcode = Opcodes.GETFIELD))
	private int altrender$forcedSprite(PistonHeadBlock block) {
		return Thread.currentThread() instanceof MeshThread ? -1 : this.forcedSprite;
	}
}
