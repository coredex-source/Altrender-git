/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.caps;

import java.util.function.Predicate;

/**
 * Optional features used inside a tier where the driver has them. A driver rule or the dev switch
 * {@code -Daltrender.debug.<key>.disable} turns one off; neither ever changes the tier.
 */
public enum Feature {
	/** Terrain drawn with one indirect multi-draw per pass (4.3); off: a multi-draw per region, as gl33. */
	MULTI_DRAW_INDIRECT("multiDrawIndirect", true, c -> c.multiDrawIndirect && c.baseInstance),
	/** Layer 0's draw commands written by a compute shader, drawn with an indirect count (4.3). */
	INDIRECT_COUNT("indirectCount", true, c -> c.indirectCount && c.compute && c.ssbo),
	SHADER_DRAW_PARAMETERS("shaderDrawParameters", true, c -> c.shaderDrawParameters),
	ANISOTROPIC("anisotropic", false, c -> c.anisotropic),
	POLYGON_OFFSET_CLAMP("polygonOffsetClamp", false, c -> c.polygonOffsetClamp),
	/** Programs compile on driver threads (4.5). */
	PARALLEL_SHADER_COMPILE("parallelShaderCompile", false, c -> c.parallelShaderCompile);

	public final String key;
	/** Used only by the gl45 tier. */
	public final boolean gl45Only;
	private final Predicate<GlCaps> present;

	Feature(String key, boolean gl45Only, Predicate<GlCaps> present) {
		this.key = key;
		this.gl45Only = gl45Only;
		this.present = present;
	}

	public boolean present(GlCaps caps) {
		return present.test(caps);
	}
}
