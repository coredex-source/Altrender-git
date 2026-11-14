/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl;

/** LWJGL2's checked exception (Altrender's LWJGL2 layer). */
public class LWJGLException extends Exception {

	private static final long serialVersionUID = 1L;

	public LWJGLException() {
	}

	public LWJGLException(String message) {
		super(message);
	}

	public LWJGLException(String message, Throwable cause) {
		super(message, cause);
	}

	public LWJGLException(Throwable cause) {
		super(cause);
	}
}
