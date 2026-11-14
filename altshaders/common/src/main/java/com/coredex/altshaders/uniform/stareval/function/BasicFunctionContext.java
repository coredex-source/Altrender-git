/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/function/BasicFunctionContext.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.function;

import com.coredex.altshaders.uniform.stareval.expression.Expression;
import com.coredex.altshaders.uniform.stareval.expression.VariableExpression;

import java.util.Map;

public class BasicFunctionContext implements FunctionContext {
	final private Map<String, Expression> variables = new java.util.HashMap<>();

	public void setVariable(String name, Expression value) {
		variables.put(name, value);
	}

	public void setIntVariable(String name, int value) {
		setVariable(name, (VariableExpression) (c, r) -> r.intReturn = value);
	}

	public void setFloatVariable(String name, float value) {
		setVariable(name, (VariableExpression) (c, r) -> r.floatReturn = value);
	}

	@Override
	public Expression getVariable(String name) {
		Expression expression = variables.get(name);
		if (expression == null)
			throw new RuntimeException("Variable hasn't been set: " + name);
		return expression;
	}

	@Override
	public boolean hasVariable(String name) {
		return variables.containsKey(name);
	}
}
