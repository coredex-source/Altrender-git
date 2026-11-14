/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.mc.mixin;

import com.coredex.altshaders.AltShaders;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {

	/** Start of the game's own init (reaching here proves AltShaders' mixins apply). */
	@Inject(method = "init", at = @At("HEAD"))
	private void altshaders$onInit(CallbackInfo ci) {
		AltShaders.onGameStart();
	}

	/** The reload key, once a tick. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void altshaders$reloadKey(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		AltShaders.reloadKey(org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_R),
				mc.world != null && mc.screen == null);
	}
}
