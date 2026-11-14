/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * A meshing worker (development.md 2.2). Game code that keeps per-call state on shared objects (the tessellator, block
 * shapes) checks {@code Thread.currentThread() instanceof MeshThread} and uses the worker's own copy, held in
 * {@link #state} (the mc node's type), instead.
 */
public final class MeshThread extends Thread {

	/** The mc node's per-worker state, set before the thread starts. */
	public Object state;

	MeshThread(Runnable task, String name) {
		super(task, name);
		setDaemon(true);
		setPriority(Thread.NORM_PRIORITY - 1);
	}

	/** The current thread's worker state, or {@code null} off the workers. */
	public static Object currentState() {
		return Thread.currentThread() instanceof MeshThread m ? m.state : null;
	}
}
