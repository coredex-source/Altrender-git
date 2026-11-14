/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.framework;

/** Keys the UI reacts to, translated from each version's key codes by its host. */
public enum Key {
	TAB, ENTER, ESCAPE, SPACE, UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, BACKSPACE, DELETE,
	A, C, V, X, F,
	/** Any other key: only its character matters (through {@code charTyped}). */
	OTHER;

	/** Modifier bits passed with a key. */
	public static final class Mods {
		public static final int SHIFT = 1;
		public static final int CTRL = 2;
		public static final int ALT = 4;

		private Mods() {
		}
	}
}
