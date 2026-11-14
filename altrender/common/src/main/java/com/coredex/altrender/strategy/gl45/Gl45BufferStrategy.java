/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl45;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.BufferStrategy;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.OutOfGpuMemoryException;
import com.coredex.altrender.strategy.StreamBuffer;
import com.coredex.altrender.strategy.UniformStrategy;
import com.coredex.altrender.strategy.VertexInputStrategy;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * GL 4.5 buffers: created and edited by name (DSA, no binding), immutable storage ({@code glNamedBufferStorage}). Every
 * buffer that may be mapped ({@code MAP_READ}/{@code MAP_WRITE}) is mapped once, persistently and coherently, when it's
 * created: {@link #map} hands out a slice of that mapping and {@link #unmap} does nothing (development.md 4.2's
 * persistent arenas and upload rings), which removes a map/unmap pair per upload. Reads, and writes when maps must
 * synchronize, wait on a fence first.
 */
public final class Gl45BufferStrategy implements BufferStrategy {
	private final GlObjectCounts counts;
	private final boolean unsynchronizedMaps;
	private final GlState state;
	private final VertexInputStrategy vertexInput;
	private final UniformStrategy uniform;
	/** Persistent mappings: buffer name → address of byte 0. */
	private final Map<Integer, Long> mapped = new HashMap<>();
	/** Upload staging (development.md 4.2): writes are copied into the stream ring and from there on the GPU. */
	private StreamBuffer staging;

	/**
	 * @param unsynchronizedMaps false = every map waits for the GPU (debug switch {@code altrender.debug.syncMaps})
	 */
	public Gl45BufferStrategy(GlObjectCounts counts, boolean unsynchronizedMaps, GlState state,
							  VertexInputStrategy vertexInput, UniformStrategy uniform) {
		this.counts = counts;
		this.unsynchronizedMaps = unsynchronizedMaps;
		this.state = state;
		this.vertexInput = vertexInput;
		this.uniform = uniform;
	}

	@Override
	public String id() {
		return "gl45-persistent";
	}

	@Override
	public int create(long size, int usage) {
		int buffer = GL45C.glCreateBuffers();
		// Immutable storage can't be empty; a zero-byte buffer gets one byte nobody reads.
		GL45C.glNamedBufferStorage(buffer, Math.max(1, size), storageFlags(usage));
		return finish(buffer, Math.max(1, size), usage);
	}

	@Override
	public int create(ByteBuffer data, int usage) {
		int buffer = GL45C.glCreateBuffers();
		if (data.remaining() == 0) {
			GL45C.glNamedBufferStorage(buffer, 1, storageFlags(usage));
			return finish(buffer, 1, usage);
		}
		GL45C.glNamedBufferStorage(buffer, data, storageFlags(usage));
		return finish(buffer, data.remaining(), usage);
	}

	private int finish(int buffer, long size, int usage) {
		if (GL11C.glGetError() == GL11C.GL_OUT_OF_MEMORY) {
			GL15Delete.delete(buffer);
			throw new OutOfGpuMemoryException("glNamedBufferStorage(" + size + " bytes) ran out of memory");
		}
		if (mappable(usage)) {
			long address = GL45C.nglMapNamedBufferRange(buffer, 0, size, mapFlags(usage));
			if (address == 0L) {
				int error = GL11C.glGetError();
				GL15Delete.delete(buffer);
				throw new IllegalStateException("persistent map of a " + size + "-byte buffer failed, GL error 0x"
						+ Integer.toHexString(error));
			}
			mapped.put(buffer, address);
		}
		counts.created(GlObjectCounts.Kind.BUFFER);
		return buffer;
	}

	@Override
	public void delete(int buffer) {
		uniform.forgetBuffer(buffer);
		if (mapped.remove(buffer) != null) {
			GL45C.glUnmapNamedBuffer(buffer);
		}
		GL15Delete.delete(buffer);
		state.forgetBuffer(buffer);
		vertexInput.forgetBuffer(buffer);
		counts.deleted(GlObjectCounts.Kind.BUFFER);
	}

	/** Uploads go through {@code ring} from now on (null: {@code glNamedBufferSubData}). */
	public void setStaging(StreamBuffer ring) {
		this.staging = ring;
	}

	@Override
	public void write(int buffer, long offset, ByteBuffer data) {
		int n = data.remaining();
		if (staging != null && buffer != staging.buffer()) {
			long at = staging.alloc(n, 16);
			if (at >= 0) {
				MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(staging.memory()) + at, n);
				GL45C.glCopyNamedBufferSubData(staging.buffer(), buffer, at, offset, n);
				return;
			}
		}
		GL45C.glNamedBufferSubData(buffer, offset, data);
	}

	@Override
	public ByteBuffer map(int buffer, long offset, long length, boolean read, boolean write) {
		Long address = mapped.get(buffer);
		if (address == null) {
			throw new IllegalStateException("buffer " + buffer + " wasn't created mappable (no MAP_READ/MAP_WRITE usage)");
		}
		if (read || !unsynchronizedMaps) {
			// Coherent mappings show GPU writes once the commands that made them have completed.
			waitForGpu();
		}
		return MemoryUtil.memByteBuffer(address + offset, Math.toIntExact(length));
	}

	@Override
	public void unmap(int buffer) {
		// Persistent and coherent: nothing to flush, the mapping stays.
	}

	@Override
	public void copy(int source, long sourceOffset, int target, long targetOffset, long length) {
		GL45C.glCopyNamedBufferSubData(source, target, sourceOffset, targetOffset, length);
	}

	private static boolean mappable(int usage) {
		return BufferUsage.has(usage, BufferUsage.MAP_READ) || BufferUsage.has(usage, BufferUsage.MAP_WRITE);
	}

	private static int storageFlags(int usage) {
		int flags = GL44C.GL_DYNAMIC_STORAGE_BIT; // glNamedBufferSubData
		if (mappable(usage)) {
			flags |= mapFlags(usage);
		}
		if (BufferUsage.has(usage, BufferUsage.MAP_READ) || BufferUsage.has(usage, BufferUsage.HINT_CLIENT_STORAGE)) {
			flags |= GL44C.GL_CLIENT_STORAGE_BIT;
		}
		return flags;
	}

	private static int mapFlags(int usage) {
		// Readable too when only written: a coherent write-only mapping read by mistake would be undefined, not slow.
		int flags = GL30C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;
		if (BufferUsage.has(usage, BufferUsage.MAP_READ)) {
			flags |= GL30C.GL_MAP_READ_BIT;
		}
		return flags;
	}

	private static void waitForGpu() {
		long fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		try {
			int flags = GL32C.GL_SYNC_FLUSH_COMMANDS_BIT;
			while (true) {
				int result = GL32C.glClientWaitSync(fence, flags, 100_000_000L);
				if (result == GL32C.GL_ALREADY_SIGNALED || result == GL32C.GL_CONDITION_SATISFIED
						|| result == GL32C.GL_WAIT_FAILED) {
					return;
				}
				flags = 0;
			}
		} finally {
			GL32C.glDeleteSync(fence);
		}
	}

	/** {@code glDeleteBuffers} (GL 1.5; no DSA variant). */
	private static final class GL15Delete {
		static void delete(int buffer) {
			org.lwjgl.opengl.GL15C.glDeleteBuffers(buffer);
		}
	}
}
