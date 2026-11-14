/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.openal;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Donor: LWJGL2-only {@code AL10} signatures (see the GL11 donor). */
public final class AL10 {

	private AL10() {
	}

	public static void alListener(int pname, FloatBuffer value) {
		org.lwjgl.openal.AL10.alListenerfv(pname, value);
	}

	public static void alSource(int source, int pname, FloatBuffer value) {
		org.lwjgl.openal.AL10.alSourcefv(source, pname, value);
	}

	public static void alSourceStop(IntBuffer sources) {
		org.lwjgl.openal.AL10.alSourceStopv(sources);
	}
}
