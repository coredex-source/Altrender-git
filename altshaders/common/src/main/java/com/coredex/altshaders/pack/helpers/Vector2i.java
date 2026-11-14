/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

/** A small mutable vector with public fields (in place of JOML's in ported code). */
public final class Vector2i {
	public int x;
	public int y;

	public Vector2i set(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public Vector2i() {
	}

	public Vector2i(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}
	@Override
	public boolean equals(Object o) {
		return o instanceof Vector2i v && v.x == x && v.y == y;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y);
	}

	@Override
	public String toString() {
		return "(x y)";
	}
}
