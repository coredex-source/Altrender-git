/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/util/Dim2i.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; offset() added.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

/** A rectangle in GUI units: position and size. */
public record Dim2i(int x, int y, int width, int height) {

	public int getLimitX() {
		return x + width;
	}

	public int getLimitY() {
		return y + height;
	}

	public boolean containsCursor(double px, double py) {
		return px >= x && px < getLimitX() && py >= y && py < getLimitY();
	}

	public int getCenterX() {
		return x + width / 2;
	}

	public int getCenterY() {
		return y + height / 2;
	}

	public Dim2i withHeight(int newHeight) {
		return new Dim2i(x, y, width, newHeight);
	}

	public Dim2i withWidth(int newWidth) {
		return new Dim2i(x, y, newWidth, height);
	}

	public Dim2i withX(int newX) {
		return new Dim2i(newX, y, width, height);
	}

	public Dim2i withY(int newY) {
		return new Dim2i(x, newY, width, height);
	}

	public Dim2i offset(int dx, int dy) {
		return new Dim2i(x + dx, y + dy, width, height);
	}

	public boolean canFitDimension(Dim2i other) {
		return x <= other.x && y <= other.y && getLimitX() >= other.getLimitX() && getLimitY() >= other.getLimitY();
	}

	public boolean overlapsWith(Dim2i other) {
		return x < other.getLimitX() && getLimitX() > other.x && y < other.getLimitY() && getLimitY() > other.y;
	}
}
