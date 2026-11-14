/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * How a buffer will be used (bit set); strategies pick driver hints and storage flags from it.
 */
public final class BufferUsage {
	public static final int MAP_READ = 1;
	public static final int MAP_WRITE = 2;
	public static final int HINT_CLIENT_STORAGE = 4;
	public static final int COPY_DST = 8;
	public static final int COPY_SRC = 16;
	public static final int VERTEX = 32;
	public static final int INDEX = 64;
	public static final int UNIFORM = 128;
	public static final int UNIFORM_TEXEL_BUFFER = 256;

	private BufferUsage() {}

	public static boolean has(int usage, int bit) {
		return (usage & bit) != 0;
	}
}
