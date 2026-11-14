/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import com.coredex.altrender.mc.terrain.BlockBounds;
import net.minecraft.block.Block;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-thread block shapes in {@code Block} itself (BlockBounds, development.md 2.2). */
@Mixin(Block.class)
abstract class BlockShapeMixin {

	@Inject(method = "setShape", at = @At("HEAD"), cancellable = true)
	private void altrender$workerShape(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			CallbackInfo ci) {
		if (BlockBounds.setShape((Block) (Object) this, minX, minY, minZ, maxX, maxY, maxZ)) {
			ci.cancel();
		}
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;minX:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinX(Block block) {
		return BlockBounds.minX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;minY:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinY(Block block) {
		return BlockBounds.minY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;minZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$MinZ(Block block) {
		return BlockBounds.minZ(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;maxX:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxX(Block block) {
		return BlockBounds.maxX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;maxY:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxY(Block block) {
		return BlockBounds.maxY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;maxZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$MaxZ(Block block) {
		return BlockBounds.maxZ(block);
	}
}
