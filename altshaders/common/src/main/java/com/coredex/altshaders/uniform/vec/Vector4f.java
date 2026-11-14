/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A small mutable vector with public fields, in place of JOML's in ported code. */
public final class Vector4f {
	public float x;
	public float y;
	public float z;
	public float w;

	public Vector4f() {
	}

	public Vector4f(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public Vector4f set(Vector4f o) {
		this.x = o.x;
		this.y = o.y;
		this.z = o.z;
		this.w = o.w;
		return this;
	}

	public Vector4f set(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
		return this;
	}

	public Vector4f negate(Vector4f dest) {
		dest.x = -x;
		dest.y = -y;
		dest.z = -z;
		dest.w = -w;
		return dest;
	}

	public Vector4f absolute(Vector4f dest) {
		dest.x = Math.abs(x);
		dest.y = Math.abs(y);
		dest.z = Math.abs(z);
		dest.w = Math.abs(w);
		return dest;
	}

	public Vector4f floor(Vector4f dest) {
		dest.x = (float) Math.floor(x);
		dest.y = (float) Math.floor(y);
		dest.z = (float) Math.floor(z);
		dest.w = (float) Math.floor(w);
		return dest;
	}

	public Vector4f ceil(Vector4f dest) {
		dest.x = (float) Math.ceil(x);
		dest.y = (float) Math.ceil(y);
		dest.z = (float) Math.ceil(z);
		dest.w = (float) Math.ceil(w);
		return dest;
	}

	public Vector4f add(Vector4f v, Vector4f dest) {
		dest.x = x + v.x;
		dest.y = y + v.y;
		dest.z = z + v.z;
		dest.w = w + v.w;
		return dest;
	}

	public Vector4f sub(Vector4f v, Vector4f dest) {
		dest.x = x - v.x;
		dest.y = y - v.y;
		dest.z = z - v.z;
		dest.w = w - v.w;
		return dest;
	}

	public Vector4f mul(Vector4f v, Vector4f dest) {
		dest.x = x * v.x;
		dest.y = y * v.y;
		dest.z = z * v.z;
		dest.w = w * v.w;
		return dest;
	}

	public Vector4f div(Vector4f v, Vector4f dest) {
		dest.x = x / v.x;
		dest.y = y / v.y;
		dest.z = z / v.z;
		dest.w = w / v.w;
		return dest;
	}

	public Vector4f min(Vector4f v, Vector4f dest) {
		dest.x = Math.min(x, v.x);
		dest.y = Math.min(y, v.y);
		dest.z = Math.min(z, v.z);
		dest.w = Math.min(w, v.w);
		return dest;
	}

	public Vector4f max(Vector4f v, Vector4f dest) {
		dest.x = Math.max(x, v.x);
		dest.y = Math.max(y, v.y);
		dest.z = Math.max(z, v.z);
		dest.w = Math.max(w, v.w);
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

	public float w() {
		return w;
	}

	public Vector4f min(Vector4f v) {
		x = Math.min(x, v.x);
		y = Math.min(y, v.y);
		z = Math.min(z, v.z);
		w = Math.min(w, v.w);
		return this;
	}

	public Vector4f max(Vector4f v) {
		x = Math.max(x, v.x);
		y = Math.max(y, v.y);
		z = Math.max(z, v.z);
		w = Math.max(w, v.w);
		return this;
	}

	public Vector4f set(float[] v) {
		x = v[0];
		y = v[1];
		z = v[2];
		w = v[3];
		return this;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Vector4f v && Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0 && Float.compare(z, v.z) == 0 && Float.compare(w, v.w) == 0;
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
