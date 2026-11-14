/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * The game's fixed-function state as its own GL calls set it (since 0.3.0, development.md 6.1), read live from
 * Altrender's tracker: a shader pack's programs replace texturing, fog and lighting, so they need to know what the
 * game asked for (OptiFine's {@code fogMode} and its basic/textured/lit program choice). GL enums as ints. Render
 * thread.
 */
public interface FixedFunction {

	/** What changed, for {@link WorldPipeline#fixedFunction}. */
	enum Change {
		/** {@code GL_TEXTURE_2D} on unit 0. */
		TEXTURE,
		/** {@code GL_FOG} on or off. */
		FOG,
		/** A {@code glFog} parameter. */
		FOG_PARAMETER,
		/** {@code GL_LIGHTING}. */
		LIGHTING,
		/** {@code GL_ALPHA_TEST} on or off (since 0.3.0). */
		ALPHA_TEST
	}

	boolean texture();

	boolean fog();

	/** {@code GL_LINEAR}, {@code GL_EXP} or {@code GL_EXP2}. */
	int fogMode();

	float fogStart();

	float fogEnd();

	float fogDensity();

	/** RGBA into {@code out[0..3]}. */
	void fogColor(float[] out);

	/** {@code GL_FOG_DISTANCE_MODE_NV}: {@code GL_EYE_RADIAL_NV} (0x855B) where the game asked for radial fog, else the eye-plane mode. */
	int fogDistanceMode();

	boolean lighting();

	boolean alphaTest();

	/** The alpha test's reference ({@code glAlphaFunc}; b1.7.3 always tests "greater than"). */
	default float alphaRef() {
		return 0.1f;
	}
}
