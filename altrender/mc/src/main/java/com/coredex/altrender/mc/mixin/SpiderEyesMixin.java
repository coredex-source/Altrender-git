/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.WorldPipeline;
import net.minecraft.client.render.entity.SpiderRenderer;
import net.minecraft.entity.mob.monster.SpiderEntity;

/**
 * The spider's eyes are a second, full-bright pass (development.md 6.8): under a pipeline that took the spider's light
 * ({@link EntityLightMixin}), the eyes get full light, as the game draws them, and the pipeline's eyes program.
 */
@Mixin(SpiderRenderer.class)
abstract class SpiderEyesMixin {

	@Inject(method = "bindTexture(Lnet/minecraft/entity/mob/monster/SpiderEntity;IF)Z", at = @At("RETURN"))
	private void altrender$eyes(SpiderEntity mob, int layer, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null && cir.getReturnValueZ()) {
			p.entity("minecraft:spider", 15, 15);
			p.entityEyes(true); // ends after the layer's draw (MobRendererMixin)
		}
	}
}
