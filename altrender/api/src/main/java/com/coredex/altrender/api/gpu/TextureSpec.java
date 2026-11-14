/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

/**
 * A texture to create (since 0.3.0). GL enums as ints, so any format a shader pack names can be asked for.
 *
 * @param target         {@code GL_TEXTURE_1D}, {@code _2D}, {@code _3D} or {@code _RECTANGLE}
 * @param internalFormat e.g. {@code GL_RGBA16F}, {@code GL_DEPTH_COMPONENT32F}, {@code GL_R32UI}
 * @param pixelFormat    the format of uploads (and of the empty allocation), e.g. {@code GL_RGBA}, {@code GL_RED_INTEGER}
 * @param pixelType      e.g. {@code GL_UNSIGNED_BYTE}, {@code GL_FLOAT}
 * @param height         1 for 1D
 * @param depth          1 unless 3D
 * @param levels         mip levels, 1 for none
 */
public record TextureSpec(int target, int internalFormat, int pixelFormat, int pixelType, int width, int height,
		int depth, int levels) {

	public static TextureSpec of2d(int internalFormat, int pixelFormat, int pixelType, int width, int height) {
		return new TextureSpec(0x0DE1, internalFormat, pixelFormat, pixelType, width, height, 1, 1);
	}

	public TextureSpec withSize(int width, int height) {
		return new TextureSpec(target, internalFormat, pixelFormat, pixelType, width, height, depth, levels);
	}
}
