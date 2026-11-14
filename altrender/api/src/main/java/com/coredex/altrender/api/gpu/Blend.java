/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

/** Blend factors (GL enums), colour and alpha separately (since 0.3.0); the equation is always add. */
public record Blend(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
	/** {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} for colour and alpha. */
	public static final Blend ALPHA = new Blend(0x0302, 0x0303, 0x0302, 0x0303);
	/** No blending (for {@code Gpu.gameBlend}: blending off). */
	public static final Blend NONE = new Blend(0, 0, 0, 0);
}
