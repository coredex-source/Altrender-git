/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/TextFormattingStyle.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

/** Colours (the 16 chat colours, in code order) and styles a text component can carry. */
public enum TextFormattingStyle {
	BLACK,
	DARK_BLUE,
	DARK_GREEN,
	DARK_AQUA,
	DARK_RED,
	DARK_PURPLE,
	GOLD,
	GRAY,
	DARK_GRAY,
	BLUE,
	GREEN,
	AQUA,
	RED,
	LIGHT_PURPLE,
	YELLOW,
	WHITE,
	STRIKETHROUGH,
	UNDERLINE,
	ITALIC;

	public boolean isColor() {
		return ordinal() < 16;
	}
}
