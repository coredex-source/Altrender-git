/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

import com.coredex.altrender.api.gpu.GpuFramebuffer;

/**
 * The game's own entities and block entities drawn from another camera (since 0.3.0, development.md 6.1/6.7: a
 * shader pack's shadow map), as the game draws them, culled to that camera's frustum. Render thread, inside a world
 * render after the camera is set, outside {@code Gpu.begin}/{@code end}. The game changes fixed-function state while
 * it draws them; a pipeline ignores its {@link WorldPipeline#fixedFunction} calls meanwhile.
 */
public interface EntityView {

	/**
	 * Draws them into {@code target}'s viewport with {@code program} (as {@code Gpu.gameDraws}) and the matrices as
	 * the fixed-function modelview and projection; positions relative to the player's camera at
	 * {@code camX, camY, camZ}; only those within {@code maxDistance} blocks of it.
	 *
	 * {@code entities}: the world's entities and block entities; {@code player}: the player too, even in first
	 * person (a shader pack's {@code shadowPlayer}); both may be on.
	 *
	 * @return whether the version could draw them
	 */
	boolean draw(GpuFramebuffer target, int x, int y, int width, int height, float[] modelView, float[] projection,
			double camX, double camY, double camZ, float maxDistance, int program, boolean entities, boolean player);

	/** The world's entities and block entities, not the first-person player. */
	default boolean draw(GpuFramebuffer target, int x, int y, int width, int height, float[] modelView,
			float[] projection, double camX, double camY, double camZ, float maxDistance, int program) {
		return draw(target, x, y, width, height, modelView, projection, camX, camY, camZ, maxDistance, program, true,
				false);
	}
}
