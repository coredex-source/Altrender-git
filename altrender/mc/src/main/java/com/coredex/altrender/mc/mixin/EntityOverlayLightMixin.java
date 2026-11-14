/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.WorldPipeline;
import com.coredex.altrender.mc.state.B173EntityNames;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

/**
 * An entity's blob shadow and fire overlay carry their own brightness (the shadow's alpha has the light under it,
 * the fire is full-bright): under a pipeline that took the entity's light ({@link EntityLightMixin}) they get full
 * light (development.md 6.8). A pipeline with its own shadows hides the blob shadow ({@code ENTITY_SHADOWS}).
 */
@Mixin(EntityRenderer.class)
abstract class EntityOverlayLightMixin {

	@Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
	private void altrender$shadow(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta,
			CallbackInfo ci) {
		if (com.coredex.altrender.render.Renderer.hides(com.coredex.altrender.api.render.VanillaPart.ENTITY_SHADOWS)) {
			ci.cancel();
			return;
		}
		fullLight(entity);
	}

	@Inject(method = "renderOnFire", at = @At("HEAD"))
	private void altrender$fire(Entity entity, double dx, double dy, double dz, float tickDelta, CallbackInfo ci) {
		fullLight(entity);
	}

	private static void fullLight(Entity entity) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null) {
			p.entity(B173EntityNames.name(entity), 15, 15);
		}
	}
}
