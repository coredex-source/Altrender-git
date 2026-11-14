/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

/**
 * The game's state as a shader pack sees it (since 0.3.0, development.md 6.1/6.5), read from the running Minecraft
 * version by Altrender's version node. Render thread; values are the current frame's (interpolated by the frame's
 * partial tick where the game interpolates them). Methods have neutral defaults, so a version without a value (and
 * implementations written before it was added) still work.
 */
public interface GameState {

	/**
	 * The world's dimension as a namespaced id ({@code minecraft:overworld}, {@code minecraft:the_nether}); versions
	 * without a modern id use their closest one. {@code null} without a world.
	 */
	String dimension();

	/** The frame's partial tick (0..1). */
	default float partialTick() {
		return 0;
	}

	/** The world's total time in ticks. */
	default long worldTime() {
		return 0;
	}

	/** The sky's angle as the game draws it (0..1; 0 at noon, 0.5 at midnight). */
	default float skyAngle() {
		return 0;
	}

	/** The moon's phase, 0 (full) to 7; versions without phases report 0. */
	default int moonPhase() {
		return 0;
	}

	default float rainStrength() {
		return 0;
	}

	default float thunderStrength() {
		return 0;
	}

	/** The sky colour the game uses this frame (RGB, 0..1). */
	default float[] skyColor() {
		return new float[] {0, 0, 0};
	}

	/** The camera's world position (the eye), into {@code out[0..2]}. */
	default void cameraPosition(double[] out) {
		out[0] = out[1] = out[2] = 0;
	}

	/** What the camera's eye is in: 0 air, 1 water, 2 lava. */
	default int eyeFluid() {
		return 0;
	}

	/** Block and sky light (0..15) at the camera's eye. */
	default int eyeBlockLight() {
		return 0;
	}

	default int eyeSkyLight() {
		return 15;
	}

	/**
	 * The biome at the camera, by the version's own name in lower case with underscores (b1.7.3:
	 * {@code seasonal_forest}, {@code ice_desert}); {@code null} without a world.
	 */
	default String biome() {
		return null;
	}

	/** The biome's temperature and rainfall (0..1, as the version's biome source has them). */
	default float temperature() {
		return 0.5f;
	}

	default float rainfall() {
		return 0.5f;
	}

	/** What falls in the biome: 0 nothing, 1 rain, 2 snow. */
	default int precipitation() {
		return 1;
	}

	/** The held item's id in the version's own numbering, -1 for none. */
	default int heldItem() {
		return -1;
	}

	/** The held item's damage (its data value: wool colour, dye, tool wear). */
	default int heldItemDamage() {
		return 0;
	}

	/** The light the held item gives if placed (a block's light emission, 0–15). */
	default int heldItemLight() {
		return 0;
	}

	default float health() {
		return 20;
	}

	default float maxHealth() {
		return 20;
	}

	/** Air while under water, and its maximum (ticks). */
	default int air() {
		return 300;
	}

	default int maxAir() {
		return 300;
	}

	default boolean sneaking() {
		return false;
	}

	default boolean burning() {
		return false;
	}

	default boolean hurt() {
		return false;
	}

	default boolean onGround() {
		return true;
	}

	/** The HUD hidden (F1). */
	default boolean hideGui() {
		return false;
	}

	/**
	 * The world's light curve: brightness (0..1) for each light level 0..15, as the game maps light to colour
	 * (b1.7.3: its dimension's brightness table).
	 */
	default float[] brightnessTable() {
		float[] t = new float[16];
		for (int i = 0; i < 16; i++) {
			t[i] = i / 15f;
		}
		return t;
	}

	/** How many levels the sky light is darkened by now (night, rain), 0..15. */
	default int skyDarkness() {
		return 0;
	}

	/** The terrain render distance in chunks. */
	default int renderDistance() {
		return 8;
	}
}
