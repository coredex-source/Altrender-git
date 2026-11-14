/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/parsing/VectorConstructor.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.parsing;

import com.coredex.altshaders.uniform.stareval.Util;
import com.coredex.altshaders.uniform.stareval.expression.Expression;
import com.coredex.altshaders.uniform.stareval.function.AbstractTypedFunction;
import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;

import java.util.Arrays;

public class VectorConstructor extends AbstractTypedFunction {

	public VectorConstructor(Type inner, int size) {
		super(
			new VectorType.ArrayVector(inner, size),
			Util.make(new Type[size], params -> Arrays.fill(params, inner))
		);
	}

	@Override
	public VectorType.ArrayVector getReturnType() {
		return (VectorType.ArrayVector) super.getReturnType();
	}

	@Override
	public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn
		functionReturn) {
		VectorType.ArrayVector vectorType = this.getReturnType();
		vectorType.map(params, context, functionReturn, (i, p, ctx, fr) -> p[i].evaluateTo(ctx, fr));
	}
}
