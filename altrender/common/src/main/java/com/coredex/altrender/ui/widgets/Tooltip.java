/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/frame/OptionPageFrame.java (renderOptionTooltip) at commit
 * 38ccea6c, licensed under LGPL-3.0; Celeritas's options GUI derives from Reese's Sodium Options (Copyright (c) 2021
 * FlashyReese, MIT; the notice is in META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: a stand-alone tooltip any widget can request; drawn by the screen above
 * everything else.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.widgets;

import java.util.List;

import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.TextComponent;

/** Sodium's option tooltip: under the element and as wide as it (above it if it wouldn't fit), lines 12 apart. */
public final class Tooltip {

	private static final int TEXT_PADDING = 3;
	private static final int BOX_PADDING = 3;

	/**
	 * A tooltip for an element.
	 *
	 * @param lines  already wrapped to {@link #textWidth} of the element
	 * @param anchor the element, in screen coordinates
	 * @param limitY the bottom of the area it should stay above
	 */
	public record Request(List<TextComponent> lines, Dim2i anchor, int limitY) {
	}

	private Tooltip() {
	}

	/** The width tooltip text may take for an element of width {@code elementWidth}. */
	public static int textWidth(int elementWidth) {
		return elementWidth - TEXT_PADDING * 2;
	}

	public static void draw(DrawContext ctx, Request r) {
		Dim2i dim = r.anchor();
		int boxWidth = dim.width();
		int boxHeight = r.lines().size() * 12 + BOX_PADDING;
		int boxX = dim.x();
		int boxY = dim.getLimitY();
		if (boxY + boxHeight > r.limitY()) {
			boxY -= boxHeight + dim.height();
		}
		if (boxY < 0) {
			boxY = dim.getLimitY();
		}
		ctx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, Theme.TOOLTIP);
		ctx.drawBorder(boxX, boxY, boxX + boxWidth, boxY + boxHeight, Theme.ACTIVATED);
		for (int i = 0; i < r.lines().size(); i++) {
			ctx.drawString(r.lines().get(i), boxX + TEXT_PADDING, boxY + TEXT_PADDING + i * 12, Theme.TEXT, true);
		}
	}
}
