/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

/** A small mutable vector with public fields (in place of JOML's in ported code). */
public final class Vector3i {
	public int x;
	public int y;
	public int z;

	public Vector3i() {
	}

	public Vector3i(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
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
	@Override
	public boolean equals(Object o) {
		return o instanceof Vector3i v && v.x == x && v.y == y && v.z == z;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return "(x y z)";
	}
}
