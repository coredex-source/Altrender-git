/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.GameRenderer;

/** The hand's camera moves (hurt shake, view bobbing), for drawing the hand early (development.md 6.8). */
@Mixin(GameRenderer.class)
public interface GameRendererHandInvoker {

	@Invoker("applyHurtCam")
	void altrender$applyHurtCam(float tickDelta);

	@Invoker("applyViewBobbing")
	void altrender$applyViewBobbing(float tickDelta);
}
