/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** Per-pipeline stencil test (NeoForge only; vanilla pipelines have none). */
public record StencilState(Face front, Face back, int readMask, int writeMask, int reference) {
	public record Face(StencilOp fail, StencilOp depthFail, StencilOp pass, Compare compare) {}
}
