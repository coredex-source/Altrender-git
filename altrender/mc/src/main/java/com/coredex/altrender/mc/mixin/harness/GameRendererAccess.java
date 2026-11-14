/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Harness only: the per-tick state the scene pins (fog brightness easing, rain animation counter). */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {

	@Accessor("ticks")
	void altrender$setTicks(int ticks);

	@Accessor("fogBrightness")
	void altrender$setFogBrightness(float value);

	@Accessor("lastFogBrightness")
	void altrender$setLastFogBrightness(float value);
}
