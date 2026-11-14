/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** Channel layout of 8-bit-per-channel source pixels for uploads (by channel count). */
public enum PixelLayout {
	RGBA(4), RGB(3), RG(2), R(1);

	private final int bytes;

	PixelLayout(int bytes) {
		this.bytes = bytes;
	}

	public int bytesPerPixel() {
		return bytes;
	}
}
