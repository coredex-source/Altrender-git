/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * Where the game's own draws go and with which program, when a world pipeline (a shader pack) says so through the
 * API's {@code Gpu.gameDraws} (development.md 6.1). {@link GlState#restoreGameState} gives the game these back after
 * Altrender's passes instead of the screen and the fixed-function pipeline. Render thread only.
 */
public final class GameDraws {

	private static int framebuffer = -1;
	private static int program;
	/** Per texture unit: [target index, name] the game's draws should find there, or null for the game's own. */
	private static final int[][] TEXTURES = new int[32][];

	private GameDraws() {
	}

	/** @param framebuffer -1: the game's own (the screen); program 0: fixed function */
	public static void set(int framebuffer, int program) {
		GameDraws.framebuffer = framebuffer;
		GameDraws.program = program;
	}

	public static void program(int program) {
		GameDraws.program = program;
	}

	/** {@code name} on {@code unit} for the game's draws ({@code name 0}: the game's own again). */
	public static void texture(int unit, int targetIndex, int name) {
		TEXTURES[unit] = name == 0 ? null : new int[] {targetIndex, name};
	}

	/** The override on {@code unit} ([target index, name]), or null. */
	public static int[] texture(int unit) {
		return unit < TEXTURES.length ? TEXTURES[unit] : null;
	}

	/** Every override off. */
	public static void clear() {
		framebuffer = -1;
		program = 0;
		java.util.Arrays.fill(TEXTURES, null);
	}

	/** The framebuffer the game draws into, or -1 when it's the game's own. */
	public static int framebuffer() {
		return framebuffer;
	}

	public static int program() {
		return program;
	}
}
