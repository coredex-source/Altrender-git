/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

/** A small mutable vector with public fields (in place of JOML's in ported code). */
public final class Vector4f {
	public float x;
	public float y;
	public float z;
	public float w;

	public Vector4f(float d) {
		this(d, d, d, d);
	}

	public Vector4f() {
	}

	public Vector4f(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public float x() {
		return x;
	}

	public float y() {
		return y;
	}

	public float z() {
		return z;
	}

	public float w() {
		return w;
	}
	@Override
	public boolean equals(Object o) {
		return o instanceof Vector4f v && v.x == x && v.y == y && v.z == z && v.w == w;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y, z, w);
	}

	@Override
	public String toString() {
		return "(x y z w)";
	}
}
