/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A small mutable vector with public fields, in place of JOML's in ported code. */
public final class Vector2f {
	public float x;
	public float y;

	public Vector2f() {
	}

	public Vector2f(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public Vector2f set(Vector2f o) {
		this.x = o.x;
		this.y = o.y;
		return this;
	}

	public Vector2f set(float x, float y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public Vector2f negate(Vector2f dest) {
		dest.x = -x;
		dest.y = -y;
		return dest;
	}

	public Vector2f absolute(Vector2f dest) {
		dest.x = Math.abs(x);
		dest.y = Math.abs(y);
		return dest;
	}

	public Vector2f floor(Vector2f dest) {
		dest.x = (float) Math.floor(x);
		dest.y = (float) Math.floor(y);
		return dest;
	}

	public Vector2f ceil(Vector2f dest) {
		dest.x = (float) Math.ceil(x);
		dest.y = (float) Math.ceil(y);
		return dest;
	}

	public Vector2f add(Vector2f v, Vector2f dest) {
		dest.x = x + v.x;
		dest.y = y + v.y;
		return dest;
	}

	public Vector2f sub(Vector2f v, Vector2f dest) {
		dest.x = x - v.x;
		dest.y = y - v.y;
		return dest;
	}

	public Vector2f mul(Vector2f v, Vector2f dest) {
		dest.x = x * v.x;
		dest.y = y * v.y;
		return dest;
	}

	public Vector2f div(Vector2f v, Vector2f dest) {
		dest.x = x / v.x;
		dest.y = y / v.y;
		return dest;
	}

	public Vector2f min(Vector2f v, Vector2f dest) {
		dest.x = Math.min(x, v.x);
		dest.y = Math.min(y, v.y);
		return dest;
	}

	public Vector2f max(Vector2f v, Vector2f dest) {
		dest.x = Math.max(x, v.x);
		dest.y = Math.max(y, v.y);
		return dest;
	}

	public float x() {
		return x;
	}

	public float y() {
		return y;
	}

	public Vector2f min(Vector2f v) {
		x = Math.min(x, v.x);
		y = Math.min(y, v.y);
		return this;
	}

	public Vector2f max(Vector2f v) {
		x = Math.max(x, v.x);
		y = Math.max(y, v.y);
		return this;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Vector2f v && Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ")";
	}
}
