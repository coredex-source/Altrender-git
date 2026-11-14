/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * What a {@link TerrainView} leaves out besides its own frustum (since 0.3.0): a shader pack's shadow culling, as
 * Iris's. Distances are half-sizes of boxes around the player's camera, in blocks.
 *
 * @param distance     sections beyond this box aren't drawn (0 or less: no box)
 * @param toLight      the direction to the light (world axes, normalised): only sections whose shadow can fall into
 *                     the player's view are drawn; null: no such culling
 * @param safeDistance within this box everything is drawn, whatever the rest says (0 or less: none)
 * @param backFaces    faces turned away from the view's camera aren't drawn (face culling on; the view draws both
 *                     sides otherwise)
 */
public record ViewCulling(float distance, float[] toLight, float safeDistance, boolean backFaces) {

	/** Only the view's own frustum and distance. */
	public static final ViewCulling NONE = new ViewCulling(0, null, 0);

	/** Both sides of every face drawn. */
	public ViewCulling(float distance, float[] toLight, float safeDistance) {
		this(distance, toLight, safeDistance, false);
	}

	/** The same, with {@code backFaces}. */
	public ViewCulling withBackFaces(boolean backFaces) {
		return new ViewCulling(distance, toLight, safeDistance, backFaces);
	}
}
