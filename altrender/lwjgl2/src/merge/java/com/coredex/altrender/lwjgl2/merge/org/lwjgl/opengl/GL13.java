/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.opengl;

import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.lwjgl2.merge.Replace;
import org.lwjgl.opengl.GL13C;

/** Donor for LWJGL 3's {@code GL13}: the active texture unit for {@link GlStateTracker} (development.md 1.4). */
public final class GL13 {

	private GL13() {
	}

	@Replace
	public static void glActiveTexture(int texture) {
		GlStateTracker.activeTexture(texture);
		GL13C.glActiveTexture(texture);
	}
}
