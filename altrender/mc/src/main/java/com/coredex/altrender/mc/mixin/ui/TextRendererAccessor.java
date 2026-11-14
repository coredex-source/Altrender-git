/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The font's glyph widths, for the UI renderer's own text (development.md 5.1). */
@Mixin(TextRenderer.class)
public interface TextRendererAccessor {
	@Accessor("characterWidths")
	int[] altrender$characterWidths();
}
