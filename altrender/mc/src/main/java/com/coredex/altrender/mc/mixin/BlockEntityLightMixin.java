/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.WorldPipeline;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/** A block entity as an entity in {@link EntityLightMixin}: white, its block and light to the pipeline (6.8). */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityLightMixin {

	@Redirect(method = "render(Lnet/minecraft/block/entity/BlockEntity;F)V", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/opengl/GL11;glColor3f(FFF)V", remap = false))
	private void altrender$blockEntityLight(float r, float g, float b, BlockEntity be, float tickDelta) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		World w = be.world;
		if (p == null || w == null) {
			GL11.glColor3f(r, g, b);
			return;
		}
		int key = w.getBlock(be.x, be.y, be.z) << 4 | w.getBlockMetadata(be.x, be.y, be.z) & 15;
		boolean taken = p.blockEntity(key, w.getLight(LightType.BLOCK, be.x, be.y, be.z), w.getLight(LightType.SKY, be.x, be.y, be.z));
		if (taken) {
			GL11.glColor3f(1, 1, 1);
		} else {
			GL11.glColor3f(r, g, b);
		}
	}
}
