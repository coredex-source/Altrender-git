/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/function/TypedFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.function;

import com.coredex.altshaders.uniform.stareval.expression.Expression;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public interface TypedFunction {

	static String format(TypedFunction function, String name) {
		return String.format("%s %s(%s) (priority: %d, pure:%s)",
			function.getReturnType().toString(),
			name,
			Arrays.stream(function.getParameters())
				.map(param -> param.constant() ? "const " + param.type() : param.type().toString())
				.collect(Collectors.joining(", ")),
			function.priority(),
			function.isPure() ? "yes" : "no"
		);
	}

	Type getReturnType();

	Parameter[] getParameters();

	void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn);

	default boolean isPure() {
		return true;
	}

	default int priority() {
		return 0;
	}

	class Parameter {
		private final Type type;
		private final boolean isConstant;

		public Parameter(Type type, boolean isConstant) {
			this.type = type;
			this.isConstant = isConstant;
		}

		public Parameter(Type type) {
			this(type, false);
		}

		public Type type() {
			return this.type;
		}

		public boolean constant() {
			return this.isConstant;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Parameter p) {
				return Objects.equals(type, p.type) && Objects.equals(isConstant, p.isConstant);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(type) + 3192 + Objects.hashCode(isConstant);
		}
	}
}

