/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/DrawContext.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; nested scissors; rounded rectangles, gradients and texture regions (development.md 5.1); textures by GL name or the host's path.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

/** What widgets draw with, in GUI units (y down). Colours are ARGB. */
public interface DrawContext extends FontMetricsProvider {

	void fill(int x1, int y1, int x2, int y2, int color);

	/** A vertical gradient. */
	void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom);

	/** A rectangle with rounded corners (anti-aliased edges). */
	void fillRounded(int x1, int y1, int x2, int y2, float radius, int color);

	default int drawString(String str, int x, int y, int color) {
		return drawString(TextComponent.literal(str), x, y, color, false);
	}

	default int drawString(TextComponent str, int x, int y, int color) {
		return drawString(str, x, y, color, false);
	}

	/** Draws the text (with the game's font, colour codes honoured) and returns the x after it. */
	int drawString(TextComponent str, int x, int y, int color, boolean shadow);

	/** A region of a texture (GL name), tinted by {@code color}. */
	void blit(int texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int color);

	/** A whole texture from the host by path (e.g. a mod's logo). */
	void blitWholeImage(String icon, int x, int y, int width, int height);

	void pushMatrix();

	void translate(double x, double y, double z);

	void popMatrix();

	/** Clips drawing to the rectangle, intersected with the current clip; nests. */
	void enableScissor(int x1, int y1, int x2, int y2);

	void disableScissor();

	default void drawBorder(int x1, int y1, int x2, int y2, int color) {
		fill(x1, y1, x2, y1 + 1, color);
		fill(x1, y2 - 1, x2, y2, color);
		fill(x1, y1, x1 + 1, y2, color);
		fill(x2 - 1, y1, x2, y2, color);
	}

	default String getModLogoPath(String modId) {
		return null;
	}

	default TextComponent getFriendlyModName(String modId) {
		return TextComponent.literal(modId);
	}
}
