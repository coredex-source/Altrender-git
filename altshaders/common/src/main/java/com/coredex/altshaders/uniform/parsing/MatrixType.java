/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/parsing/MatrixType.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.parsing;

import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.vec.Matrix2f;
import com.coredex.altshaders.uniform.vec.Matrix3f;
import com.coredex.altshaders.uniform.vec.Matrix4f;

import java.util.function.Supplier;

public class MatrixType<T> extends Type.ObjectType {
	public static final MatrixType<Matrix4f> MAT4 = new MatrixType<>("mat4", Matrix4f::new);
	public static MatrixType<Matrix2f> MAT2 = new MatrixType<>("mat2", Matrix2f::new);
	public static MatrixType<Matrix3f> MAT3 = new MatrixType<>("mat3", Matrix3f::new);
	final String name;

	public MatrixType(String name, Supplier<T> supplier) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
