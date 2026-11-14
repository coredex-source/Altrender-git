/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform;

import com.coredex.altrender.api.gpu.Gpu;

/**
 * Where the uniform classes write: the API's {@link Gpu} of the pipeline in use (development.md 6.5), set by the
 * pipeline before it pushes uniforms. Render thread only.
 */
public final class UniformGl {

	private static Gpu gpu;
	private static float lastFrameTime;

	private UniformGl() {
	}

	public static Gpu gpu() {
		return gpu;
	}

	public static void gpu(Gpu g) {
		gpu = g;
	}

	/** The last frame's duration in seconds ({@code smooth()} in custom uniforms uses it). */
	public static float lastFrameTime() {
		return lastFrameTime;
	}

	public static void lastFrameTime(float seconds) {
		lastFrameTime = seconds;
	}
}
