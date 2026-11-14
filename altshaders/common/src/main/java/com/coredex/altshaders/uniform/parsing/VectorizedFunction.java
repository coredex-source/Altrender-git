/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/parsing/VectorizedFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.parsing;

import com.coredex.altshaders.uniform.stareval.expression.Expression;
import com.coredex.altshaders.uniform.stareval.expression.VariableExpression;
import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.stareval.function.TypedFunction;

import java.util.Collection;

public class VectorizedFunction implements TypedFunction {
	final TypedFunction inner;
	final int size;
	final VectorType.ArrayVector returnType;
	final Parameter[] parameters;

	private final ElementAccessExpression[] vectorAccessors;
	private int index;
	private final VectorType.ArrayVector.IntObjectObjectObjectConsumer<VectorizedFunction, FunctionContext, FunctionReturn> mapper =
		(i, self, ctx, fr) -> {
			self.index = i;
			self.inner.evaluateTo(self.vectorAccessors, ctx, fr);
		};

	public VectorizedFunction(TypedFunction inner, int size) {
		this.inner = inner;
		this.size = size;

		this.returnType = new VectorType.ArrayVector(inner.getReturnType(), size);

		Parameter[] innerTypes = inner.getParameters();
		this.parameters = new Parameter[innerTypes.length];
		this.vectorAccessors = new ElementAccessExpression[innerTypes.length];

		for (int i = 0; i < innerTypes.length; i++) {
			this.parameters[i] = new Parameter(new VectorType.ArrayVector(innerTypes[i].type(), size));
			this.vectorAccessors[i] = new ElementAccessExpression(innerTypes[i].type());
		}
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
	public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
		for (int p = 0; p < params.length; p++) {
			final Expression param = params[p];
			param.evaluateTo(context, functionReturn);
			this.vectorAccessors[p].vector = functionReturn.objectReturn;
		}

		returnType.map(this, context, functionReturn, this.mapper);
	}

	class ElementAccessExpression implements Expression {
		final Type parameterType;
		Object vector;


		ElementAccessExpression(Type parameterType) {
			this.parameterType = parameterType;
		}

		@Override
		public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
			parameterType.getValueFromArray(vector, index, functionReturn);
		}


		@Override
		public void listVariables(Collection<? super VariableExpression> variables) {
			throw new IllegalStateException();
		}
	}
}
