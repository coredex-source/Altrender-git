/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/CustomUniformFixedInputUniformsHolder.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom;

import java.util.Map;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.uniform.FloatSupplier;
import com.coredex.altshaders.uniform.UniformHolder;
import com.coredex.altshaders.uniform.UniformType;
import com.coredex.altshaders.uniform.UniformUpdateFrequency;
import com.coredex.altshaders.uniform.custom.cached.BooleanCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.CachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Float2VectorCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Float3MatrixCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Float3VectorCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Float4MatrixCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Float4VectorCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.FloatCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Int2VectorCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.Int3VectorCachedUniform;
import com.coredex.altshaders.uniform.custom.cached.IntCachedUniform;
import com.coredex.altshaders.uniform.vec.Matrix3f;
import com.coredex.altshaders.uniform.vec.Matrix4f;
import com.coredex.altshaders.uniform.vec.Vector2f;
import com.coredex.altshaders.uniform.vec.Vector2i;
import com.coredex.altshaders.uniform.vec.Vector3d;
import com.coredex.altshaders.uniform.vec.Vector3f;
import com.coredex.altshaders.uniform.vec.Vector3i;
import com.coredex.altshaders.uniform.vec.Vector4f;

import java.util.Collection;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CustomUniformFixedInputUniformsHolder {
	final private Map<String, CachedUniform> inputVariables;

	public CustomUniformFixedInputUniformsHolder(
		Map<String, CachedUniform> inputVariables) {
		this.inputVariables = inputVariables;
	}

	public Type getType(String name) {
		CachedUniform uniform = this.inputVariables.get(name);
		if (uniform == null)
			return null;
		return uniform.getType();
	}

	public boolean containsKey(String name) {
		return this.inputVariables.containsKey(name);
	}

	public CachedUniform getUniform(String name) {
		return this.inputVariables.get(name);
	}

	public void updateAll() {
		for (CachedUniform value : this.inputVariables.values()) {
			value.update();
		}
	}

	public Collection<CachedUniform> getAll() {
		return this.inputVariables.values();
	}

	public static class Builder implements UniformHolder {
		final private Map<String, CachedUniform> inputVariables = new java.util.HashMap<>();

		private Builder put(String name, CachedUniform uniform) {
			if (inputVariables.containsKey(name)) {
				PackEnv.LOG.warn("Duplicated fixed uniform supplied as inputs to the Custom uniform holder: " + name);
				return this;
			}
			inputVariables.put(name, uniform);
			return this;
		}

		@Override
		public Builder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value) {
			return this.put(name, new FloatCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
			return this.put(name, new FloatCachedUniform(name, updateFrequency, value::getAsInt));
		}

		@Override
		public Builder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value) {
			return this.put(name, new FloatCachedUniform(name, updateFrequency, () -> (float) value.getAsDouble()));
		}

		@Override
		public Builder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
			return this.put(name, new IntCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value) {
			return this.put(name, new BooleanCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value) {
			return this.put(name, new Float2VectorCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2i> value) {
			return this.put(name, new Int2VectorCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value) {
			return this.put(name, new Float3VectorCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3i> value) {
			return this.put(name, new Int3VectorCachedUniform(name, updateFrequency, value));
		}

		@Override
		public Builder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
			Vector3f held = new Vector3f();
			return this.put(name, new Float3VectorCachedUniform(name, updateFrequency, () -> {
				Vector4f vec = value.get();
				held.set(vec.x(), vec.y(), vec.z());
				return held;
			}));
		}

		@Override
		public UniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value) {
			Vector3f held = new Vector3f();
			return this.put(name, new Float3VectorCachedUniform(name, updateFrequency, () -> {
				Vector3d vec = value.get();
				held.set((float) vec.x, (float) vec.y, (float) vec.z);
				return held;
			}));
		}

		@Override
		public UniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
			Vector4f held = new Vector4f();
			return this.put(name, new Float4VectorCachedUniform(name, updateFrequency, () -> {
				Vector4f vec = value.get();
				held.set(vec.x(), vec.y(), vec.z(), vec.w());
				return held;
			}));
		}

		@Override
		public UniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
			Vector4f held = new Vector4f();
			return this.put(name, new Float4VectorCachedUniform(name, updateFrequency, () -> {
				float[] vec = value.get();
				held.set(vec);
				return held;
			}));
		}

		@Override
		public UniformHolder uniformMatrix(
			UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4f> value) {
			return this.put(name, new Float4MatrixCachedUniform(name, updateFrequency, value));
		}

		@Override
		public UniformHolder uniformMatrix3(
			UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix3f> value) {
			return this.put(name, new Float3MatrixCachedUniform(name, updateFrequency, value));
		}

		@Override
		public UniformHolder uniformMatrixFromArray(
			UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
			Matrix4f held = new Matrix4f();

			return this.put(name, new Float4MatrixCachedUniform(name, updateFrequency, () -> {
				held.set(value.get());
				return held;
			}));
		}

		@Override
		public UniformHolder externallyManagedUniform(String name, UniformType type) {
			// TODO: what is this function for?
			return this;
		}

		public CustomUniformFixedInputUniformsHolder build() {
			return new CustomUniformFixedInputUniformsHolder(Map.copyOf(this.inputVariables));
		}
	}
}
