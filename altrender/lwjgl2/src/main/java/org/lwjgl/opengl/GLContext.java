/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.opengl;

/** LWJGL2's {@code GLContext} (Altrender's LWJGL2 layer): capability flags of the current context. */
public final class GLContext {

	private static ContextCapabilities capabilities;

	private GLContext() {
	}

	public static synchronized ContextCapabilities getCapabilities() {
		if (capabilities == null) {
			capabilities = new ContextCapabilities(GL.getCapabilities());
		}
		return capabilities;
	}

	/** Called by the layer after it creates a context. */
	public static synchronized void contextCreated() {
		capabilities = null;
	}

	public static void useContext(Object context) {
		if (context == null) {
			com.coredex.altrender.lwjgl2.SdlWindow.releaseCurrent();
		} else {
			com.coredex.altrender.lwjgl2.SdlWindow.makeCurrent();
		}
	}
}
