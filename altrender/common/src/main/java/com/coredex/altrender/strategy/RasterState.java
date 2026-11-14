/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;


/**
 * Fixed-function state of one pipeline, in our own terms. Built once per compiled pipeline and applied through
 * {@link GlState#apply}.
 *
 * @param colorMask   bits R=1, G=2, B=4, A=8
 * @param depthTest   false = no depth test and no depth writes
 * @param offsetFactor polygon offset; both 0 = disabled
 * @param colorTargets colour attachments the pipeline writes, all with the same blending and mask
 */
public record RasterState(boolean blend, BlendFactor srcColor, BlendFactor dstColor, BlendFactor srcAlpha,
						  BlendFactor dstAlpha, int colorMask, boolean depthTest, Compare depthFunc, boolean depthWrite,
						  float offsetFactor, float offsetUnits, boolean cull, boolean wireframe,
						  StencilState stencil, BlendEquation colorEquation, BlendEquation alphaEquation,
						  int colorTargets) {
	/** Additive blend equations, one colour target. */
	public RasterState(boolean blend, BlendFactor srcColor, BlendFactor dstColor, BlendFactor srcAlpha,
					   BlendFactor dstAlpha, int colorMask, boolean depthTest, Compare depthFunc, boolean depthWrite,
					   float offsetFactor, float offsetUnits, boolean cull, boolean wireframe,
					   StencilState stencil) {
		this(blend, srcColor, dstColor, srcAlpha, dstAlpha, colorMask, depthTest, depthFunc, depthWrite, offsetFactor,
				offsetUnits, cull, wireframe, stencil, BlendEquation.ADD, BlendEquation.ADD, 1);
	}
}
