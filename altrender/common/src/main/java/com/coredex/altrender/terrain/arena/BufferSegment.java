/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/GlBufferSegment.java at commit 38ccea6c, licensed under
 * LGPL-3.0 (Sodium/Embeddium lineage).
 * Modified by Coredex, 2026-11-12: renamed, Altrender style.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.terrain.arena;

/** A range of a {@link BufferArena}, in elements; free or in use; linked to its neighbours in offset order. */
public final class BufferSegment {

	private final BufferArena arena;
	private boolean free;
	private int offset;
	private int length;
	private BufferSegment next;
	private BufferSegment prev;

	BufferSegment(BufferArena arena, int offset, int length) {
		this.arena = arena;
		this.offset = offset;
		this.length = length;
	}

	/** Gives the range back to the arena. */
	public void delete() {
		arena.free(this);
	}

	int end() {
		return offset + length;
	}

	/** Length in elements. */
	public int length() {
		return length;
	}

	void setLength(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length <= 0");
		}
		this.length = length;
	}

	/** Offset in elements. */
	public int offset() {
		return offset;
	}

	void setOffset(int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("offset < 0");
		}
		this.offset = offset;
	}

	boolean isFree() {
		return free;
	}

	void setFree(boolean free) {
		this.free = free;
	}

	BufferSegment next() {
		return next;
	}

	void setNext(BufferSegment next) {
		this.next = next;
	}

	BufferSegment prev() {
		return prev;
	}

	void setPrev(BufferSegment prev) {
		this.prev = prev;
	}

	/** Absorbs {@code entry}, which follows this segment. */
	void mergeInto(BufferSegment entry) {
		setLength(length + entry.length);
		setNext(entry.next);
		if (next != null) {
			next.setPrev(this);
		}
	}
}
