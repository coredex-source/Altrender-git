/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * A pipeline's say in how Altrender draws terrain (since 0.3.0, development.md 6.6): its programs for the terrain
 * passes (built for {@link TerrainFormat}) and its block ids. While a pipeline offers this, Altrender meshes in
 * {@link TerrainFormat}'s extended format (the light out of the vertex colour, the extra attributes filled); offering
 * it or a different {@link #revision()} makes the terrain mesh again.
 */
public interface TerrainPipeline {

	enum Pass {
		/**
		 * The opaque layer: all of it, or (when the pipeline gives a {@link #CUTOUT} program) its quads whose texture
		 * has no see-through texels, drawn without an alpha test. For a {@code TerrainView}: those quads.
		 */
		SOLID,
		/**
		 * The opaque layer's quads whose texture has see-through texels (leaves, plants, glass, a grass side's
		 * overlay), drawn after {@link #SOLID}'s with this program; 0: {@link #SOLID}'s program draws all of the layer.
		 * For a {@code TerrainView}: those quads. Since 0.3.0.
		 */
		CUTOUT,
		TRANSLUCENT,
		/**
		 * The translucent terrain drawn for its depth only (colour writes off; old versions' fancy water draws it so
		 * before its colour): a cheaper program with the same positions, or 0 to use {@link #TRANSLUCENT}'s. Since
		 * 0.3.0.
		 */
		TRANSLUCENT_DEPTH
	}

	/** The program for {@code pass} (0: Altrender's own, or for TRANSLUCENT_DEPTH: TRANSLUCENT's). */
	int program(Pass pass);

	/**
	 * The pipeline's id for a block ({@code mc_Entity.x}), by the version's own block key
	 * ({@code id << 4 | metadata} on versions with metadata); -1 for none. Called from meshing threads.
	 */
	int blockId(int blockKey);

	/** Changes when {@link #blockId} would answer differently (another pack): the terrain meshes again. */
	int revision();

	/**
	 * Whether the game's per-face shading (a block's sides and bottom drawn darker) stays in the vertex colour (since
	 * 0.3.0; a shader pack's {@code oldLighting}); false: every face as bright as its texture and tint.
	 */
	default boolean faceShading() {
		return true;
	}

	/**
	 * Where ambient occlusion goes (since 0.3.0; a shader pack's {@code separateAo}): the vertex colour's alpha (true)
	 * or its colour (false). With the game's smooth lighting off there is none.
	 */
	default boolean separateAo() {
		return false;
	}

	/**
	 * Whether the game's depth-only draw of the translucent terrain (old versions' fancy water: depth first, so only
	 * the nearest surface takes colour) is drawn (since 0.3.0). False: it's skipped and the colour draw sorts and
	 * draws as usual (every surface blends, back to front, as on modern versions).
	 */
	default boolean translucentDepthDraw() {
		return true;
	}
}
