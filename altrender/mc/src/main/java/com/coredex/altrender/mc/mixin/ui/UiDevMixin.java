/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import com.coredex.altrender.mc.ui.B173UiHost;
import com.coredex.altrender.mc.ui.HostScreen;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.WidgetGallery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dev: {@code -Daltrender.debug.uiGallery} shows the widget gallery in place of the first title screen (5.1). */
@Mixin(Minecraft.class)
abstract class UiDevMixin {

	@Unique
	private static final boolean altrender$GALLERY = Boolean.getBoolean("altrender.debug.uiGallery");
	@Unique
	private static boolean altrender$shown;

	@ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
	private Screen altrender$gallery(Screen screen) {
		if (altrender$GALLERY && !altrender$shown && screen instanceof TitleScreen && Renderer.ui() != null) {
			altrender$shown = true;
			return new HostScreen(new WidgetGallery(), screen, B173UiHost.get((Minecraft) (Object) this));
		}
		return screen;
	}
}
