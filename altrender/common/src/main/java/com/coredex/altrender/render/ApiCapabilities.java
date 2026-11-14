/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.util.Set;

import com.coredex.altrender.api.Capabilities;
import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.caps.Tier;

/** The API's {@link Capabilities} from the probed caps and the chosen tier (development.md 6.1). */
record ApiCapabilities(String tier, String glVersion, String glslVersion, String vendor, String renderer,
		Set<String> extensions, boolean compute, boolean storageBuffers, boolean imageLoadStore,
		boolean drawBuffersBlend, boolean tessellation, boolean indirectCount, int maxDrawBuffers, int maxTextureUnits,
		int maxTextureSize) implements Capabilities {

	static ApiCapabilities of(GlCaps c, Tier tier) {
		boolean full = tier == Tier.GL45;
		Set<String> ext = Set.copyOf(c.extensions);
		return new ApiCapabilities(full ? "gl45" : "gl33", c.version, c.glsl, c.vendor, c.renderer, ext,
				full && c.compute, full && c.ssbo, full && c.imageLoadStore,
				c.atLeast(4, 0) || ext.contains("GL_ARB_draw_buffers_blend"),
				full && (c.atLeast(4, 0) || ext.contains("GL_ARB_tessellation_shader")),
				full && c.indirectCount, c.maxDrawBuffers, c.maxTextureUnits, c.maxTextureSize);
	}
}
