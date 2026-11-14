/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.WorldPipeline;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.mob.MobEntity;

/**
 * A mob's flashes and decoration layers under a world pipeline (development.md 6.8):
 * <ul>
 * <li>the hurt red (b1.7.3: 0.4 while hurt or dying) and the renderer's own flash (a creeper's white) go to the
 * pipeline as one colour ({@code entityColor}, Iris); when it takes them, the game's overlay pass isn't drawn;</li>
 * <li>a decoration layer's end ends the eyes' program ({@link SpiderEyesMixin} starts it).</li>
 * </ul>
 */
@Mixin(MobRenderer.class)
abstract class MobRendererMixin {

	@Unique
	private static boolean altrender$colorTaken;

	@Shadow
	protected abstract int getOverlayColor(MobEntity entity, float brightness, float tickDelta);

	@Inject(method = "render(Lnet/minecraft/entity/mob/MobEntity;DDDFF)V", at = @At("HEAD"))
	private void altrender$entityColor(MobEntity entity, double x, double y, double z, float yaw, float tickDelta,
			CallbackInfo ci) {
		altrender$colorTaken = false;
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p == null) {
			return;
		}
		float hurt = entity.damagedTimer > 0 || entity.deathTicks > 0 ? 0.4f : 0f;
		if (hurt > 0 && (Object) this instanceof net.minecraft.client.render.entity.SpiderRenderer) {
			// The red pass also draws the layers bindTexture accepts: a spider's eyes layer has its body's shape, so
			// b1.7.3 reddens a spider twice.
			hurt = 1 - (1 - hurt) * (1 - hurt);
		}
		int overlay = getOverlayColor(entity, 1f, tickDelta); // brightness 1: the pipeline lights it
		float flash = (overlay >> 24 & 255) / 255f;
		// The game draws the red, then the flash over it: as one colour over the texture.
		float a = 1 - (1 - hurt) * (1 - flash);
		if (a <= 0) {
			return;
		}
		float r = (hurt * (1 - flash) + (overlay >> 16 & 255) / 255f * flash) / a;
		float g = (overlay >> 8 & 255) / 255f * flash / a;
		float b = (overlay & 255) / 255f * flash / a;
		altrender$colorTaken = p.entityColor(r, g, b, a);
	}

	/** The overlay pass's draws (after the game works out its flash): skipped when the pipeline took the colour. */
	@Redirect(method = "render(Lnet/minecraft/entity/mob/MobEntity;DDDFF)V",
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/MobRenderer;getOverlayColor(Lnet/minecraft/entity/mob/MobEntity;FF)I")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;render(FFFFFF)V"))
	private void altrender$overlayDraw(Model model, float a, float b, float c, float d, float e, float f) {
		if (!altrender$colorTaken) {
			model.render(a, b, c, d, e, f);
		}
	}

	@Inject(method = "render(Lnet/minecraft/entity/mob/MobEntity;DDDFF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/model/Model;render(FFFFFF)V", ordinal = 1, shift = At.Shift.AFTER))
	private void altrender$layerDone(MobEntity entity, double x, double y, double z, float yaw, float tickDelta,
			CallbackInfo ci) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null) {
			p.entityEyes(false);
		}
	}
}
