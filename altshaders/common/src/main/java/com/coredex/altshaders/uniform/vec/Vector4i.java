/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A small mutable vector with public fields, in place of JOML's in ported code. */
public final class Vector4i {
	public int x;
	public int y;
	public int z;
	public int w;

	public Vector4i() {
	}

	public Vector4i(int x, int y, int z, int w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public Vector4i set(Vector4i o) {
		this.x = o.x;
		this.y = o.y;
		this.z = o.z;
		this.w = o.w;
		return this;
	}

	public Vector4i set(int x, int y, int z, int w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
		return this;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	public int w() {
		return w;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Vector4i v && x == v.x && y == v.y && z == v.z && w == v.w;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y, z, w);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ", " + w + ")";
	}
}
