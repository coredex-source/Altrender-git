/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

/** A framebuffer made through {@link Gpu} (since 0.3.0). Render thread only. */
public interface GpuFramebuffer {

	/** Attaches {@code texture}'s {@code level} as colour attachment {@code index} ({@code null} detaches). */
	void color(int index, GpuTexture texture, int level);

	/** Attaches a depth (or depth-stencil) texture ({@code null} detaches). */
	void depth(GpuTexture texture);

	/** Which colour attachments the fragment outputs 0, 1, … write ({@code glDrawBuffers}); none for depth only. */
	void drawBuffers(int... attachments);

	/** The GL name (for Altrender's own draws into it, and debug tools). */
	int name();

	/** {@code null} when complete, else the GL status as text. */
	String check();

	void delete();
}
