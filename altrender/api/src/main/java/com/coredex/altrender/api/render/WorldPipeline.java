/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

/**
 * A replacement for how the world is drawn (since 0.3.0, development.md 6.1): a shader pack's pipeline. Altrender
 * calls it at each step of the world render, on the render thread; between the calls the game draws its own geometry
 * (sky, entities, particles, weather, the hand) with the program and into the target the pipeline set through
 * {@link com.coredex.altrender.api.gpu.Gpu#gameDraws}, and Altrender draws its terrain. One pipeline at a time
 * ({@link com.coredex.altrender.api.AltrenderApi#setWorldPipeline}); every method defaults to doing nothing.
 */
public interface WorldPipeline {

	/**
	 * Start of a world render, before the game clears the screen: bind the targets the game's draws (and its clear)
	 * go to.
	 *
	 * @param width  the game's framebuffer size
	 */
	default void beginWorld(int width, int height, float partialTick) {
	}

	/** The camera's matrices are set (the tracker's modelview and projection are the frame's camera). */
	default void cameraReady() {
	}

	/**
	 * Its terrain programs and block ids, or {@code null} for Altrender's own terrain draw (into the pipeline's
	 * targets). Asked at each world render.
	 */
	default TerrainPipeline terrain() {
		return null;
	}

	/**
	 * How the hand meets the world's depth (Iris's {@code MC_HAND_DEPTH}): 0 keeps the game's own depth clear before
	 * the hand; otherwise the hand draws into the world's depth buffer, its depth squeezed by this factor around the
	 * middle of the range (0.125: depths 0.4375–0.5625, which packs test for), so later passes see the world's depth
	 * too.
	 */
	default float handDepth() {
		return 0;
	}

	/** Whether the game's own {@code part} is left out of the world render while this pipeline draws it. */
	default boolean hides(VanillaPart part) {
		return false;
	}

	/** The world render now draws {@code phase} ({@link RenderPhase#NONE} after one ends). */
	default void phase(RenderPhase phase) {
	}

	/**
	 * The game changed fixed-function state its draws depend on, between {@link #beginWorld} and
	 * {@link #endWorld} (the new values are in {@code AltrenderApi.fixedFunction()}). Called from inside the game's
	 * GL call, before it reaches the driver: switch programs or set uniforms here, draw nothing.
	 */
	default void fixedFunction(FixedFunction.Change change) {
	}

	/**
	 * The game draws an entity next (since 0.3.0): its modern type ({@code minecraft:zombie}) and the light at it
	 * (block, sky; 0–15). Returning true takes the light: the entity's draws are white (the game's own brightness
	 * isn't in their colour) and the pipeline gives the light, e.g. with {@code Gpu.gameLightCoords}; false leaves
	 * the game's brightness in the colour.
	 */
	default boolean entity(String type, int blockLight, int skyLight) {
		return false;
	}

	/**
	 * The entity just announced with {@link #entity} flashes (since 0.3.0): its hurt and explosion overlays as one
	 * colour over its texture (Iris's {@code entityColor}: rgb, alpha how much; nothing is (0, 0, 0, 0)). Returning
	 * true takes it: the game skips its own overlay pass for the entity.
	 */
	default boolean entityColor(float r, float g, float b, float a) {
		return false;
	}

	/**
	 * The entity's glowing eyes (a spider's) draw next ({@code true}) or are done ({@code false}), since 0.3.0: a
	 * pipeline may give them their own program (Iris's {@code gbuffers_spidereyes}).
	 */
	default void entityEyes(boolean on) {
	}

	/**
	 * Whether the game's particles draw after the translucent terrain (since 0.3.0; Iris's {@code particles.ordering}
	 * {@code after}, so the deferred passes don't light them) instead of where the game draws them (old versions:
	 * before it). Asked once per frame.
	 */
	default boolean particlesLate() {
		return false;
	}

	/**
	 * Whether the solid hand draws before the translucent terrain (since 0.3.0; Iris: before the deferred passes, so
	 * they light it) instead of at the end of the world. The phase is {@code HAND_SOLID} either way.
	 */
	default boolean handFirst() {
		return false;
	}

	/** The game draws a block entity next (a sign, a mob spawner): its block key and light, as {@link #entity}. */
	default boolean blockEntity(int blockKey, int blockLight, int skyLight) {
		return false;
	}

	/**
	 * End of the world render (after the hand), before the HUD: the pipeline's last passes, ending with the picture
	 * on the screen and {@code gameDraws(null, 0)} so the HUD and menus draw as usual.
	 */
	default void endWorld() {
	}

	/** The game's framebuffer size changed. */
	default void resized(int width, int height) {
	}

	/**
	 * Free everything made through the API: the pipeline was replaced, or the renderer is switching tier or
	 * stopping (the {@link com.coredex.altrender.api.gpu.Gpu} it used is still valid during this call). After a tier
	 * switch the pipeline stays installed: make the objects again from {@code AltrenderApi.gpu()} on the next
	 * {@link #beginWorld}.
	 */
	default void destroy() {
	}
}
