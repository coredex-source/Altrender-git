/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

import com.coredex.altrender.api.gpu.GpuFramebuffer;

/**
 * Altrender's terrain drawn from another camera (since 0.3.0, development.md 6.1/6.7: a shader pack's shadow map),
 * with that camera's own culling. Render thread, inside a world render after the camera is set, outside
 * {@code Gpu.begin}/{@code end}.
 */
public interface TerrainView {

	/**
	 * Draws the sections within {@code maxDistance} blocks of the player's camera that {@code projection ·
	 * modelView} keeps into {@code target}'s viewport, with {@code program} (built for {@link TerrainFormat}), the depth
	 * test on, blending and face culling off and the alpha test at 0.1. The matrices become the fixed-function ones
	 * for the draw; vertex positions are relative to the player's camera at {@code camX, camY, camZ}.
	 *
	 * @return sections drawn
	 */
	default int draw(GpuFramebuffer target, int x, int y, int width, int height, float[] modelView,
			float[] projection, double camX, double camY, double camZ, float maxDistance, TerrainPipeline.Pass pass,
			int program) {
		return draw(target, x, y, width, height, modelView, projection, camX, camY, camZ, maxDistance, pass, program,
				null, null, ViewCulling.NONE);
	}

	/**
	 * As above with blending (a shader pack's {@code blend.shadow*}): {@code blend} for every buffer ({@code null} or
	 * {@code Blend.NONE}: off), then {@code buffers} per attachment of {@code target} (null or empty: none; a null
	 * entry: as the rest; {@code Blend.NONE}: off). Functions per buffer need {@code Capabilities.drawBuffersBlend};
	 * on/off works everywhere. {@code culling}: what else it leaves out (a shader pack's shadow culling); the player's
	 * view for it is the camera's as the game set it this frame.
	 */
	int draw(GpuFramebuffer target, int x, int y, int width, int height, float[] modelView, float[] projection,
			double camX, double camY, double camZ, float maxDistance, TerrainPipeline.Pass pass, int program,
			com.coredex.altrender.api.gpu.Blend blend, com.coredex.altrender.api.gpu.Blend[] buffers,
			ViewCulling culling);
}
