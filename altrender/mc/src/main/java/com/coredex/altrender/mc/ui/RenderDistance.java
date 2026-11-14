/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import com.coredex.altrender.render.Renderer;
import net.minecraft.client.options.GameOptions;

/**
 * The render distance in chunks (development.md 5.2, owner): the game's four steps are terrain radii of 13 (Far), 8
 * (Normal), 4 (Short) and 2 (Tiny) chunks; Altrender's setting can be any of 2..32. The game's own step follows the
 * nearest one below (its sky and clear colour depend on it), and at the four steps fog and terrain are the game's.
 */
public final class RenderDistance {

	public static final int MIN = 2;
	public static final int MAX = 32;
	private static final String[] NAMES = {"options.renderDistance.far", "options.renderDistance.normal",
			"options.renderDistance.short", "options.renderDistance.tiny"};

	private RenderDistance() {
	}

	/** The game's own radius for its step {@code viewDistance}: half its section grid, min(400, 64 << (3 - vd)) / 16 + 1. */
	public static int presetRadius(int viewDistance) {
		return (Math.min(400, 64 << (3 - viewDistance)) / 16 + 1) / 2;
	}

	/** The radius in effect: Altrender's setting, or the game's step. */
	public static int chunks(GameOptions o) {
		int c = Renderer.config().renderDistance();
		return c > 0 ? c : presetRadius(o.viewDistance);
	}

	/** The game's step for a radius: the largest whose radius is at most {@code chunks}. */
	public static int stepFor(int chunks) {
		return chunks >= 13 ? 0 : chunks >= 8 ? 1 : chunks >= 4 ? 2 : 3;
	}

	/** The step's name key if {@code chunks} is exactly one of the game's steps, else null. */
	public static String stepName(int chunks) {
		int step = stepFor(chunks);
		return presetRadius(step) == chunks ? NAMES[step] : null;
	}

	/**
	 * Fog end and far plane in blocks: the game's (256 >> step) at its own steps; 16 per chunk otherwise, at least the
	 * game's Far fog from 13 chunks up (so it never drops going past Far).
	 */
	public static float fogBlocks(GameOptions o) {
		int c = Renderer.config().renderDistance();
		if (c <= 0) {
			return 256 >> o.viewDistance;
		}
		return c >= 13 ? Math.max(256, 16 * c) : 16 * c;
	}
}
