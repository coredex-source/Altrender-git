/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

import java.nio.ByteBuffer;

/** A texture made through {@link Gpu} (since 0.3.0). Render thread only. */
public interface GpuTexture {

	TextureSpec spec();

	/** The GL name (for debug tools; bind it through {@link Gpu}). */
	int name();

	/** Reallocates at a new size (contents undefined); same name. */
	void resize(int width, int height);

	/** Uploads {@code pixels} (its position to its limit) in the spec's pixel format and type. */
	void upload(int level, int x, int y, int z, int width, int height, int depth, ByteBuffer pixels);

	/**
	 * Sampling state: {@code minFilter}/{@code magFilter} as {@code GL_LINEAR}, {@code GL_NEAREST} or a mipmap
	 * filter; {@code wrap} as {@code GL_REPEAT} or {@code GL_CLAMP_TO_EDGE} for every axis.
	 */
	void sampling(int minFilter, int magFilter, int wrap);

	/** Depth comparison for shadow samplers ({@code GL_LEQUAL}, or 0 for none). */
	void compare(int func);

	void generateMipmaps();

	void delete();
}
