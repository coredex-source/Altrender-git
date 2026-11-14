/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * GPU/CPU synchronisation and GPU timing: {@code ARB_sync} fences and {@code ARB_timer_query} (gl33 and gl45). Render
 * thread only.
 */
public interface SyncStrategy {
	String id();

	/** A fence after all previously issued GL commands. Returns an opaque handle. */
	long fence();

	/**
	 * Waits up to {@code timeoutNanos} (0 = poll) for the fence. Always flushes first, so a fence that was never
	 * flushed can't deadlock the wait.
	 */
	boolean await(long fence, long timeoutNanos);

	void delete(long fence);

	/** Starts a GPU elapsed-time query (one at a time). Returns its name. */
	int beginTimer();

	/** Ends the active elapsed-time query. */
	void endTimer();

	/** Elapsed GPU nanoseconds of an ended query, or -1 while the result isn't available yet. Never blocks. */
	long timerResult(int query);

	void deleteTimer(int query);

	/** True if this tier records GPU timestamps ({@code glQueryCounter}, {@code ARB_timer_query}). */
	default boolean timestamps() {
		return false;
	}

	/** A new query object for timestamps (only if {@link #timestamps()}). */
	default int createTimestampQuery() {
		throw new UnsupportedOperationException("timestamp queries");
	}

	/** Stores the GPU time at which all previously issued commands have completed into {@code query}. */
	default void writeTimestamp(int query) {
		throw new UnsupportedOperationException("timestamp queries");
	}

	/** The GPU nanoseconds a written timestamp query holds, or -1 while it isn't available yet. Never blocks. */
	default long timestampResult(int query) {
		return -1L;
	}

	/** The GPU's current time on the timestamp clock, or the CPU's ({@link System#nanoTime}) without timestamps. */
	default long timestampNow() {
		return System.nanoTime();
	}
}
