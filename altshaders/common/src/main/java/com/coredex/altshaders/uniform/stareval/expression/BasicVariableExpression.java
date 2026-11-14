/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/expression/BasicVariableExpression.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.expression;

import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;

public class BasicVariableExpression implements VariableExpression {
	final private String name;
	final private Type type;

	public BasicVariableExpression(String name, Type type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public void evaluateTo(FunctionContext c, FunctionReturn r) {
		c.getVariable(name).evaluateTo(c, r);
	}

	@Override
	public Expression partialEval(FunctionContext context, FunctionReturn functionReturn) {
		if (context.hasVariable(this.name)) {
			context.getVariable(this.name).evaluateTo(context, functionReturn);
			return type.createConstant(functionReturn);
		} else {
			return this;
		}
	}

}
