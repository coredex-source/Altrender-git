/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/Float3MatrixCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import com.coredex.altshaders.uniform.parsing.MatrixType;
import com.coredex.altshaders.uniform.vec.Matrix3f;
import com.coredex.altshaders.uniform.vec.Matrix4f;

import java.util.function.Supplier;

public class Float3MatrixCachedUniform extends VectorCachedUniform<Matrix3f> {
	final private float[] buffer = new float[9];

	public Float3MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix3f> supplier) {
		super(name, updateFrequency, new Matrix3f(), supplier);
	}

	@Override
	protected void setFrom(Matrix3f other) {
		((Matrix3f) this.cached).set(other);
	}

	@Override
	public void push(int location) {
		// `gets` the values from the matrix and put's them into a buffer
		UniformGl.gpu().uniformMatrix3(location, ((Matrix3f) this.cached).m);
	}

	@Override
	public MatrixType<Matrix3f> getType() {
		return MatrixType.MAT3;
	}
}
