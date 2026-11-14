/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import java.nio.ByteBuffer;

/**
 * One buffer for data the GPU reads once, in the frame it's written (development.md 4.2): per-pass uniform blocks,
 * per-draw data, indirect commands, the immediate-mode stream and (gl45) upload staging. Render thread only.
 * <p>
 * Usage: {@link #alloc} a range, write it through {@link #memory} at the returned offset (positions are absolute in
 * the buffer), {@link #commit} it, then use {@link #buffer()} with that offset in GL calls. A range stays valid until
 * the frame's end ({@link #frameEnd}); later frames may reuse it.
 * <ul>
 *   <li>gl45: persistently mapped, coherent storage; a fence per frame guards reuse. {@code commit} does nothing.</li>
 *   <li>gl33: a CPU copy committed with {@code glBufferSubData}; the storage is orphaned when the buffer wraps.</li>
 * </ul>
 */
public interface StreamBuffer {

	String id();

	/** The GL buffer name. Constant for the buffer's life (gl33 orphans storage, not names). */
	int buffer();

	/** Bytes the buffer holds; an allocation larger than a quarter of this is refused ({@code -1}). */
	long capacity();

	/**
	 * Reserves {@code bytes} at an offset aligned to {@code alignment} (a power of two, or any positive value for
	 * multiples like a vertex stride). May wait for the GPU to finish an older frame. Returns the offset, or {@code -1}
	 * if {@code bytes} is too large for this buffer.
	 */
	long alloc(long bytes, int alignment);

	/** Writable view of the whole buffer, little-endian; write the allocated range at its absolute offset. */
	ByteBuffer memory();

	/** The range is written: make it visible to the GPU. */
	void commit(long offset, long bytes);

	/** End of a frame: what this frame wrote is fenced (gl45) and may be reused once the GPU is past it. */
	void frameEnd();

	void delete();
}
