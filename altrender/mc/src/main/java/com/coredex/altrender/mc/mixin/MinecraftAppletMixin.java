/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.applet.Applet;

import com.coredex.altrender.mc.boot.StandaloneMinecraft;
import net.minecraft.client.MinecraftApplet;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric Loader starts applet-era versions through {@code MinecraftApplet} in its own frame: start frameless instead.
 * Doesn't return while the game runs (see StandaloneMinecraft#startFromApplet).
 * <p>
 * Taken over at {@code init}'s first own instruction (storing the canvas), not at HEAD: other mods' HEAD injections run
 * first whatever the mixin order. OSL starts its {@code init}/{@code client-init} entrypoints and launch events from
 * there (osl-entrypoints' {@code MinecraftAppletMixin}); cancelling at HEAD skipped them, and OSL's item registry then
 * failed ("registry minecraft:item is not synced") once the game's classes loaded.
 */
@Mixin(MinecraftApplet.class)
abstract class MinecraftAppletMixin extends Applet {

	@Inject(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftApplet;canvas:Ljava/awt/Canvas;",
			opcode = Opcodes.PUTFIELD, ordinal = 0), cancellable = true)
	private void altrender$startFrameless(CallbackInfo ci) {
		StandaloneMinecraft.startFromApplet(this);
		ci.cancel();
	}
}
