/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import com.coredex.altrender.debugscreen.DebugScreen;
import com.coredex.altrender.debugscreen.FpsCounter;
import com.coredex.altrender.mc.ui.B173DebugEntries;
import com.coredex.altrender.mc.ui.B173UiHost;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Altrender's debug screen in place of the game's F3 text (development.md 5.3) while the Modern F3 Screen setting is
 * on (the default) and Altrender renders; drawn over the rest of the HUD.
 */
@Mixin(GameGui.class)
abstract class GameGuiDebugMixin {

	@Shadow
	private Minecraft minecraft;

	@Redirect(method = "render", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/options/GameOptions;debugEnabled:Z"))
	private boolean altrender$gameDebug(GameOptions options) {
		return options.debugEnabled && !modern();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void altrender$debugScreen(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
		boolean f3 = this.minecraft.options.debugEnabled;
		UiRenderer ui = Renderer.ui();
		if (ui == null) {
			return;
		}
		boolean modern = modern();
		Renderer.wantGpuTimers(modern && DebugScreen.wantsGpuTimers(f3) || FpsCounter.wantsGpuTimers());
		Window window = new Window(this.minecraft.options, this.minecraft.width, this.minecraft.height);
		// The FPS counter (5.4), unless the debug screen is open over it.
		if (FpsCounter.enabled() && !f3) {
			FpsCounter.render(ui, B173UiHost.get(this.minecraft), window.getWidth(), window.getHeight());
		}
		if (!modern) {
			return;
		}
		B173DebugEntries.register(this.minecraft);
		if (DebugScreen.active(f3)) {
			DebugScreen.render(ui, B173UiHost.get(this.minecraft), f3, window.getWidth(), window.getHeight());
		}
	}

	private static boolean modern() {
		return Renderer.instance() != null && Renderer.config().modernDebugScreen();
	}
}
