/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.applet.Applet;

import com.coredex.altrender.mc.boot.StandaloneMinecraft;
import net.minecraft.client.MinecraftApplet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric Loader starts applet-era versions through {@code MinecraftApplet} in its own frame: start frameless instead.
 * Doesn't return while the game runs (see StandaloneMinecraft#startFromApplet).
 */
@Mixin(MinecraftApplet.class)
abstract class MinecraftAppletMixin extends Applet {

	@Inject(method = "init", at = @At("HEAD"), cancellable = true)
	private void altrender$startFrameless(CallbackInfo ci) {
		StandaloneMinecraft.startFromApplet(this);
		ci.cancel();
	}
}
