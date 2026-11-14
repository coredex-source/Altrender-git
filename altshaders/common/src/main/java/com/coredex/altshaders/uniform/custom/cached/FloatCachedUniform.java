/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/FloatCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.FloatSupplier;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import org.lwjgl.opengl.GL21;

public class FloatCachedUniform extends CachedUniform {

	final private FloatSupplier supplier;
	private float cached;

	public FloatCachedUniform(String name, UniformUpdateFrequency updateFrequency, FloatSupplier supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
	}

	@Override
	protected boolean doUpdate() {
		float prev = this.cached;
		this.cached = this.supplier.getAsFloat();
		return prev != cached;
	}

	@Override
	public void push(int location) {
		UniformGl.gpu().uniform1f(location, this.cached);
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {
		functionReturn.floatReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Float;
	}
}
