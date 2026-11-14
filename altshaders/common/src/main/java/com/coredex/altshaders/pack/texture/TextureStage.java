/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/texture/TextureStage.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.texture;

import java.util.Optional;

public enum TextureStage {
	/**
	 * The setup passes.
	 * <p>
	 * Exclusive to Iris 1.6.
	 */
	SETUP,
	/**
	 * The begin pass.
	 * <p>
	 * Exclusive to Iris 1.6.
	 */
	BEGIN,
	/**
	 * The shadowcomp passes.
	 * <p>
	 * While this is not documented in shaders.txt, it is a valid stage for defining custom textures.
	 */
	SHADOWCOMP,
	/**
	 * The prepare passes.
	 * <p>
	 * While this is not documented in shaders.txt, it is a valid stage for defining custom textures.
	 */
	PREPARE,
	/**
	 * All of the gbuffer passes, as well as the shadow passes.
	 */
	GBUFFERS_AND_SHADOW,
	/**
	 * The deferred pass.
	 */
	DEFERRED,
	/**
	 * The composite pass and final pass.
	 */
	COMPOSITE_AND_FINAL;

	public static Optional<TextureStage> parse(String name) {
		return switch (name) {
			case "setup" -> Optional.of(SETUP);
			case "begin" -> Optional.of(BEGIN);
			case "shadowcomp" -> Optional.of(SHADOWCOMP);
			case "prepare" -> Optional.of(PREPARE);
			case "gbuffers" -> Optional.of(GBUFFERS_AND_SHADOW);
			case "deferred" -> Optional.of(DEFERRED);
			case "composite" -> Optional.of(COMPOSITE_AND_FINAL);
			default -> Optional.empty();
		};
	}
}
