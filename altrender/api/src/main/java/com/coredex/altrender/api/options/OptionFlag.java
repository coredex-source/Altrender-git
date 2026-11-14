/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

/** What applying a changed option needs; the screen does it once for all changes applied together. */
public enum OptionFlag {
	/** Rebuild the terrain (every section meshes again). */
	REQUIRES_RENDERER_RELOAD,
	/** Reload textures (the game's texture-pack reload). */
	REQUIRES_ASSET_RELOAD,
	/** Takes effect after a game restart; the option is marked. */
	REQUIRES_GAME_RESTART,
	/** Lay screens out again (GUI scale). */
	REQUIRES_LAYOUT
}
