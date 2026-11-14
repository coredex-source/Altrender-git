/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.opengl;

import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.lwjgl2.merge.Replace;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.JNI;

/** Donor for LWJGL 3's {@code ARBMultitexture}: the active texture unit for {@link GlStateTracker} (1.4). */
public final class ARBMultitexture {

	private ARBMultitexture() {
	}

	@Replace
	public static void glActiveTextureARB(int texture) {
		GlStateTracker.activeTexture(texture);
		JNI.callV(texture, GL.getCapabilities().glActiveTextureARB);
	}
}
