/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

/**
 * The game's bitmap font as the UI renderer draws it itself (one batch with everything else, development.md 5.1): a
 * texture with glyph cells, provided by each version's host.
 */
public interface UiFont {

	/** GL name of the font texture. */
	int texture();

	/** Line spacing in GUI units. */
	int lineHeight();

	/**
	 * Fills {@code out} with {@code c}'s glyph and returns true, or false if the font has no glyph for it (it's then
	 * skipped, as the game skips it).
	 */
	boolean glyph(char c, Glyph out);

	/** A glyph: its cell in the texture (0..1), the quad size and the advance, in GUI units. */
	final class Glyph {
		public float u0;
		public float v0;
		public float u1;
		public float v1;
		public float width;
		public float height;
		public int advance;
	}
}
