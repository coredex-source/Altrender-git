/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import com.coredex.altrender.mc.terrain.BlockBounds;
import net.minecraft.block.PistonBaseBlock;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@code PistonBaseBlock#getSprite} reads its own shape: per-thread on workers (BlockBounds, development.md 2.2). */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockShapeMixin {

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;minX:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinX(PistonBaseBlock block) {
		return BlockBounds.minX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;minY:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinY(PistonBaseBlock block) {
		return BlockBounds.minY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;minZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinZ(PistonBaseBlock block) {
		return BlockBounds.minZ(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;maxX:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxX(PistonBaseBlock block) {
		return BlockBounds.maxX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;maxY:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxY(PistonBaseBlock block) {
		return BlockBounds.maxY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/PistonBaseBlock;maxZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxZ(PistonBaseBlock block) {
		return BlockBounds.maxZ(block);
	}
}
