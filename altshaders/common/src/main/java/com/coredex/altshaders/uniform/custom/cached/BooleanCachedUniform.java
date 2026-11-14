/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/BooleanCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
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

import java.util.function.BooleanSupplier;

public class BooleanCachedUniform extends CachedUniform {

	final private BooleanSupplier supplier;
	private boolean cached;

	public BooleanCachedUniform(String name, UniformUpdateFrequency updateFrequency, BooleanSupplier supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
	}

	@Override
	protected boolean doUpdate() {
		boolean prev = this.cached;
		this.cached = this.supplier.getAsBoolean();
		return prev != cached;
	}

	@Override
	public void push(int location) {
		UniformGl.gpu().uniform1i(location, this.cached ? 1 : 0);
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {
		functionReturn.booleanReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Boolean;
	}
}
