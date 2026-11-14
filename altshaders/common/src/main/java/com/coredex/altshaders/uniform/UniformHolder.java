/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/uniform/UniformHolder.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform;

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

public interface UniformHolder {
	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value);

	UniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value);

	UniformHolder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value);

	UniformHolder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2i> value);

	UniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value);

	UniformHolder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3i> value);

	UniformHolder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value);

	UniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value);

	UniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value);

	UniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder uniformMatrix(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4f> value);

	UniformHolder uniformMatrix3(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix3f> value);

	UniformHolder uniformMatrixFromArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder externallyManagedUniform(String name, UniformType type);
}
