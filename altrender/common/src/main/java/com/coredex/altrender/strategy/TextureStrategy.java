/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** Texture storage, uploads and sampler state per tier (development.md 0.4). Render thread only. */
public interface TextureStrategy {
	String id();

	/**
	 * Allocates every mip level of a 2D texture, or of all six faces when {@code cubemap}. Contents are undefined.
	 * Returns the GL texture name.
	 *
	 * @throws OutOfGpuMemoryException      if the driver reports GL_OUT_OF_MEMORY
	 * @throws UnsupportedOperationException if this tier can't represent {@code format}
	 */
	int create(TexFormat format, int width, int height, int mipLevels, boolean cubemap);

	void delete(int texture);

	/**
	 * Uploads a rectangle of 8-bit-per-channel pixels from CPU memory.
	 *
	 * @param face        cube face 0..5 (ignored unless {@code cubemap})
	 * @param address     address of the source image's first pixel (row 0, column 0)
	 * @param rowLength   source row length in pixels
	 * @param skipPixels  source column of the rectangle's first pixel
	 * @param skipRows    source row of the rectangle's first pixel
	 */
	void upload(int texture, TexFormat format, boolean cubemap, int face, int level, int x, int y, int width, int height,
				PixelLayout layout, long address, int rowLength, int skipPixels, int skipRows);

	/**
	 * Binds a texture for sampling on {@code unit}, limited to mip levels {@code baseLevel..maxLevel} (the view's
	 * range), with {@code sampler}.
	 */
	void bindForSampling(int unit, int texture, boolean cubemap, int baseLevel, int maxLevel, int sampler);

	/** Creates a sampler (a GL sampler object where the tier has them). Returns its name. */
	int createSampler(SamplerState state);

	void deleteSampler(int sampler);
}
