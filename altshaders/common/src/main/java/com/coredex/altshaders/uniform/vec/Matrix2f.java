/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A column-major 2×2 float matrix ({@code m[column * 2 + row]}), in place of JOML's in ported code. */
public final class Matrix2f {
	public final float[] m = new float[4];

	public Matrix2f() {
		for (int i = 0; i < 2; i++) {
			m[i * 2 + i] = 1;
		}
	}

	public Matrix2f set(Matrix2f o) {
		System.arraycopy(o.m, 0, m, 0, 4);
		return this;
	}

	public Matrix2f set(float[] columnMajor) {
		System.arraycopy(columnMajor, 0, m, 0, 4);
		return this;
	}

	public float get(int column, int row) {
		return m[column * 2 + row];
	}

	public void set(int column, int row, float v) {
		m[column * 2 + row] = v;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Matrix2f v && java.util.Arrays.equals(m, v.m);
	}

	@Override
	public int hashCode() {
		return java.util.Arrays.hashCode(m);
	}
}
