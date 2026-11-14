/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.platform;

/**
 * Where Altrender's passes run in the vanilla frame (development.md 0.4). Each mc node calls these from its mixins, on
 * the render thread, in this order per frame: {@code beginFrame}, then per world pass (two with anaglyph 3D)
 * {@code beginWorld}, {@code cameraReady}, {@code terrainLayer}/{@code terrainLayerEnd} per layer drawn,
 * {@code endWorld}; then
 * {@code endFrame}. Every method defaults to doing nothing.
 */
public interface FrameHooks {

	FrameHooks NONE = new FrameHooks() {
	};

	/** Start of the game's frame render (before the world, HUD and screens). */
	default void beginFrame(float partialTick) {
	}

	/** Start of the world render. */
	default void beginWorld(float partialTick) {
	}

	/** The camera's projection and modelview matrices are set and the game's frustum is built. */
	default void cameraReady() {
	}

	/** The game is about to draw terrain layer {@code layer} (0 opaque + cutout, 1 translucent). */
	default void terrainLayer(int layer) {
	}

	/** The game drew terrain layer {@code layer}. Fancy graphics draw layer 1 twice (depth, then colour). */
	default void terrainLayerEnd(int layer) {
	}

	/** End of the world render. */
	default void endWorld() {
	}

	/** End of the game's frame render (world, HUD and screens drawn; before the next swap). */
	default void endFrame() {
	}

	/** The game's framebuffer size changed. */
	default void resized(int width, int height) {
	}

	/** The game is about to destroy the context. */
	default void shutdown() {
	}
}
