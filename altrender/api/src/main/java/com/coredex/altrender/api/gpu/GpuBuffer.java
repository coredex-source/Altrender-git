/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

import java.nio.ByteBuffer;

/** A buffer made through {@link Gpu} (since 0.3.0): uniform or shader storage data. Render thread only. */
public interface GpuBuffer {

	long size();

	/** Writes {@code data} (position to limit) at {@code offset}. */
	void upload(long offset, ByteBuffer data);

	/** Fills the whole buffer with zeros. */
	void clear();

	void delete();
}
