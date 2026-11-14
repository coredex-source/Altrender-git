/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.opengl;

import java.nio.IntBuffer;

/** Donor: LWJGL2-only {@code ARBOcclusionQuery} signatures (see the GL11 donor). */
public final class ARBOcclusionQuery {

	private ARBOcclusionQuery() {
	}

	public static void glGetQueryObjectuARB(int id, int pname, IntBuffer params) {
		org.lwjgl.opengl.ARBOcclusionQuery.glGetQueryObjectuivARB(id, pname, params);
	}
}
