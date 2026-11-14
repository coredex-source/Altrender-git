/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * Texture formats Altrender knows, independent of GL enums (each {@link TextureStrategy} maps them to what its tier
 * can allocate). Covers vanilla's formats plus NeoForge's depth-stencil formats.
 */
public enum TexFormat {
	RGBA8(4, true, false, false, false),
	R8(1, true, false, false, false),
	/** Signed 8-bit integer, single channel (vanilla: clouds texel buffer only). */
	R8I(1, true, false, false, true),
	/** Signed 32-bit integer, single channel (Sodium's per-section fade-in times, a texel buffer). */
	R32I(4, true, false, false, true),
	/** 32-bit depth (vanilla allocates normalized GL_DEPTH_COMPONENT32). */
	DEPTH32(4, false, true, false, false),
	DEPTH24_STENCIL8(4, false, true, true, false),
	DEPTH32F_STENCIL8(8, false, true, true, false),
	/** Half-float colour (e.g. order-independent transparency targets). */
	RGBA16F(8, true, false, false, false),
	/** Float colour (e.g. depth bounds). */
	RGBA32F(16, true, false, false, false);

	private final int pixelSize;
	private final boolean color;
	private final boolean depth;
	private final boolean stencil;
	private final boolean integer;

	TexFormat(int pixelSize, boolean color, boolean depth, boolean stencil, boolean integer) {
		this.pixelSize = pixelSize;
		this.color = color;
		this.depth = depth;
		this.stencil = stencil;
		this.integer = integer;
	}

	public int pixelSize() {
		return pixelSize;
	}

	public boolean hasColor() {
		return color;
	}

	public boolean hasDepth() {
		return depth;
	}

	public boolean hasStencil() {
		return stencil;
	}

	public boolean isInteger() {
		return integer;
	}

	/** Float colour: clears need exact float values, which packed ARGB can't carry. */
	public boolean isFloat() {
		return this == RGBA16F || this == RGBA32F;
	}
}
