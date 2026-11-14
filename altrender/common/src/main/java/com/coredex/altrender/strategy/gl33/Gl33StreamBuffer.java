/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.coredex.altrender.strategy.BufferStrategy;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.StreamBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * The gl33 {@link StreamBuffer}: ranges are written to a CPU copy and committed with {@code glBufferSubData}. When the
 * buffer is full it's replaced by a new one (the driver keeps the old storage until the GPU is done with it), so
 * {@link #buffer()} can change after an {@link #alloc}.
 */
public final class Gl33StreamBuffer implements StreamBuffer {

	private static final int USAGE = BufferUsage.VERTEX | BufferUsage.UNIFORM | BufferUsage.HINT_CLIENT_STORAGE;

	private final BufferStrategy buffers;
	private final long capacity;
	private final ByteBuffer memory;
	private int buffer;
	private long head;

	public Gl33StreamBuffer(BufferStrategy buffers, long capacity) {
		this.buffers = buffers;
		this.capacity = capacity;
		memory = MemoryUtil.memAlloc(Math.toIntExact(capacity)).order(ByteOrder.LITTLE_ENDIAN);
		buffer = buffers.create(capacity, USAGE);
	}

	@Override
	public String id() {
		return "gl33-subdata";
	}

	@Override
	public int buffer() {
		return buffer;
	}

	@Override
	public long capacity() {
		return capacity;
	}

	@Override
	public long alloc(long bytes, int alignment) {
		if (bytes <= 0 || bytes > capacity / 4) {
			return -1;
		}
		long start = (head + alignment - 1) / alignment * alignment;
		if (start + bytes > capacity) {
			buffers.delete(buffer);
			buffer = buffers.create(capacity, USAGE);
			start = 0;
		}
		head = start + bytes;
		return start;
	}

	@Override
	public ByteBuffer memory() {
		return memory;
	}

	@Override
	public void commit(long offset, long bytes) {
		buffers.write(buffer, offset, MemoryUtil.memSlice(memory, Math.toIntExact(offset), Math.toIntExact(bytes)));
	}

	@Override
	public void frameEnd() {
	}

	@Override
	public void delete() {
		buffers.delete(buffer);
		MemoryUtil.memFree(memory);
	}
}
