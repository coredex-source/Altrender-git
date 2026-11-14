/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import com.coredex.altrender.mc.terrain.BlockBounds;
import com.coredex.altrender.mc.terrain.MeshWorkerState;
import com.coredex.altrender.terrain.MeshThread;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The block renderer on meshing workers (development.md 2.2): the worker's own tessellator instead of the game's single
 * one, and per-thread block shapes (BlockBounds). Off the workers both redirects return what the game had.
 */
@Mixin(BlockRenderer.class)
abstract class BlockRendererMeshMixin {

	@Redirect(method = "*", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/render/vertex/Tesselator;INSTANCE:Lnet/minecraft/client/render/vertex/Tesselator;",
			opcode = Opcodes.GETSTATIC))
	private Tesselator altrender$tesselator() {
		return Thread.currentThread() instanceof MeshThread m ? ((MeshWorkerState) m.state).tesselator : Tesselator.INSTANCE;
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;minY:D", opcode = Opcodes.PUTFIELD))
	private void altrender$setMinY(Block block, double value) {
		BlockBounds.setMinY(block, value);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;maxY:D", opcode = Opcodes.PUTFIELD))
	private void altrender$setMaxY(Block block, double value) {
		BlockBounds.setMaxY(block, value);
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

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;minX:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMinX(DoorBlock block) {
		return BlockBounds.minX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;minY:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMinY(DoorBlock block) {
		return BlockBounds.minY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;minZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMinZ(DoorBlock block) {
		return BlockBounds.minZ(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;maxX:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMaxX(DoorBlock block) {
		return BlockBounds.maxX(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;maxY:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMaxY(DoorBlock block) {
		return BlockBounds.maxY(block);
	}

	@Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/block/DoorBlock;maxZ:D", opcode = Opcodes.GETFIELD))
	private double altrender$doorMaxZ(DoorBlock block) {
		return BlockBounds.maxZ(block);
	}
}
