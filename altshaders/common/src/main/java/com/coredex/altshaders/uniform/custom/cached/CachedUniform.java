/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/cached/CachedUniform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom.cached;

import com.coredex.altshaders.uniform.stareval.expression.Expression;
import com.coredex.altshaders.uniform.stareval.expression.VariableExpression;
import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import com.coredex.altshaders.uniform.parsing.VectorType;
import com.coredex.altshaders.uniform.vec.Vector2f;
import com.coredex.altshaders.uniform.vec.Vector3f;
import com.coredex.altshaders.uniform.vec.Vector4f;

public abstract class CachedUniform implements VariableExpression {
	private final String name;
	private final UniformUpdateFrequency updateFrequency;
	private boolean changed = true;

	public CachedUniform(String name, UniformUpdateFrequency updateFrequency) {
		this.name = name;
		this.updateFrequency = updateFrequency;
	}

	static public CachedUniform forExpression(String name, Type type, Expression expression, FunctionContext context) {
		final FunctionReturn held = new FunctionReturn();
		final UniformUpdateFrequency frequency = UniformUpdateFrequency.CUSTOM;
		if (type.equals(Type.Boolean)) {
			return new BooleanCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return held.booleanReturn;
			});
		} else if (type.equals(Type.Int)) {
			return new IntCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return held.intReturn;
			});
		} else if (type.equals(Type.Float)) {
			return new FloatCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return held.floatReturn;
			});
		} else if (type.equals(VectorType.VEC2)) {
			return new Float2VectorCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return (Vector2f) held.objectReturn;
			});
		} else if (type.equals(VectorType.VEC3)) {
			return new Float3VectorCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return (Vector3f) held.objectReturn;
			});
		} else if (type.equals(VectorType.VEC4)) {
			return new Float4VectorCachedUniform(name, frequency, () -> {
				expression.evaluateTo(context, held);
				return (Vector4f) held.objectReturn;
			});
		} else {
			throw new IllegalArgumentException("Custom uniforms of type: " + type + " are currently not supported");
		}
	}

	public void markUnchanged() {
		this.changed = false;
	}

	public void update() {
		doUpdate();
		// TODO: Works around a logic error / architectural flaw - there's no way to
		//       know when a uniform has been uploaded to all programs, so we can never
		//       safely change this to false with the current design.
		this.changed = true;
	}

	protected abstract boolean doUpdate();

	public abstract void push(int location);

	public void pushIfChanged(int location) {
		if (this.changed)
			push(location);
	}

	@Override
	public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
		this.writeTo(functionReturn);
	}

	public abstract void writeTo(FunctionReturn functionReturn);

	public abstract Type getType();

	public String getName() {
		return name;
	}

	public UniformUpdateFrequency getUpdateFrequency() {
		return updateFrequency;
	}
}
