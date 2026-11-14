/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import com.coredex.altrender.api.CameraState;
import com.coredex.altrender.gl.GlStateTracker;

/** The API's {@link CameraState}: the tracker's matrices taken at each camera-ready hook (development.md 6.1). */
final class ApiCamera implements CameraState {

	private static final int GL_MODELVIEW = 0x1700;
	private static final int GL_PROJECTION = 0x1701;

	private float[] modelView = identity();
	private float[] projection = identity();
	private float[] previousModelView = identity();
	private float[] previousProjection = identity();
	private long frame;

	private static float[] identity() {
		float[] m = new float[16];
		m[0] = m[5] = m[10] = m[15] = 1;
		return m;
	}

	/** Takes the camera from the tracker (render thread, at camera ready). */
	void capture() {
		float[] mv = previousModelView;
		float[] p = previousProjection;
		previousModelView = modelView;
		previousProjection = projection;
		GlStateTracker.matrix(GL_MODELVIEW, mv);
		GlStateTracker.matrix(GL_PROJECTION, p);
		modelView = mv;
		projection = p;
		frame++;
	}

	@Override
	public float[] modelView() {
		return modelView.clone();
	}

	@Override
	public float[] projection() {
		return projection.clone();
	}

	@Override
	public float[] previousModelView() {
		return previousModelView.clone();
	}

	@Override
	public float[] previousProjection() {
		return previousProjection.clone();
	}

	@Override
	public long frame() {
		return frame;
	}
}
