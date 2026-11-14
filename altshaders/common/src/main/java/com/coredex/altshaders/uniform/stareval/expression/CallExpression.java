/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/expression/CallExpression.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.expression;

import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.TypedFunction;

import java.util.Collection;

public class CallExpression implements Expression {
	private final TypedFunction function;
	private final Expression[] arguments;

	public CallExpression(TypedFunction function, Expression[] arguments) {
		this.function = function;
		this.arguments = arguments;
	}

	@Override
	public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
		this.function.evaluateTo(this.arguments, context, functionReturn);
	}

	@Override
	public void listVariables(Collection<? super VariableExpression> variables) {
		for (Expression argument : this.arguments) {
			argument.listVariables(variables);
		}
	}

	private boolean isConstant() {
		for (Expression i : arguments) {
			if (!(i instanceof ConstantExpression)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Expression partialEval(FunctionContext context, FunctionReturn functionReturn) {
		// the dirty way would be this
		/*
			try{
				this.evaluateTo(context, functionReturn);
				return this.function.getReturnType().createConstant(functionReturn);
			}catch(Exception e){
				return this;
			}
		 */

		if (this.function.isPure() && isConstant()) {
			this.evaluateTo(context, functionReturn);
			return function.getReturnType().createConstant(functionReturn);
		}

		Expression[] partialEvaluatedParams = new Expression[this.arguments.length];
		boolean allFullySimplified = true;
		boolean noneSimplified = true;
		for (int i = 0; i < this.arguments.length; i++) {
			Expression simplified = this.arguments[i].partialEval(context, functionReturn);
			if (simplified instanceof ConstantExpression) {
				noneSimplified = false;
			} else {
				allFullySimplified = false;
				if (simplified != this.arguments[i]) {
					noneSimplified = false;
				}
			}
			partialEvaluatedParams[i] = simplified;
		}

		if (this.function.isPure() && allFullySimplified) {
			this.function.evaluateTo(partialEvaluatedParams, context, functionReturn);
			return this.function.getReturnType().createConstant(functionReturn);
		}

		if (noneSimplified) {
			return this;
		}

		return new CallExpression(this.function, partialEvaluatedParams);

	}
}
