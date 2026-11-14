/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.Log;
import com.coredex.altrender.strategy.BufferStrategy;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.UniformStrategy;
import com.coredex.altrender.strategy.VertexInputStrategy;
import com.coredex.altrender.strategy.OutOfGpuMemoryException;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;

import java.nio.ByteBuffer;

/**
 * GL 3.3 buffers: mutable storage ({@code glBufferData}), {@code glBufferSubData} uploads, {@code glMapBufferRange}
 * maps. Everything goes through the copy binding points, so buffer work never disturbs vertex, index or uniform
 * bindings.
 */
public final class Gl33BufferStrategy implements BufferStrategy {
	private static final int SCRATCH = GL31C.GL_COPY_WRITE_BUFFER;
	private static final int SCRATCH_READ = GL31C.GL_COPY_READ_BUFFER;

	private final GlObjectCounts counts;
	private final boolean unsynchronizedMaps;
	private final GlState state;
	private final VertexInputStrategy vertexInput;
	private final UniformStrategy uniform;

	/**
	 * @param unsynchronizedMaps false = every map waits for the GPU (debug switch {@code altrender.debug.syncMaps})
	 * @param vertexInput        told about deletions (VAOs keep referencing deleted buffers)
	 * @param uniform            told about deletions (texel-buffer textures)
	 */
	public Gl33BufferStrategy(GlObjectCounts counts, boolean unsynchronizedMaps, GlState state,
							  VertexInputStrategy vertexInput, UniformStrategy uniform) {
		this.counts = counts;
		this.unsynchronizedMaps = unsynchronizedMaps;
		this.state = state;
		this.vertexInput = vertexInput;
		this.uniform = uniform;
	}

	@Override
	public String id() {
		return "gl33-bufferdata";
	}

	@Override
	public int create(long size, int usage) {
		int buffer = GL15C.glGenBuffers();
		GL15C.glBindBuffer(SCRATCH, buffer);
		GL15C.glBufferData(SCRATCH, size, hint(usage));
		checkAllocation(buffer, size);
		counts.created(GlObjectCounts.Kind.BUFFER);
		return buffer;
	}

	@Override
	public int create(ByteBuffer data, int usage) {
		int buffer = GL15C.glGenBuffers();
		GL15C.glBindBuffer(SCRATCH, buffer);
		GL15C.glBufferData(SCRATCH, data, hint(usage));
		checkAllocation(buffer, data.remaining());
		counts.created(GlObjectCounts.Kind.BUFFER);
		return buffer;
	}

	@Override
	public void delete(int buffer) {
		uniform.forgetBuffer(buffer);
		GL15C.glDeleteBuffers(buffer);
		state.forgetBuffer(buffer);
		vertexInput.forgetBuffer(buffer);
		counts.deleted(GlObjectCounts.Kind.BUFFER);
	}

	@Override
	public void write(int buffer, long offset, ByteBuffer data) {
		GL15C.glBindBuffer(SCRATCH, buffer);
		GL15C.glBufferSubData(SCRATCH, offset, data);
	}

	@Override
	public ByteBuffer map(int buffer, long offset, long length, boolean read, boolean write) {
		int access = 0;
		if (read) {
			access |= GL30C.GL_MAP_READ_BIT;
		}
		if (write) {
			access |= GL30C.GL_MAP_WRITE_BIT;
			if (!read && unsynchronizedMaps) {
				// Contract: the caller fences its own reuse of mapped ranges.
				access |= GL30C.GL_MAP_UNSYNCHRONIZED_BIT;
			}
		}
		GL15C.glBindBuffer(SCRATCH, buffer);
		ByteBuffer mapped = GL30C.glMapBufferRange(SCRATCH, offset, length, access);
		if (mapped == null) {
			throw new IllegalStateException("glMapBufferRange(offset=" + offset + ", length=" + length + ") failed, GL error 0x"
					+ Integer.toHexString(GL11C.glGetError()));
		}
		return mapped;
	}

	@Override
	public void unmap(int buffer) {
		GL15C.glBindBuffer(SCRATCH, buffer);
		if (!GL15C.glUnmapBuffer(SCRATCH)) {
			Log.warn("glUnmapBuffer reported corrupted contents for buffer " + buffer + " (display mode change?)");
		}
	}

	@Override
	public void copy(int source, long sourceOffset, int target, long targetOffset, long length) {
		GL15C.glBindBuffer(SCRATCH_READ, source);
		GL15C.glBindBuffer(SCRATCH, target);
		GL31C.glCopyBufferSubData(SCRATCH_READ, SCRATCH, sourceOffset, targetOffset, length);
	}

	/** Driver hint from how the caller says it will use the buffer. Only affects placement, never behaviour. */
	private static int hint(int usage) {
		if (BufferUsage.has(usage, BufferUsage.MAP_READ)) {
			return GL15C.GL_STREAM_READ;
		}
		if (BufferUsage.has(usage, BufferUsage.MAP_WRITE) || BufferUsage.has(usage, BufferUsage.HINT_CLIENT_STORAGE)) {
			return GL15C.GL_STREAM_DRAW;
		}
		if (BufferUsage.has(usage, BufferUsage.COPY_DST)) {
			return GL15C.GL_DYNAMIC_DRAW;
		}
		return GL15C.GL_STATIC_DRAW;
	}

	private static void checkAllocation(int buffer, long size) {
		if (GL11C.glGetError() == GL11C.GL_OUT_OF_MEMORY) {
			GL15C.glDeleteBuffers(buffer);
			throw new OutOfGpuMemoryException("glBufferData(" + size + " bytes) ran out of memory");
		}
	}
}
