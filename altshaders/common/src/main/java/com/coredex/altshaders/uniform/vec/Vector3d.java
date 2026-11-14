/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform.vec;

/** A small mutable double vector, in place of JOML's in ported code. */
public final class Vector3d {
	public double x;
	public double y;
	public double z;

	public Vector3d() {
	}

	public Vector3d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3d set(Vector3d o) {
		x = o.x;
		y = o.y;
		z = o.z;
		return this;
	}
}
