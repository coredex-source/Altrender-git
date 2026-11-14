/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/widgets/FlatButtonWidget.java at commit 38ccea6c, licensed
 * under LGPL-3.0; Celeritas's options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT;
 * the notice is in META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender's widget base (keyboard focus: Enter/Space, a white outline while
 * focused).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.widgets;

import java.util.Objects;

import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.InteractionContext;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.framework.TextComponent;

/** A flat button; a selected one (a side bar's current page) is marked in the activated colour. */
public class Button extends Widget {

	private final Runnable action;
	private Style style = Style.defaults();
	private boolean selected;
	private boolean visible = true;
	private boolean leftAligned;
	private TextComponent label;

	public Button(Dim2i dim, TextComponent label, Runnable action) {
		super(dim);
		this.label = label;
		this.action = action;
	}

	protected int getLeftAlignedTextOffset(DrawContext ctx) {
		return 10;
	}

	protected boolean isHovered(int mouseX, int mouseY) {
		return dim.containsCursor(mouseX, mouseY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (!visible) {
			return;
		}
		boolean hovered = isHovered(mouseX, mouseY);
		int background = enabled ? (hovered ? style.bgHovered : style.bgDefault) : style.bgDisabled;
		int textColor = enabled ? style.textDefault : style.textDisabled;
		int strWidth = ctx.getStringWidth(label);
		ctx.fill(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), background);
		int textX = leftAligned ? dim.x() + getLeftAlignedTextOffset(ctx) : dim.getCenterX() - strWidth / 2;
		ctx.drawString(label, textX, dim.getCenterY() - 4, textColor);
		if (enabled && selected) {
			ctx.fill(dim.x(), leftAligned ? dim.y() : dim.getLimitY() - 1, leftAligned ? dim.x() + 1 : dim.getLimitX(),
					dim.getLimitY(), Theme.ACTIVATED);
		}
		if (focused) {
			ctx.drawBorder(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), Theme.TEXT);
		}
	}

	public void setStyle(Style style) {
		this.style = Objects.requireNonNull(style);
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public void setLeftAligned(boolean leftAligned) {
		this.leftAligned = leftAligned;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean visible() {
		return visible;
	}

	public void setLabel(TextComponent label) {
		this.label = label;
	}

	public TextComponent label() {
		return label;
	}

	@Override
	public boolean isFocusable() {
		return enabled && visible;
	}

	@Override
	public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
		if (!enabled || !visible) {
			return false;
		}
		if (button == 0 && dim.containsCursor(mouseX, mouseY)) {
			doAction(context);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(InteractionContext context, Key key, int mods) {
		if (enabled && visible && (key == Key.ENTER || key == Key.SPACE)) {
			doAction(context);
			return true;
		}
		return false;
	}

	private void doAction(InteractionContext context) {
		action.run();
		context.playClickSound();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return dim.containsCursor(mouseX, mouseY);
	}

	/** Colours of a flat button. */
	public static final class Style {
		public int bgHovered;
		public int bgDefault;
		public int bgDisabled;
		public int textDefault;
		public int textDisabled;

		public static Style defaults() {
			Style s = new Style();
			s.bgHovered = Theme.BG_HOVERED;
			s.bgDefault = Theme.BG;
			s.bgDisabled = Theme.BG_DISABLED;
			s.textDefault = Theme.TEXT;
			s.textDisabled = Theme.TEXT_DISABLED;
			return s;
		}
	}
}
