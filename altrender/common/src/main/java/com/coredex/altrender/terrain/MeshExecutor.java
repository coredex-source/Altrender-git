/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.coredex.altrender.Log;

/**
 * The meshing worker pool (development.md 2.2): {@code -Daltrender.meshThreads} workers (default: cores − 2, at least
 * 1). Every worker is a {@link MeshThread} whose state the mc node creates on the main thread, when the pool starts.
 */
public final class MeshExecutor {

	private final ThreadPoolExecutor pool;
	private final int threads;

	/** @param stateFactory makes one worker's state; called on the calling (main) thread, once per worker */
	public MeshExecutor(int threads, Supplier<Object> stateFactory) {
		this.threads = threads;
		AtomicInteger n = new AtomicInteger();
		pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
			MeshThread t = new MeshThread(task, "Altrender mesh " + n.incrementAndGet());
			t.state = stateFactory.get();
			return t;
		});
		pool.prestartAllCoreThreads();
		Log.info("meshing: " + threads + " worker" + (threads == 1 ? "" : "s"));
	}

	/** -Daltrender.meshThreads, else cores − 2 (at least 1). */
	/** {@code setting} threads (0: cores - 2), unless {@code -Daltrender.meshThreads} overrides it; at least 1. */
	public static int configuredThreads(int setting) {
		int cores = Runtime.getRuntime().availableProcessors();
		return Math.max(1, Integer.getInteger("altrender.meshThreads", setting > 0 ? setting : cores - 2));
	}

	/** The automatic count (cores - 2, at least 1). */
	public static int automaticThreads() {
		return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
	}

	public int threads() {
		return threads;
	}

	public <T> CompletableFuture<T> submit(Supplier<T> task) {
		return CompletableFuture.supplyAsync(task, pool);
	}

	/** Tasks waiting for a worker. */
	public int queued() {
		return pool.getQueue().size();
	}

	public void shutdown() {
		pool.shutdownNow();
	}
}
