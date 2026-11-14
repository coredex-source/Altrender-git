/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A column-major 4×4 float matrix ({@code m[column * 4 + row]}), in place of JOML's in ported code. */
public final class Matrix4f {
	public final float[] m = new float[16];

	public Matrix4f() {
		for (int i = 0; i < 4; i++) {
			m[i * 4 + i] = 1;
		}
	}

	public Matrix4f set(Matrix4f o) {
		System.arraycopy(o.m, 0, m, 0, 16);
		return this;
	}

	public Matrix4f set(float[] columnMajor) {
		System.arraycopy(columnMajor, 0, m, 0, 16);
		return this;
	}

	public float get(int column, int row) {
		return m[column * 4 + row];
	}

	public void set(int column, int row, float v) {
		m[column * 4 + row] = v;
	}

	public Vector4f getColumn(int column, Vector4f out) {
		return out.set(m[column * 4], m[column * 4 + 1], m[column * 4 + 2], m[column * 4 + 3]);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Matrix4f v && java.util.Arrays.equals(m, v.m);
	}

	@Override
	public int hashCode() {
		return java.util.Arrays.hashCode(m);
	}
}
