/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reused {@link SectionSnapshot}s (development.md 2.1): taken on the main thread, returned by whichever worker meshed
 * them. Each holds about 40 KB, so a pool beats allocating one per dirty section.
 */
public final class SnapshotPool {

	private final ConcurrentLinkedQueue<SectionSnapshot> free = new ConcurrentLinkedQueue<>();
	private final AtomicInteger created = new AtomicInteger();

	public SectionSnapshot acquire() {
		SectionSnapshot s = free.poll();
		if (s == null) {
			created.incrementAndGet();
			s = new SectionSnapshot();
		}
		return s;
	}

	public void release(SectionSnapshot s) {
		free.add(s);
	}

	/** Snapshots allocated so far (debug, F3). */
	public int created() {
		return created.get();
	}
}
