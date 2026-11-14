/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether the game is fullscreen (the video settings' Fullscreen option, development.md 5.2). */
@Mixin(Minecraft.class)
public interface MinecraftUiAccess {
	@Accessor("fullscreen")
	boolean altrender$fullscreen();
}
