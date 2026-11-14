/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import java.util.Random;

import com.coredex.altrender.debug.harness.Harness;
import net.minecraft.world.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scene harness hook (development.md 0.7): the portal a dimension change builds takes its orientation from an
 * unseeded random; seed it so the Nether scene is the same every run. */
@Mixin(PortalForcer.class)
abstract class PortalForcerMixin {

	@Shadow
	private Random random;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void altrender$seed(CallbackInfo ci) {
		if (Harness.active()) {
			this.random.setSeed(0x417252L);
		}
	}
}
