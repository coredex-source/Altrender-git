/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;


import java.util.List;

/**
 * Uniform blocks (UBOs) and texel buffers (buffer textures, e.g. {@code R32I} per-section data).
 * The caller lays out its blocks itself (std140); this binds them. Render thread only.
 */
public interface UniformStrategy {
	String id();

	/**
	 * Prepares a linked program and returns the names of its active uniform blocks. Block {@code i} of the result
	 * reads from binding point {@code i} (see {@link #bindBlock}).
	 */
	List<String> configureProgram(int program);

	/** The program is about to be deleted. */
	default void forgetProgram(int program) {}

	/** Called once per presented frame (statistics). */
	default void frameEnd() {}

	/** Statistics since the last call for {@code -Daltrender.debug.frameStats}, or {@code null}. */
	default String takeReport() {
		return null;
	}

	/** Highest number of blocks one program can use. */
	int maxBlocks();

	void bindBlock(int binding, int buffer, long offset, long size);

	/** Binds {@code buffer} as a texel buffer of {@code format} on texture unit {@code unit}. */
	void bindTexelBuffer(int unit, int buffer, TexFormat format);

	/** A buffer was deleted: drop anything derived from it (texel-buffer textures). */
	void forgetBuffer(int buffer);
}
