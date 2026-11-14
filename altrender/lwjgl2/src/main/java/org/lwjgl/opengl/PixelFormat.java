/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.opengl;

/**
 * LWJGL2's pixel format request (Altrender's LWJGL2 layer). Accepted for compatibility: the layer always asks SDL for
 * 24-bit depth and an 8-bit stencil, which covers what old versions request.
 */
public final class PixelFormat {

	private final int alpha;
	private final int depth;
	private final int stencil;
	private final int samples;

	public PixelFormat() {
		this(0, 8, 0, 0);
	}

	public PixelFormat(int alpha, int depth, int stencil) {
		this(alpha, depth, stencil, 0);
	}

	public PixelFormat(int alpha, int depth, int stencil, int samples) {
		this.alpha = alpha;
		this.depth = depth;
		this.stencil = stencil;
		this.samples = samples;
	}

	public PixelFormat withDepthBits(int bits) {
		return new PixelFormat(alpha, bits, stencil, samples);
	}

	public PixelFormat withStencilBits(int bits) {
		return new PixelFormat(alpha, depth, bits, samples);
	}

	public PixelFormat withAlphaBits(int bits) {
		return new PixelFormat(bits, depth, stencil, samples);
	}

	public PixelFormat withSamples(int count) {
		return new PixelFormat(alpha, depth, stencil, count);
	}

	public int getAlphaBits() {
		return alpha;
	}

	public int getDepthBits() {
		return depth;
	}

	public int getStencilBits() {
		return stencil;
	}

	public int getSamples() {
		return samples;
	}
}
