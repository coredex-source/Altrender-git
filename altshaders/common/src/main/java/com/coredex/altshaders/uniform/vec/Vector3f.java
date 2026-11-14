/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A small mutable vector with public fields, in place of JOML's in ported code. */
public final class Vector3f {
	public float x;
	public float y;
	public float z;

	public Vector3f() {
	}

	public Vector3f(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3f set(Vector3f o) {
		this.x = o.x;
		this.y = o.y;
		this.z = o.z;
		return this;
	}

	public Vector3f set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public Vector3f negate(Vector3f dest) {
		dest.x = -x;
		dest.y = -y;
		dest.z = -z;
		return dest;
	}

	public Vector3f absolute(Vector3f dest) {
		dest.x = Math.abs(x);
		dest.y = Math.abs(y);
		dest.z = Math.abs(z);
		return dest;
	}

	public Vector3f floor(Vector3f dest) {
		dest.x = (float) Math.floor(x);
		dest.y = (float) Math.floor(y);
		dest.z = (float) Math.floor(z);
		return dest;
	}

	public Vector3f ceil(Vector3f dest) {
		dest.x = (float) Math.ceil(x);
		dest.y = (float) Math.ceil(y);
		dest.z = (float) Math.ceil(z);
		return dest;
	}

	public Vector3f add(Vector3f v, Vector3f dest) {
		dest.x = x + v.x;
		dest.y = y + v.y;
		dest.z = z + v.z;
		return dest;
	}

	public Vector3f sub(Vector3f v, Vector3f dest) {
		dest.x = x - v.x;
		dest.y = y - v.y;
		dest.z = z - v.z;
		return dest;
	}

	public Vector3f mul(Vector3f v, Vector3f dest) {
		dest.x = x * v.x;
		dest.y = y * v.y;
		dest.z = z * v.z;
		return dest;
	}

	public Vector3f div(Vector3f v, Vector3f dest) {
		dest.x = x / v.x;
		dest.y = y / v.y;
		dest.z = z / v.z;
		return dest;
	}

	public Vector3f min(Vector3f v, Vector3f dest) {
		dest.x = Math.min(x, v.x);
		dest.y = Math.min(y, v.y);
		dest.z = Math.min(z, v.z);
		return dest;
	}

	public Vector3f max(Vector3f v, Vector3f dest) {
		dest.x = Math.max(x, v.x);
		dest.y = Math.max(y, v.y);
		dest.z = Math.max(z, v.z);
		return dest;
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

	public Vector3f min(Vector3f v) {
		x = Math.min(x, v.x);
		y = Math.min(y, v.y);
		z = Math.min(z, v.z);
		return this;
	}

	public Vector3f max(Vector3f v) {
		x = Math.max(x, v.x);
		y = Math.max(y, v.y);
		z = Math.max(z, v.z);
		return this;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Vector3f v && Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0 && Float.compare(z, v.z) == 0;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
