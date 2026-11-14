/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/materialmap/BlockRenderType.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.materialmap;

import java.util.Optional;

/**
 * Defines the possible render types for blocks.
 *
 * @see <a href="https://github.com/sp614x/optifine/blob/9ea6d9b7f57f20babae64f3807b00a6ca6e1d44a/OptiFineDoc/doc/shaders.txt#L662-L681">
 * the corresponding OptiFine documentation.</a>
 */
public enum BlockRenderType {
	/**
	 * Alpha test disabled, blending disabled, mipmaps enabled.
	 */
	SOLID,
	/**
	 * Alpha test enabled, blending disabled, mipmaps disabled.
	 * Used for tall grass, normal glass, etc.
	 */
	CUTOUT,
	/**
	 * Alpha test enabled, blending disabled, mipmaps enabled.
	 * Used for leaves.
	 */
	CUTOUT_MIPPED,
	/**
	 * Alpha test enabled (w/ low cutoff), blending enabled, mipmaps enabled.
	 * Used for stained glass, nether portals, and water.
	 */
	TRANSLUCENT;

	public static Optional<BlockRenderType> fromString(String name) {
		return switch (name) {
			case "solid" -> Optional.of(SOLID);
			case "cutout" -> Optional.of(CUTOUT);
			case "cutout_mipped" -> Optional.of(CUTOUT_MIPPED);
			case "translucent" -> Optional.of(TRANSLUCENT);
			default -> Optional.empty();
		};
	}
}
