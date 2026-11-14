/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

/**
 * The toolkit's colours (ARGB): Sodium's options screen as Reese's Sodium Options and Celeritas draw it (owner,
 * 2026-11-12: "Sodium look"). Flat translucent black rectangles, white text, cyan for what's selected or on.
 */
public final class Theme {

	/** Sodium's {@code ELEMENT_ACTIVATED}: the selected tab, a ticked box, the tooltip border. */
	public static final int ACTIVATED = 0xFF00CBCB;
	public static final int BG = 0x90000000;
	public static final int BG_HOVERED = 0xE0202020;
	public static final int BG_DISABLED = 0x60000000;
	public static final int BG_FOCUSED = 0xE0000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DISABLED = 0x90FFFFFF;
	public static final int TEXT_GRAY = 0xFFAAAAAA;
	public static final int OUTLINE = 0xFFAAAAAA;
	public static final int TOOLTIP = 0xE0000000;

	private Theme() {
	}
}
