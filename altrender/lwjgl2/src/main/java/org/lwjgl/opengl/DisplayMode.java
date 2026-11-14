/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.opengl;

/** LWJGL2's display mode (Altrender's LWJGL2 layer). */
public final class DisplayMode {

	private final int width;
	private final int height;
	private final int bpp;
	private final int freq;
	private final boolean fullscreen;

	public DisplayMode(int width, int height) {
		this(width, height, 0, 0, false);
	}

	DisplayMode(int width, int height, int bpp, int freq, boolean fullscreen) {
		this.width = width;
		this.height = height;
		this.bpp = bpp;
		this.freq = freq;
		this.fullscreen = fullscreen;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getBitsPerPixel() {
		return bpp;
	}

	public int getFrequency() {
		return freq;
	}

	public boolean isFullscreenCapable() {
		return fullscreen;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof DisplayMode m && m.width == width && m.height == height && m.bpp == bpp && m.freq == freq;
	}

	@Override
	public int hashCode() {
		return ((width * 31 + height) * 31 + bpp) * 31 + freq;
	}

	@Override
	public String toString() {
		return width + " x " + height + " x " + bpp + " @" + freq + "Hz";
	}
}
