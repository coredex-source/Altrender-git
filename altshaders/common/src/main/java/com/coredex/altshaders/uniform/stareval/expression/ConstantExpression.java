/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/expression/ConstantExpression.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.expression;

import com.coredex.altshaders.uniform.stareval.function.Type;

import java.util.Collection;

public abstract class ConstantExpression implements Expression {
	private final Type type;

	protected ConstantExpression(Type type) {
		this.type = type;
	}

	public Type getType() {
		return this.type;
	}

	@Override
	public void listVariables(Collection<? super VariableExpression> variables) {
	}
}
