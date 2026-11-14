/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** The driver reported GL_OUT_OF_MEMORY while a strategy allocated storage. */
public final class OutOfGpuMemoryException extends RuntimeException {
	public OutOfGpuMemoryException(String message) {
		super(message);
	}
}
