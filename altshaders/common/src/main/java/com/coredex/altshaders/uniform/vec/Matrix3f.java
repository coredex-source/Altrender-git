/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A column-major 3×3 float matrix ({@code m[column * 3 + row]}), in place of JOML's in ported code. */
public final class Matrix3f {
	public final float[] m = new float[9];

	public Matrix3f() {
		for (int i = 0; i < 3; i++) {
			m[i * 3 + i] = 1;
		}
	}

	public Matrix3f set(Matrix3f o) {
		System.arraycopy(o.m, 0, m, 0, 9);
		return this;
	}

	public Matrix3f set(float[] columnMajor) {
		System.arraycopy(columnMajor, 0, m, 0, 9);
		return this;
	}

	public float get(int column, int row) {
		return m[column * 3 + row];
	}

	public void set(int column, int row, float v) {
		m[column * 3 + row] = v;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Matrix3f v && java.util.Arrays.equals(m, v.m);
	}

	@Override
	public int hashCode() {
		return java.util.Arrays.hashCode(m);
	}
}
