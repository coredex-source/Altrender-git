/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.widgets;

import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.Renderable;
import com.coredex.altrender.ui.framework.TextComponent;

/** Static text. */
public record Label(TextComponent text, int x, int y, int color, boolean shadow) implements Renderable {

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.drawString(text, x, y, color, shadow);
	}
}
