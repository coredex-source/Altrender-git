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
import com.coredex.altrender.mc.state.B173EntityNames;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;

/**
 * With a world pipeline installed, an entity draws white and the pipeline gets its type and light instead
 * (development.md 6.8): packs light entities from light coordinates, the game's brightness in the colour would light
 * them twice. The light is read where the game reads its brightness.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityLightMixin {

	@Redirect(method = "render(Lnet/minecraft/entity/Entity;F)V", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/opengl/GL11;glColor3f(FFF)V", remap = false))
	private void altrender$entityLight(float r, float g, float b, Entity entity, float tickDelta) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p == null || entity.world == null) {
			GL11.glColor3f(r, g, b);
			return;
		}
		int x = MathHelper.floor(entity.x);
		int y = MathHelper.floor(entity.y - entity.eyeHeight + (entity.shape.maxY - entity.shape.minY) * 0.66);
		int z = MathHelper.floor(entity.z);
		boolean taken = p.entity(B173EntityNames.name(entity), entity.world.getLight(LightType.BLOCK, x, y, z),
				entity.world.getLight(LightType.SKY, x, y, z));
		if (taken) {
			GL11.glColor3f(1, 1, 1);
		} else {
			GL11.glColor3f(r, g, b);
		}
	}
}
