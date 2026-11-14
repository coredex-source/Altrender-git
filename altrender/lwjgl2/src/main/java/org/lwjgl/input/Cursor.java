/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.input;

import java.nio.IntBuffer;

import org.lwjgl.LWJGLException;

/**
 * LWJGL2's native cursor (Altrender's LWJGL2 layer). Old versions only build a blank one to hide the pointer; that is
 * all this supports (any cursor whose pixels are all transparent hides the pointer, others show the default).
 */
public class Cursor {

	public static final int CURSOR_ONE_BIT_TRANSPARENCY = 1;
	public static final int CURSOR_8_BIT_ALPHA = 2;
	public static final int CURSOR_ANIMATION = 4;

	private final boolean blank;
	private boolean destroyed;

	public Cursor(int width, int height, int xHotspot, int yHotspot, int numImages, IntBuffer images, IntBuffer delays)
			throws LWJGLException {
		boolean anyVisible = false;
		if (images != null) {
			for (int i = images.position(); i < images.limit(); i++) {
				if ((images.get(i) >>> 24) != 0) {
					anyVisible = true;
					break;
				}
			}
		}
		this.blank = !anyVisible;
	}

	public static int getMinCursorSize() {
		return 1;
	}

	public static int getMaxCursorSize() {
		return 256;
	}

	public static int getCapabilities() {
		return CURSOR_ONE_BIT_TRANSPARENCY | CURSOR_8_BIT_ALPHA;
	}

	boolean isBlank() {
		return blank;
	}

	public void destroy() {
		destroyed = true;
	}

	boolean isDestroyed() {
		return destroyed;
	}
}
