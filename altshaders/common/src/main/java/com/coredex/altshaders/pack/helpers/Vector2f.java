/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

/** A small mutable vector with public fields (in place of JOML's in ported code). */
public final class Vector2f {
	public float x;
	public float y;

	public Vector2f() {
	}

	public Vector2f(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public Vector2f set(float x, float y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public float x() {
		return x;
	}

	public float y() {
		return y;
	}
	@Override
	public boolean equals(Object o) {
		return o instanceof Vector2f v && v.x == x && v.y == y;
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
