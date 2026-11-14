/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** Index element type. */
public enum IndexType {
	U16(2), U32(4);

	private final int bytes;

	IndexType(int bytes) {
		this.bytes = bytes;
	}

	public int bytes() {
		return bytes;
	}
}
