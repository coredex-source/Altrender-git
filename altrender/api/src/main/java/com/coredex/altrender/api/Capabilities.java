/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

import java.util.Set;

/**
 * What the GL context and Altrender's tier offer (since 0.3.0, development.md 6.1). The feature answers are the
 * tier's: on {@code gl33} compute shaders, storage buffers, image load/store, tessellation and indirect count are
 * off even when the driver has them, since the tier exists to stay within GL 3.3. Changes when the user switches the
 * tier; read it again then (see {@link AltrenderApi#capabilities()}).
 */
public interface Capabilities {

	/** {@code gl45} or {@code gl33}. */
	String tier();

	/** {@code GL_VERSION}, {@code GL_SHADING_LANGUAGE_VERSION}, {@code GL_VENDOR}, {@code GL_RENDERER}. */
	String glVersion();

	String glslVersion();

	String vendor();

	String renderer();

	/** The context's extensions ({@code GL_ARB_...}), whatever the tier. */
	Set<String> extensions();

	/** Compute shaders (GL 4.3). */
	boolean compute();

	/** Shader storage buffers (GL 4.3). */
	boolean storageBuffers();

	/** Image load/store (GL 4.2). */
	boolean imageLoadStore();

	/** Per-buffer blend state (GL 4.0 or {@code ARB_draw_buffers_blend}, on either tier). */
	boolean drawBuffersBlend();

	/** Tessellation shaders (GL 4.0). */
	boolean tessellation();

	/** {@code glMultiDrawElementsIndirectCount} (GL 4.6 or {@code ARB_indirect_parameters}). */
	boolean indirectCount();

	int maxDrawBuffers();

	int maxTextureUnits();

	int maxTextureSize();
}
