/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/framebuffer/ViewportData.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.gl;

public record ViewportData(float scale, float viewportX, float viewportY) {
	private static final ViewportData DEFAULT = new ViewportData(1.0f, 0.0f, 0.0f);

	public static ViewportData defaultValue() {
		return DEFAULT;
	}
}
