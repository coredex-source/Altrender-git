/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/VectorCachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;

import java.util.function.Supplier;

public abstract class VectorCachedUniform<T> extends CachedUniform {

	final protected T cached;
	final private Supplier<T> supplier;

	public VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, T cache, Supplier<T> supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
		this.cached = cache;
	}

	abstract protected void setFrom(T other);

	@Override
	protected boolean doUpdate() {
		T other = this.supplier.get();
		if (other == null) {
			PackEnv.LOG.warn("Cached Uniform supplier gave null back");
			return false;
		}
		if (!this.cached.equals(other)) {
			this.setFrom(other);
			return true;
		} else return false;
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {

		functionReturn.objectReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Float;
	}
}
