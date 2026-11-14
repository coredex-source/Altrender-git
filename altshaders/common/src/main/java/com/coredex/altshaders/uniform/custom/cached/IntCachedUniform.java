/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/IntCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import org.lwjgl.opengl.GL21;

import java.util.function.IntSupplier;

public class IntCachedUniform extends CachedUniform {

	final private IntSupplier supplier;
	private int cached;

	public IntCachedUniform(String name, UniformUpdateFrequency updateFrequency, IntSupplier supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
	}

	@Override
	protected boolean doUpdate() {
		int prev = this.cached;
		this.cached = this.supplier.getAsInt();
		return prev != cached;
	}

	@Override
	public void push(int location) {
		UniformGl.gpu().uniform1i(location, this.cached);
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {
		functionReturn.intReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Int;
	}
}
