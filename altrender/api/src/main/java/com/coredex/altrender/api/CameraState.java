/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

/**
 * The world camera of the current and the previous frame (since 0.3.0, development.md 6.1/6.5): the game's own
 * matrices as its fixed-function state had them when the camera was set ({@code WorldPipeline.cameraReady}).
 * Column-major 4×4 arrays, fresh copies on each call. Render thread.
 */
public interface CameraState {

	float[] modelView();

	float[] projection();

	float[] previousModelView();

	float[] previousProjection();

	/** Frames counted since start (each world render that set a camera). */
	long frame();
}
