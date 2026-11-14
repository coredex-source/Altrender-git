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
@Mixin(Minecraft.class)
abstract class GameHarnessMixin {

	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(F)V"))
	private void altrender$beforeRender(CallbackInfo ci) {
		if (Harness.active()) {
			SceneDriver.beforeRender((Minecraft) (Object) this);
		}
	}

	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(F)V", shift = At.Shift.AFTER))
	private void altrender$afterRender(CallbackInfo ci) {
		if (Harness.active()) {
			SceneDriver.afterRender((Minecraft) (Object) this);
		}
	}

	/** The real cursor must not turn the camera. */
	@Inject(method = "lockMouse", at = @At("HEAD"), cancellable = true)
	private void altrender$noGrab(CallbackInfo ci) {
		if (Harness.active()) {
			ci.cancel();
		}
	}

	/** Losing focus would open the pause menu in the shots. */
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void altrender$noPause(CallbackInfo ci) {
		if (Harness.active()) {
			ci.cancel();
		}
	}
}
