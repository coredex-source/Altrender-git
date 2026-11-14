/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** Programmable stages. */
public enum ShaderStage {
	VERTEX, FRAGMENT,
	/** GL 4.3 (gl45 tier). */
	COMPUTE,
	/** GL 3.2. */
	GEOMETRY,
	/** GL 4.0 (gl45 tier). */
	TESS_CONTROL,
	TESS_EVALUATION
}
