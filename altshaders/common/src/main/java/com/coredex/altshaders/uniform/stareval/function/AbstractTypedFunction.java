/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/function/AbstractTypedFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.function;

import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractTypedFunction implements TypedFunction {
	private final Type returnType;
	private final Parameter[] parameters;
	private final int priority;
	private final boolean isPure;

	public AbstractTypedFunction(Type returnType, Parameter[] parameters, int priority, boolean isPure) {
		this.returnType = returnType;
		this.parameters = parameters;
		this.priority = priority;
		this.isPure = isPure;
	}

	public AbstractTypedFunction(Type returnType, Type[] parameterType) {
		this.returnType = returnType;
		this.parameters = Arrays.stream(parameterType).map(Parameter::new).toArray(Parameter[]::new);
		this.priority = 0;
		this.isPure = true;
	}

	@Override
	public Type getReturnType() {
		return this.returnType;
	}

	@Override
	public Parameter[] getParameters() {
		return this.parameters;
	}

	@Override
	public boolean isPure() {
		return this.isPure;
	}

	@Override
	public int priority() {
		return this.priority;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AbstractTypedFunction func) {
			return Objects.equals(returnType, func.returnType) &&
				Arrays.equals(parameters, func.parameters) &&
				priority == func.priority &&
				isPure == func.isPure;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(returnType, Arrays.hashCode(parameters), priority, isPure);
	}

	@Override
	public String toString() {
		return TypedFunction.format(this, "unknown");
	}
}
