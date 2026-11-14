/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import java.nio.ByteBuffer;

/** Buffer storage per tier: create, upload, map, copy (development.md 0.4). Render thread only. */
public interface BufferStrategy {
	String id();

	/**
	 * Allocates {@code size} bytes of uninitialised storage. Returns the GL buffer name.
	 *
	 * @param usage {@link BufferUsage} bits, used to pick a driver usage hint
	 * @throws OutOfGpuMemoryException if the driver reports GL_OUT_OF_MEMORY
	 */
	int create(long size, int usage);

	/** Allocates storage initialised with {@code data}'s remaining bytes. Doesn't keep a reference to {@code data}. */
	int create(ByteBuffer data, int usage);

	void delete(int buffer);

	/** Copies {@code data}'s remaining bytes into the buffer at {@code offset}, ordered with later GPU work. */
	void write(int buffer, long offset, ByteBuffer data);

	/**
	 * Maps a range for CPU access until {@link #unmap}. Write-only maps are unsynchronized (the caller fences);
	 * read maps wait for pending GPU writes. One mapped range per buffer at a time.
	 */
	ByteBuffer map(int buffer, long offset, long length, boolean read, boolean write);

	void unmap(int buffer);

	/** GPU-side copy between (or within, non-overlapping) buffers. */
	void copy(int source, long sourceOffset, int target, long targetOffset, long length);

	/** The CPU memory of {@code buffer} if it lives there (GL 2.1 client buffers), else {@code null}. */
	default java.nio.ByteBuffer cpuMemory(int buffer) {
		return null;
	}

	/** Changes counter of a CPU-memory buffer ({@link #cpuMemory}). */
	default long cpuVersion(int buffer) {
		return 0;
	}
}
