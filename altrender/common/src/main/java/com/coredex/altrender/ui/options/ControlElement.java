/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/taumc/celeritas/api/options/control/ControlElement.java at commit 38ccea6c, licensed under
 * LGPL-3.0; Celeritas's options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the
 * notice is in META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: on Altrender's option API and widget base (keyboard focus: a white outline).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.options;

import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;
import com.coredex.altrender.ui.widgets.Button;
import com.coredex.altrender.ui.widgets.Widget;

/**
 * One option's row: its name on the left (italic with " *" while changed, struck through grey while unavailable) on
 * a flat background; the control draws on the right.
 */
public class ControlElement<T> extends Widget {

	protected final Option<T> option;
	private final Button.Style style = Button.Style.defaults();
	private final int controlWidth;

	public ControlElement(Option<T> option, Dim2i dim, int controlWidth) {
		super(dim);
		this.option = option;
		this.controlWidth = controlWidth;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		String name = ctx.extractString(TextComponent.translatable(option.name()));
		TextComponent label;
		boolean hovered = isMouseOver(mouseX, mouseY);
		if (hovered && ctx.getStringWidth(name) > dim.width() - controlWidth) {
			name = name.substring(0, Math.min(name.length(), 10)) + "...";
		}
		if (option.isAvailable()) {
			if (option.hasChanged()) {
				label = TextComponent.literal(name + " *").withStyle(TextFormattingStyle.ITALIC);
			} else {
				label = TextComponent.literal(name).withStyle(TextFormattingStyle.WHITE);
			}
		} else {
			label = TextComponent.literal(name).withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
		}
		ctx.fill(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), hovered || focused ? style.bgHovered : style.bgDefault);
		ctx.drawString(label, dim.x() + 6, dim.getCenterY() - 4, style.textDefault);
		if (focused) {
			ctx.drawBorder(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), Theme.TEXT);
		}
	}

	public Option<T> option() {
		return option;
	}

	@Override
	public boolean isFocusable() {
		return option.isAvailable();
	}
}
