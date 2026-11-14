/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl45;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.StreamBuffer;
import com.coredex.altrender.strategy.SyncStrategy;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

/**
 * The gl45 {@link StreamBuffer} (development.md 4.2): immutable storage mapped once, persistent and coherent, so
 * nothing is mapped or unmapped per frame. Each frame's ranges are fenced at its end; an allocation that would
 * overlap a range still in flight waits for that frame's fence. Not client storage: NVIDIA then keeps it in system
 * memory, and indirect commands and per-draw data read from there cost the frame up to 5 times (968 → 175 FPS).
 */
public final class Gl45StreamRing implements StreamBuffer {

	/** One frame's ranges and the fence after its last command. */
	private record Frame(long fence, List<long[]> ranges) {
	}

	private final SyncStrategy sync;
	private final GlObjectCounts counts;
	private final int buffer;
	private final long capacity;
	private final ByteBuffer memory;
	private final ArrayDeque<Frame> inFlight = new ArrayDeque<>();
	private List<long[]> current = new ArrayList<>();
	private long head;
	private long waits;

	public Gl45StreamRing(long capacity, SyncStrategy sync, GlObjectCounts counts) {
		this.sync = sync;
		this.counts = counts;
		this.capacity = capacity;
		buffer = GL45C.glCreateBuffers();
		int flags = GL30C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;
		GL45C.glNamedBufferStorage(buffer, capacity, flags);
		long address = GL45C.nglMapNamedBufferRange(buffer, 0, capacity, flags);
		if (address == 0L) {
			int error = GL11C.glGetError();
			GL15C.glDeleteBuffers(buffer);
			throw new IllegalStateException("stream ring: persistent map of " + capacity + " bytes failed, GL error 0x"
					+ Integer.toHexString(error));
		}
		memory = MemoryUtil.memByteBuffer(address, Math.toIntExact(capacity)).order(ByteOrder.LITTLE_ENDIAN);
		counts.created(GlObjectCounts.Kind.BUFFER);
		GlDebug.label(GL43C.GL_BUFFER, buffer, "stream-ring");
	}

	@Override
	public String id() {
		return "gl45-persistent-ring";
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
		while (true) {
			long start = alignUp(head, alignment);
			if (start + bytes > capacity) {
				start = 0;
			}
			long end = start + bytes;
			if (!overlapsInFlight(start, end) && !overlaps(current, start, end)) {
				head = end;
				add(current, start, end);
				return start;
			}
			// The range is still in use: wait for the oldest frame (this frame's own data: fence it first).
			if (inFlight.isEmpty()) {
				frameEnd();
			}
			Frame oldest = inFlight.poll();
			waits++;
			while (!sync.await(oldest.fence(), 1_000_000_000L)) {
				// keep waiting: the GPU must get there
			}
			sync.delete(oldest.fence());
		}
	}

	@Override
	public ByteBuffer memory() {
		return memory;
	}

	@Override
	public void commit(long offset, long bytes) {
		// Coherent mapping: visible to commands issued after the write.
	}

	@Override
	public void frameEnd() {
		// Frames the GPU has finished free their ranges without waiting.
		while (!inFlight.isEmpty() && sync.await(inFlight.peek().fence(), 0)) {
			sync.delete(inFlight.poll().fence());
		}
		if (!current.isEmpty()) {
			inFlight.add(new Frame(sync.fence(), current));
			current = new ArrayList<>();
		}
	}

	/** Allocations that had to wait for the GPU (stats). */
	public long waits() {
		return waits;
	}

	@Override
	public void delete() {
		for (Frame f : inFlight) {
			sync.delete(f.fence());
		}
		inFlight.clear();
		current.clear();
		GL45C.glUnmapNamedBuffer(buffer);
		GL15C.glDeleteBuffers(buffer);
		counts.deleted(GlObjectCounts.Kind.BUFFER);
	}

	private boolean overlapsInFlight(long start, long end) {
		for (Frame f : inFlight) {
			if (overlaps(f.ranges(), start, end)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(List<long[]> ranges, long start, long end) {
		for (long[] r : ranges) {
			if (start < r[1] && r[0] < end) {
				return true;
			}
		}
		return false;
	}

	/** Adds [start, end), merging with the last range when contiguous (one or two ranges per frame). */
	private static void add(List<long[]> ranges, long start, long end) {
		if (!ranges.isEmpty()) {
			long[] last = ranges.get(ranges.size() - 1);
			if (last[1] <= start && start - last[1] < 4096) {
				last[1] = end; // alignment padding between allocations: covered too
				return;
			}
		}
		ranges.add(new long[] {start, end});
	}

	static long alignUp(long value, int alignment) {
		return (value + alignment - 1) / alignment * alignment;
	}
}
