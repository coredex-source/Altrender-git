/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.awt.Component;

import net.minecraft.client.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Releasing the mouse centres it on the AWT component, which the frameless start doesn't have: use the window. */
@Mixin(Mouse.class)
abstract class MouseMixin {

	@Redirect(method = "unlock", at = @At(value = "INVOKE", target = "Ljava/awt/Component;getWidth()I"))
	private int altrender$width(Component component) {
		return component != null ? component.getWidth() : Display.getWidth();
	}

	@Redirect(method = "unlock", at = @At(value = "INVOKE", target = "Ljava/awt/Component;getHeight()I"))
	private int altrender$height(Component component) {
		return component != null ? component.getHeight() : Display.getHeight();
	}
}
