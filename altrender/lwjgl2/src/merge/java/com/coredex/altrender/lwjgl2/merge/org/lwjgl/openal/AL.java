/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.openal;

/**
 * Donor: LWJGL2's {@code AL.create()}/{@code isCreated()} (see the GL11 donor). LWJGL 3's own {@code AL.destroy()}
 * stays as it is (it unloads AL); the device and context are closed by {@code OpenAl} at shutdown.
 */
public final class AL {

	private AL() {
	}

	public static void create() throws org.lwjgl.LWJGLException {
		com.coredex.altrender.lwjgl2.OpenAl.create();
	}

	public static boolean isCreated() {
		return com.coredex.altrender.lwjgl2.OpenAl.isCreated();
	}
}
