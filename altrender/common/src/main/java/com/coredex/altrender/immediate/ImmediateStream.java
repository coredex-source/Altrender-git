/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.immediate;

import java.nio.ByteBuffer;

import com.coredex.altrender.Log;
import com.coredex.altrender.strategy.StreamBuffer;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.system.MemoryUtil;

/**
 * The game's immediate-mode draws from the tier's {@link StreamBuffer} (development.md 3.2, 4.2): the tessellator's
 * vertex bytes are copied in and drawn from there with the same fixed-function pointers, instead of client arrays the
 * driver copies on every draw (gl45: the persistent fenced ring; gl33: {@code glBufferSubData}). Main thread only.
 * <p>
 * Off unless switched on (Performance page, or {@code -Daltrender.debug.stream=true}): on average no
 * faster than the game's client arrays on Intel/Mesa or NVIDIA; NVIDIA's 1% low is better (docs/decisions.md).
 */
public final class ImmediateStream {

	private static ImmediateStream instance;

	private long draws;
	private long refused;

	private ImmediateStream() {
	}

	/** Called once the renderer is installed (a GL 3.3+ context). */
	public static void install() {
		if (instance == null) {
			instance = new ImmediateStream();
		}
	}

	/** The stream, or {@code null} when off (the setting, or not installed). */
	public static ImmediateStream get() {
		return instance != null && com.coredex.altrender.render.Renderer.config().immediateStream() ? instance : null;
	}

	/**
	 * Copies {@code bytes} (position 0, its limit) into the tier's stream buffer and leaves that bound to
	 * {@code GL_ARRAY_BUFFER}; returns the byte offset to point the arrays at, or {@code -1} when it doesn't fit (the
	 * game's client arrays draw it then). Call {@link #unbind} after the draw.
	 */
	public long upload(ByteBuffer bytes, int stride) {
		com.coredex.altrender.render.Renderer r = com.coredex.altrender.render.Renderer.instance();
		if (r == null) {
			return -1;
		}
		StreamBuffer stream = r.strategies().stream();
		int size = bytes.limit();
		long at = stream.alloc(size, stride);
		if (at < 0) {
			refused++;
			return -1;
		}
		MemoryUtil.memCopy(MemoryUtil.memAddress0(bytes), MemoryUtil.memAddress(stream.memory()) + at, size);
		stream.commit(at, size);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, stream.buffer());
		draws++;
		return at;
	}

	/** The game's client arrays need no array buffer bound. */
	public void unbind() {
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
	}

	public String stats() {
		return "immediate stream: " + draws + " draws, " + refused + " too large (client arrays)";
	}

	/** The stream's storage belongs to the strategies: this only stops using it. */
	public static void delete() {
		instance = null;
	}
}
