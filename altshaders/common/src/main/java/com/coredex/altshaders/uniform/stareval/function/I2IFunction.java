/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/function/I2IFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.function;

import com.coredex.altshaders.uniform.stareval.expression.Expression;

@FunctionalInterface
public interface I2IFunction extends TypedFunction {
	int eval(int a);

	@Override
	default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
		params[0].evaluateTo(context, functionReturn);
		functionReturn.intReturn = this.eval(functionReturn.intReturn);
	}

	@Override
	default Type getReturnType() {
		return Type.Int;
	}

	@Override
	default Parameter[] getParameters() {
		return new Parameter[]{Type.IntParameter};
	}
}
