/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/function/FF2BFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.function;

import com.coredex.altshaders.uniform.stareval.expression.Expression;

@FunctionalInterface
public interface FF2BFunction extends TypedFunction {
	boolean eval(float a, float b);

	@Override
	default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
		params[0].evaluateTo(context, functionReturn);
		float a = functionReturn.floatReturn;

		params[1].evaluateTo(context, functionReturn);
		float b = functionReturn.floatReturn;

		functionReturn.booleanReturn = this.eval(a, b);
	}

	@Override
	default Type getReturnType() {
		return Type.Boolean;
	}

	@Override
	default Parameter[] getParameters() {
		return new Parameter[]{Type.FloatParameter, Type.FloatParameter};
	}
}
