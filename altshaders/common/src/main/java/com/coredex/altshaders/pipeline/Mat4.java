/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

/** Column-major 4×4 float matrices as {@code float[16]} (the GL layout). */
public final class Mat4 {

	private Mat4() {
	}

	public static float[] identity() {
		float[] m = new float[16];
		m[0] = m[5] = m[10] = m[15] = 1;
		return m;
	}

	/** The inverse of {@code m} into {@code out} (identity when {@code m} is singular). */
	public static float[] invert(float[] m, float[] out) {
		float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
		float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
		float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
		float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];
		float b00 = a00 * a11 - a01 * a10, b01 = a00 * a12 - a02 * a10, b02 = a00 * a13 - a03 * a10;
		float b03 = a01 * a12 - a02 * a11, b04 = a01 * a13 - a03 * a11, b05 = a02 * a13 - a03 * a12;
		float b06 = a20 * a31 - a21 * a30, b07 = a20 * a32 - a22 * a30, b08 = a20 * a33 - a23 * a30;
		float b09 = a21 * a32 - a22 * a31, b10 = a21 * a33 - a23 * a31, b11 = a22 * a33 - a23 * a32;
		float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
		if (det == 0 || Float.isNaN(det)) {
			System.arraycopy(identity(), 0, out, 0, 16);
			return out;
		}
		float inv = 1 / det;
		out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * inv;
		out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * inv;
		out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * inv;
		out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * inv;
		out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * inv;
		out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * inv;
		out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * inv;
		out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * inv;
		out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * inv;
		out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * inv;
		out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * inv;
		out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * inv;
		out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * inv;
		out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * inv;
		out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * inv;
		out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * inv;
		return out;
	}
}
