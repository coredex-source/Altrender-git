/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.strategy.PixelLayout;
import com.coredex.altrender.strategy.TexFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

/** GL enums for our formats on a GL 3.3 core context. */
public final class Gl33Formats {
	private Gl33Formats() {}

	public static int internalFormat(TexFormat format) {
		return switch (format) {
			case RGBA8 -> GL11C.GL_RGBA8;
			case R8 -> GL30C.GL_R8;
			case R8I -> GL30C.GL_R8I;
			case R32I -> GL30C.GL_R32I;
			case DEPTH32 -> GL14C.GL_DEPTH_COMPONENT32;
			case DEPTH24_STENCIL8 -> GL30C.GL_DEPTH24_STENCIL8;
			case DEPTH32F_STENCIL8 -> GL30C.GL_DEPTH32F_STENCIL8;
			case RGBA16F -> GL30C.GL_RGBA16F;
			case RGBA32F -> GL30C.GL_RGBA32F;
		};
	}

	/** Client-side format matching the texture's own layout (allocation and readback). */
	public static int externalFormat(TexFormat format) {
		return switch (format) {
			case RGBA8, RGBA16F, RGBA32F -> GL11C.GL_RGBA;
			case R8 -> GL11C.GL_RED;
			case R8I, R32I -> GL30C.GL_RED_INTEGER;
			case DEPTH32 -> GL11C.GL_DEPTH_COMPONENT;
			case DEPTH24_STENCIL8, DEPTH32F_STENCIL8 -> GL30C.GL_DEPTH_STENCIL;
		};
	}

	/** Client-side type matching {@link #externalFormat}; sizes match {@link TexFormat#pixelSize()}. */
	public static int externalType(TexFormat format) {
		return switch (format) {
			case RGBA8, R8 -> GL11C.GL_UNSIGNED_BYTE;
			case R8I -> GL11C.GL_BYTE;
			case R32I -> GL11C.GL_INT;
			case DEPTH32 -> GL11C.GL_FLOAT;
			case DEPTH24_STENCIL8 -> GL30C.GL_UNSIGNED_INT_24_8;
			case DEPTH32F_STENCIL8 -> GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
			case RGBA16F -> GL30C.GL_HALF_FLOAT;
			case RGBA32F -> GL11C.GL_FLOAT;
		};
	}

	/** Upload format for 8-bit source pixels: raw channels in order, no luminance expansion. */
	public static int uploadFormat(PixelLayout layout, boolean integerTexture) {
		if (integerTexture) {
			return switch (layout) {
				case RGBA -> GL30C.GL_RGBA_INTEGER;
				case RGB -> GL30C.GL_RGB_INTEGER;
				case RG -> GL30C.GL_RG_INTEGER;
				case R -> GL30C.GL_RED_INTEGER;
			};
		}
		return switch (layout) {
			case RGBA -> GL11C.GL_RGBA;
			case RGB -> GL11C.GL_RGB;
			case RG -> GL30C.GL_RG;
			case R -> GL11C.GL_RED;
		};
	}
}
