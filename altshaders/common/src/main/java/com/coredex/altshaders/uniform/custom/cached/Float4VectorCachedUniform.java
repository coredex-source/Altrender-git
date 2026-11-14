/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/Float4VectorCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import com.coredex.altshaders.uniform.parsing.VectorType;
import com.coredex.altshaders.uniform.vec.Vector4f;
import org.lwjgl.opengl.GL21;

import java.util.function.Supplier;

public class Float4VectorCachedUniform extends VectorCachedUniform<Vector4f> {

	public Float4VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector4f> supplier) {
		super(name, updateFrequency, new Vector4f(), supplier);
	}

	@Override
	protected void setFrom(Vector4f other) {
		this.cached.set(other);
	}

	@Override
	public void push(int location) {
		UniformGl.gpu().uniform4f(location, this.cached.x, this.cached.y, this.cached.z, this.cached.w);
	}

	@Override
	public VectorType getType() {
		return VectorType.VEC4;
	}
}
