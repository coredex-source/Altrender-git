/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import com.coredex.altrender.debug.harness.Harness;
import com.coredex.altrender.mc.harness.SceneDriver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scene harness hook (development.md 0.7); does nothing unless -Daltrender.scene is set. */
@Mixin(GameRenderer.class)
abstract class FovMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void altrender$sceneFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (Harness.active() && SceneDriver.fov() > 0) {
			cir.setReturnValue(SceneDriver.fov());
		}
	}
}
