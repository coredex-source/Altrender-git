/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug.harness;

/**
 * The dev-only scene harness switch (development.md 0.7): active only with {@code -Daltrender.scene=<scene.json>}.
 * Version-specific drivers (each mc node's {@code harness} package) check this before changing anything.
 */
public final class Harness {

	private static final boolean ACTIVE = System.getProperty("altrender.scene") != null;
	private static volatile boolean frozen;

	private Harness() {
	}

	public static boolean active() {
		return ACTIVE;
	}

	/** Ticks stopped and frame interpolation pinned (set once the scene is built). */
	public static boolean frozen() {
		return frozen;
	}

	public static void freeze(boolean on) {
		frozen = on;
	}
}
