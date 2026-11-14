/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's rain and snow draw, for a pipeline that hides it (development.md 6.8). */
@Mixin(GameRenderer.class)
public interface GameRendererWeatherAccess {
	@Invoker("renderSnowAndRain")
	void altrender$renderSnowAndRain(float tickDelta);
}
