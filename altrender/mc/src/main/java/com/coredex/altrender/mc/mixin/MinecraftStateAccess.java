/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game's tick timer, for the frame's partial tick (development.md 6.5, the API's game state). */
@Mixin(Minecraft.class)
public interface MinecraftStateAccess {
	@Accessor("timer")
	TickTimer altrender$timer();
}
