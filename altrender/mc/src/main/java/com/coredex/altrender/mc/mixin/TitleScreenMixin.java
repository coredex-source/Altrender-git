/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.render.Renderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The in-game notice when Altrender switched itself off (development.md 1.2), under the version line. */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {

	@Inject(method = "render", at = @At("TAIL"))
	private void altrender$notice(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		String notice = Renderer.notice();
		if (notice != null) {
			this.drawString(this.textRenderer, notice, 2, 12, 0xFF5555);
		}
	}
}
