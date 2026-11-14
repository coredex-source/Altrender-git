/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.coredex.altshaders.uniform.vec.Matrix3f;
import com.coredex.altshaders.uniform.vec.Matrix4f;
import com.coredex.altshaders.uniform.vec.Vector2f;
import com.coredex.altshaders.uniform.vec.Vector2i;
import com.coredex.altshaders.uniform.vec.Vector3d;
import com.coredex.altshaders.uniform.vec.Vector3f;
import com.coredex.altshaders.uniform.vec.Vector3i;
import com.coredex.altshaders.uniform.vec.Vector4f;

/**
 * The standard uniforms' current values as text ({@code -Daltshaders.debug.uniformDump}, development.md 6.5): a
 * holder that keeps each uniform's supplier and prints them on demand.
 */
public final class UniformDump implements UniformHolder {

	private final Map<String, Supplier<String>> values = new LinkedHashMap<>();

	/** Every uniform as {@code name = value}, in registration order. */
	public List<String> lines() {
		List<String> out = new ArrayList<>(values.size());
		values.forEach((name, v) -> out.add(name + " = " + v.get()));
		return out;
	}

	private UniformDump put(String name, Supplier<String> value) {
		values.put(name, value);
		return this;
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.4f", v);
	}

	private static String floats(float[] m) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < m.length; i++) {
			b.append(i == 0 ? "" : ", ").append(f(m[i]));
		}
		return b.append(']').toString();
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency u, String name, FloatSupplier value) {
		return put(name, () -> f(value.getAsFloat()));
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency u, String name, IntSupplier value) {
		return put(name, () -> Integer.toString(value.getAsInt()));
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency u, String name, DoubleSupplier value) {
		return put(name, () -> f(value.getAsDouble()));
	}

	@Override
	public UniformHolder uniform1i(UniformUpdateFrequency u, String name, IntSupplier value) {
		return put(name, () -> Integer.toString(value.getAsInt()));
	}

	@Override
	public UniformHolder uniform1b(UniformUpdateFrequency u, String name, BooleanSupplier value) {
		return put(name, () -> Boolean.toString(value.getAsBoolean()));
	}

	@Override
	public UniformHolder uniform2f(UniformUpdateFrequency u, String name, Supplier<Vector2f> value) {
		return put(name, () -> {
			Vector2f v = value.get();
			return "(" + f(v.x) + ", " + f(v.y) + ")";
		});
	}

	@Override
	public UniformHolder uniform2i(UniformUpdateFrequency u, String name, Supplier<Vector2i> value) {
		return put(name, () -> {
			Vector2i v = value.get();
			return "(" + v.x + ", " + v.y + ")";
		});
	}

	@Override
	public UniformHolder uniform3f(UniformUpdateFrequency u, String name, Supplier<Vector3f> value) {
		return put(name, () -> {
			Vector3f v = value.get();
			return "(" + f(v.x) + ", " + f(v.y) + ", " + f(v.z) + ")";
		});
	}

	@Override
	public UniformHolder uniform3i(UniformUpdateFrequency u, String name, Supplier<Vector3i> value) {
		return put(name, () -> {
			Vector3i v = value.get();
			return "(" + v.x + ", " + v.y + ", " + v.z + ")";
		});
	}

	@Override
	public UniformHolder uniformTruncated3f(UniformUpdateFrequency u, String name, Supplier<Vector4f> value) {
		return put(name, () -> {
			Vector4f v = value.get();
			return "(" + f(v.x) + ", " + f(v.y) + ", " + f(v.z) + ")";
		});
	}

	@Override
	public UniformHolder uniform3d(UniformUpdateFrequency u, String name, Supplier<Vector3d> value) {
		return put(name, () -> {
			Vector3d v = value.get();
			return "(" + f(v.x) + ", " + f(v.y) + ", " + f(v.z) + ")";
		});
	}

	@Override
	public UniformHolder uniform4f(UniformUpdateFrequency u, String name, Supplier<Vector4f> value) {
		return put(name, () -> {
			Vector4f v = value.get();
			return "(" + f(v.x) + ", " + f(v.y) + ", " + f(v.z) + ", " + f(v.w) + ")";
		});
	}

	@Override
	public UniformHolder uniform4fArray(UniformUpdateFrequency u, String name, Supplier<float[]> value) {
		return put(name, () -> floats(value.get()));
	}

	@Override
	public UniformHolder uniformMatrix(UniformUpdateFrequency u, String name, Supplier<Matrix4f> value) {
		return put(name, () -> floats(value.get().m));
	}

	@Override
	public UniformHolder uniformMatrix3(UniformUpdateFrequency u, String name, Supplier<Matrix3f> value) {
		return put(name, () -> floats(value.get().m));
	}

	@Override
	public UniformHolder uniformMatrixFromArray(UniformUpdateFrequency u, String name, Supplier<float[]> value) {
		return put(name, () -> floats(value.get()));
	}

	@Override
	public UniformHolder externallyManagedUniform(String name, UniformType type) {
		return this;
	}
}
