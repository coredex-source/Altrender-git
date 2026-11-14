/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/Float4MatrixCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import com.coredex.altshaders.uniform.parsing.MatrixType;
import com.coredex.altshaders.uniform.vec.Matrix4f;
import org.lwjgl.opengl.GL21;

import java.util.function.Supplier;

public class Float4MatrixCachedUniform extends VectorCachedUniform<Matrix4f> {
	final private float[] buffer = new float[16];

	public Float4MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix4f> supplier) {
		super(name, updateFrequency, new Matrix4f(), supplier);
	}

	@Override
	protected void setFrom(Matrix4f other) {
		((Matrix4f) this.cached).set(other);
	}

	@Override
	public void push(int location) {
		// `gets` the values from the matrix and put's them into a buffer
		UniformGl.gpu().uniformMatrix4(location, ((Matrix4f) this.cached).m);
	}

	@Override
	public MatrixType<Matrix4f> getType() {
		return MatrixType.MAT4;
	}
}
