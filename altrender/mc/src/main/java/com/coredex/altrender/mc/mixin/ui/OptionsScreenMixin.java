/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import com.coredex.altrender.mc.ui.B173Settings;
import com.coredex.altrender.render.Renderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Options screen's "Video Settings..." button opens Altrender's video settings (development.md 5.2), while
 * Altrender's renderer runs; with it off, the game's own screen.
 */
@Mixin(OptionsScreen.class)
abstract class OptionsScreenMixin extends Screen {

	@Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
	private void altrender$videoSettings(ButtonWidget button, CallbackInfo ci) {
		if (button.active && button.id == 101 && Renderer.ui() != null) {
			this.minecraft.options.save();
			B173Settings.open(this.minecraft);
			ci.cancel();
		}
	}
}
