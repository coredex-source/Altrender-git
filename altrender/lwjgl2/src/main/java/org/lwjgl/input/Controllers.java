/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.input;

/**
 * LWJGL2's {@code Controllers} (Altrender's LWJGL2 layer): no controllers yet. Old versions only call {@code create()}
 * and ignore failures; gamepad support through SDL3 can come later.
 */
public final class Controllers {

	private static boolean created;

	private Controllers() {
	}

	public static void create() {
		created = true;
	}

	public static void destroy() {
		created = false;
	}

	public static boolean isCreated() {
		return created;
	}

	public static int getControllerCount() {
		return 0;
	}

	public static void poll() {
	}

	public static boolean next() {
		return false;
	}

	public static void clearEvents() {
	}
}
