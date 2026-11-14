/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.render.FramePacer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The game's "Power saver" frame limit (40 FPS) sleeps whole milliseconds from a truncated estimate; here it waits
 * precisely until the frame's target time (development.md 3.6). Same limit, steadier frames.
 */
@Mixin(GameRenderer.class)
abstract class FrameLimitMixin {

	@Shadow
	private long lastWorldRenderTime;

	private static final boolean OFF = Boolean.getBoolean("altrender.debug.preciseLimit.disable");

	@Redirect(method = "render(F)V", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"))
	private void altrender$preciseLimit(long millis) throws InterruptedException {
		if (OFF) {
			Thread.sleep(millis);
		} else {
			FramePacer.waitUntil(this.lastWorldRenderTime + 1_000_000_000L / 40);
		}
	}
}
