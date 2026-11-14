/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * What the world render is drawing (since 0.3.0, development.md 6.1). The names follow the stages shader packs know
 * (Iris's render stages); a version reports the ones it has, in its own order. {@link #NONE} between them.
 */
public enum RenderPhase {
	NONE,
	/** The sky: planes, sunrise, stars (untextured draws). */
	SKY,
	/** Sun and moon (textured sky draws). */
	SKY_TEXTURED,
	TERRAIN_SOLID,
	TERRAIN_CUTOUT,
	TERRAIN_TRANSLUCENT,
	ENTITIES,
	BLOCK_ENTITIES,
	/** Block breaking cracks. */
	DESTROY,
	/** The targeted block's outline. */
	OUTLINE,
	PARTICLES,
	RAIN_SNOW,
	CLOUDS,
	HAND_SOLID,
	HAND_TRANSLUCENT
}
