/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/GlBufferArena.java at commit 38ccea6c, licensed under
 * LGPL-3.0 (Sodium/Embeddium lineage).
 * Modified by Coredex, 2026-11-12: on Altrender's BufferStrategy (any tier) instead of Celeritas's command list; the
 * buffer is created on first growth (immutable storage can't be empty); uploads go straight through the strategy;
 * a debug check is switchable at runtime.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.terrain.arena;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.coredex.altrender.strategy.BufferStrategy;
import com.coredex.altrender.strategy.BufferUsage;

/**
 * One GL buffer shared by many allocations (development.md 2.3: a region's sections), sub-allocated in elements of
 * {@code stride} bytes: best fit among free segments, neighbours merged when freed. When an upload doesn't fit, the
 * arena moves to a larger buffer and packs the used segments at its end on the way (that is the defragmentation), so
 * segment offsets change: callers re-read them after {@link #upload} returns {@code true}.
 */
public final class BufferArena {

	/** {@code -Daltrender.debug.arenaCheck}: verify the segment list after every change. */
	private static final boolean CHECK = Boolean.getBoolean("altrender.debug.arenaCheck");
	private static final int LARGE_ARENA_BYTES = 4 * 1024 * 1024;
	private static final int LARGE_GROWTH_DIVISOR = 2;
	private static final int USAGE = BufferUsage.VERTEX | BufferUsage.COPY_DST | BufferUsage.COPY_SRC;

	private final BufferStrategy buffers;
	private final int stride;
	private final int minimumCapacity;
	private int buffer;
	private BufferSegment head;
	private int capacity;
	private int used;

	public BufferArena(BufferStrategy buffers, int stride, int minimumCapacityBytes) {
		if (stride <= 0) {
			throw new IllegalArgumentException("stride must be positive");
		}
		this.buffers = buffers;
		this.stride = stride;
		this.minimumCapacity = elementsFor(minimumCapacityBytes);
	}

	/** One upload: the data to place, and where it went. */
	public static final class Upload {
		final ByteBuffer data;
		BufferSegment result;

		public Upload(ByteBuffer data) {
			this.data = data;
		}

		public BufferSegment result() {
			return result;
		}
	}

	/** The GL buffer (0 until something has been uploaded). Changes when {@link #upload} returns true. */
	public int buffer() {
		return buffer;
	}

	public int stride() {
		return stride;
	}

	public long usedBytes() {
		return (long) used * stride;
	}

	public long allocatedBytes() {
		return (long) capacity * stride;
	}

	public boolean isEmpty() {
		return used <= 0;
	}

	/**
	 * Places every upload's data, growing (and packing) the buffer if needed.
	 *
	 * @return whether the buffer was replaced (every segment's offset may have changed)
	 */
	public boolean upload(List<Upload> uploads) {
		int before = buffer;
		List<Upload> queue = new ArrayList<>(uploads);
		tryUploads(queue);
		if (!queue.isEmpty()) {
			long remaining = 0;
			for (Upload u : queue) {
				remaining += elementsFor(u.data.remaining());
			}
			ensureCapacity(remaining);
			tryUploads(queue);
			if (!queue.isEmpty()) {
				throw new IllegalStateException("arena failed to place " + queue.size() + " uploads");
			}
		}
		return buffer != before;
	}

	public void delete() {
		if (buffer != 0) {
			buffers.delete(buffer);
			buffer = 0;
		}
		capacity = -1;
	}

	void free(BufferSegment entry) {
		if (entry.isFree()) {
			throw new IllegalStateException("already freed");
		}
		entry.setFree(true);
		used -= entry.length();
		BufferSegment next = entry.next();
		if (next != null && next.isFree()) {
			entry.mergeInto(next);
		}
		BufferSegment prev = entry.prev();
		if (prev != null && prev.isFree()) {
			prev.mergeInto(entry);
		}
		check();
	}

	private void tryUploads(List<Upload> queue) {
		for (Iterator<Upload> it = queue.iterator(); it.hasNext(); ) {
			Upload u = it.next();
			BufferSegment dst = alloc(elementsFor(u.data.remaining()));
			if (dst != null) {
				buffers.write(buffer, (long) dst.offset() * stride, u.data);
				u.result = dst;
				it.remove();
			}
		}
	}

	private BufferSegment alloc(int size) {
		BufferSegment a = findFree(size);
		if (a == null) {
			return null;
		}
		BufferSegment result;
		if (a.length() == size) {
			a.setFree(false);
			result = a;
		} else {
			// Split from the end of the free segment.
			BufferSegment b = new BufferSegment(this, a.end() - size, size);
			b.setNext(a.next());
			b.setPrev(a);
			if (b.next() != null) {
				b.next().setPrev(b);
			}
			a.setLength(a.length() - size);
			a.setNext(b);
			result = b;
		}
		used += result.length();
		check();
		return result;
	}

	/** Exact fit if there is one, else the smallest free segment that fits. */
	private BufferSegment findFree(int size) {
		BufferSegment best = null;
		for (BufferSegment e = head; e != null; e = e.next()) {
			if (e.isFree()) {
				if (e.length() == size) {
					return e;
				} else if (e.length() >= size && (best == null || best.length() > e.length())) {
					best = e;
				}
			}
		}
		return best;
	}

	private void ensureCapacity(long elements) {
		long required = used + elements;
		if (required * 2 <= capacity) {
			resize(capacity); // enough room, only fragmented: pack in place size
			return;
		}
		resize(growthTarget(required));
	}

	private int growthTarget(long required) {
		long base = Math.max(required, capacity);
		long increment = base * stride < LARGE_ARENA_BYTES ? base : base / LARGE_GROWTH_DIVISOR;
		long target = Math.max(base + increment, minimumCapacity);
		if (required > Integer.MAX_VALUE) {
			throw new OutOfMemoryError("arena can't grow beyond " + Integer.MAX_VALUE + " elements");
		}
		return (int) Math.min(target, Integer.MAX_VALUE);
	}

	/** Moves every used segment, packed, to the end of a new buffer of {@code newCapacity}; the start is one free run. */
	private void resize(int newCapacity) {
		if (used > newCapacity) {
			throw new IllegalArgumentException("new capacity below used size");
		}
		check();
		int tail = newCapacity - used;
		List<BufferSegment> usedSegments = new ArrayList<>();
		for (BufferSegment s = head; s != null; s = s.next()) {
			if (!s.isFree()) {
				usedSegments.add(s);
			}
		}
		int newBuffer = buffers.create((long) newCapacity * stride, USAGE);
		// Contiguous runs of used segments become one copy each.
		int writeOffset = tail;
		int runRead = -1;
		int runWrite = -1;
		int runLength = 0;
		for (int i = 0; i < usedSegments.size(); i++) {
			BufferSegment s = usedSegments.get(i);
			if (runRead >= 0 && runRead + runLength == s.offset()) {
				runLength += s.length();
			} else {
				copy(newBuffer, runRead, runWrite, runLength);
				runRead = s.offset();
				runWrite = writeOffset;
				runLength = s.length();
			}
			s.setOffset(writeOffset);
			s.setNext(i + 1 < usedSegments.size() ? usedSegments.get(i + 1) : null);
			s.setPrev(i > 0 ? usedSegments.get(i - 1) : null);
			writeOffset += s.length();
		}
		copy(newBuffer, runRead, runWrite, runLength);
		if (buffer != 0) {
			buffers.delete(buffer);
		}
		buffer = newBuffer;
		capacity = newCapacity;
		if (tail == 0) {
			head = usedSegments.isEmpty() ? null : usedSegments.get(0);
			if (head != null) {
				head.setPrev(null);
			}
		} else {
			head = new BufferSegment(this, 0, tail);
			head.setFree(true);
			head.setNext(usedSegments.isEmpty() ? null : usedSegments.get(0));
			if (head.next() != null) {
				head.next().setPrev(head);
			}
		}
		check();
	}

	private void copy(int newBuffer, int read, int write, int length) {
		if (length > 0) {
			buffers.copy(buffer, (long) read * stride, newBuffer, (long) write * stride, (long) length * stride);
		}
	}

	private int elementsFor(long bytes) {
		return (int) ((bytes + stride - 1) / stride);
	}

	private void check() {
		if (!CHECK) {
			return;
		}
		int usedSum = 0;
		for (BufferSegment s = head; s != null; s = s.next()) {
			if (s.offset() < 0 || s.end() > capacity) {
				throw new IllegalStateException("segment out of bounds");
			}
			if (!s.isFree()) {
				usedSum += s.length();
			}
			BufferSegment n = s.next();
			if (n != null) {
				if (n.offset() != s.end()) {
					throw new IllegalStateException("segments not contiguous");
				}
				if (n.isFree() && s.isFree()) {
					throw new IllegalStateException("adjacent free segments not merged");
				}
			}
		}
		if (usedSum != used || used < 0 || used > capacity) {
			throw new IllegalStateException("arena used count wrong: " + used + " vs " + usedSum);
		}
	}
}
