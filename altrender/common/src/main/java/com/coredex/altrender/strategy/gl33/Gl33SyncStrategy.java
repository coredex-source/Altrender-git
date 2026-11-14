/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.Log;
import com.coredex.altrender.strategy.SyncStrategy;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

/** GL 3.3 fences via {@code ARB_sync} (core since 3.2) and elapsed-time queries via {@code ARB_timer_query} (core in 3.3). */
public final class Gl33SyncStrategy implements SyncStrategy {
	private final GlObjectCounts counts;

	public Gl33SyncStrategy(GlObjectCounts counts) {
		this.counts = counts;
	}

	@Override
	public String id() {
		return "gl33-arbsync";
	}

	@Override
	public long fence() {
		long sync = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		counts.created(GlObjectCounts.Kind.SYNC);
		return sync;
	}

	@Override
	public boolean await(long fence, long timeoutNanos) {
		int result = GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
		return switch (result) {
			case GL32C.GL_ALREADY_SIGNALED, GL32C.GL_CONDITION_SATISFIED -> true;
			case GL32C.GL_TIMEOUT_EXPIRED -> false;
			default -> {
				// GL_WAIT_FAILED: treating it as signalled can't deadlock; a GL error is logged by KHR_debug.
				Log.warn("glClientWaitSync failed (0x" + Integer.toHexString(result) + "); treating the fence as signalled");
				yield true;
			}
		};
	}

	@Override
	public void delete(long fence) {
		GL32C.glDeleteSync(fence);
		counts.deleted(GlObjectCounts.Kind.SYNC);
	}

	@Override
	public int beginTimer() {
		int query = GL15C.glGenQueries();
		counts.created(GlObjectCounts.Kind.QUERY);
		GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
		return query;
	}

	@Override
	public void endTimer() {
		GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
	}

	@Override
	public long timerResult(int query) {
		if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) {
			return -1L;
		}
		return GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
	}

	@Override
	public void deleteTimer(int query) {
		GL15C.glDeleteQueries(query);
		counts.deleted(GlObjectCounts.Kind.QUERY);
	}

	@Override
	public boolean timestamps() {
		return true;
	}

	@Override
	public int createTimestampQuery() {
		int query = GL15C.glGenQueries();
		counts.created(GlObjectCounts.Kind.QUERY);
		return query;
	}

	@Override
	public void writeTimestamp(int query) {
		GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP);
	}

	@Override
	public long timestampResult(int query) {
		if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) {
			return -1L;
		}
		return GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
	}

	@Override
	public long timestampNow() {
		return GL33C.glGetInteger64(GL33C.GL_TIMESTAMP);
	}
}
