/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * Parts of the game's own world render a pipeline can switch off (since 0.3.0, development.md 6.8): shader packs
 * that draw their own clouds or sky set {@code clouds=off}, {@code sun=false} and the like.
 */
public enum VanillaPart {
	CLOUDS, WEATHER, SUN, MOON, STARS,
	/** The round shadow under entities (since 0.3.0; packs with a shadow map hide it, as OptiFine and Iris do). */
	ENTITY_SHADOWS
}
