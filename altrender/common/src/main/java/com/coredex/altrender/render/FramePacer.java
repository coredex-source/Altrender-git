/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.util.concurrent.locks.LockSupport;

/**
 * Precise frame pacing without busy-waiting (development.md 3.6): parks in shrinking steps toward an absolute target,
 * so a limit holds to a fraction of a millisecond where {@code Thread.sleep(ms)} (the game's limiter) truncates to
 * whole milliseconds and drifts.
 */
public final class FramePacer {

	private FramePacer() {
	}

	/** Returns at {@code target} ({@code System.nanoTime()} clock) or just after; immediately if it has passed. */
	public static void waitUntil(long target) {
		while (true) {
			long remaining = target - System.nanoTime();
			if (remaining <= 0) {
				return;
			}
			// Long waits in one park; the last ~2 ms in halves (the scheduler wakes ~50-100 µs late).
			LockSupport.parkNanos(remaining > 2_000_000L ? remaining - 1_000_000L : Math.max(20_000L, remaining / 2));
			if (Thread.interrupted()) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
