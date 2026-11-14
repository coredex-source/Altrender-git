/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/blending/BlendModeOverride.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.gl;

public class BlendModeOverride {
	public static final BlendModeOverride OFF = new BlendModeOverride(null);

	private final BlendMode blendMode;

	public BlendModeOverride(BlendMode blendMode) {
		this.blendMode = blendMode;
	}


	public BlendMode getBlendMode() {
		return blendMode;
	}
}
