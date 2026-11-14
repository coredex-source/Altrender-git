/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * The view frustum for section culling (development.md 2.5): six planes from projection × modelview (Gribb-Hartmann),
 * tested against boxes in camera-relative coordinates, the space the terrain is drawn in. Conservative: a box is
 * culled only when it lies entirely behind one plane by more than {@link #MARGIN} blocks.
 */
public final class Frustum {

	/** Blocks of slack: geometry may stick out of its section a little (fluids, the 1.000001 crack scale). */
	public static final float MARGIN = 1.0f;

	private final float[] planes = new float[24];

	/** Builds the planes from column-major {@code projection} and {@code modelview}. */
	public void set(float[] projection, float[] modelview) {
		float[] m = new float[16];
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				m[c * 4 + r] = projection[r] * modelview[c * 4] + projection[4 + r] * modelview[c * 4 + 1]
						+ projection[8 + r] * modelview[c * 4 + 2] + projection[12 + r] * modelview[c * 4 + 3];
			}
		}
		// row i of m: (m[i], m[4 + i], m[8 + i], m[12 + i]); planes = row3 ± row0/1/2
		for (int p = 0; p < 6; p++) {
			int row = p / 2;
			float sign = (p & 1) == 0 ? 1f : -1f;
			float a = m[3] + sign * m[row];
			float b = m[7] + sign * m[4 + row];
			float c = m[11] + sign * m[8 + row];
			float d = m[15] + sign * m[12 + row];
			float len = (float) Math.sqrt(a * a + b * b + c * c);
			planes[p * 4] = a / len;
			planes[p * 4 + 1] = b / len;
			planes[p * 4 + 2] = c / len;
			planes[p * 4 + 3] = d / len;
		}
	}

	/**
	 * The planes, inward: (a, b, c, d) × 6 in the order left, right, bottom, top, near, far (opposite planes are
	 * {@code p} and {@code p ^ 1}). Not a copy.
	 */
	float[] planes() {
		return planes;
	}

	/** Whether the box [x0, x1] × [y0, y1] × [z0, z1] (camera-relative) may be visible. */
	public boolean intersects(float x0, float y0, float z0, float x1, float y1, float z1) {
		for (int p = 0; p < 6; p++) {
			float a = planes[p * 4];
			float b = planes[p * 4 + 1];
			float c = planes[p * 4 + 2];
			float d = planes[p * 4 + 3];
			// the box corner furthest along the plane normal
			float x = a >= 0 ? x1 : x0;
			float y = b >= 0 ? y1 : y0;
			float z = c >= 0 ? z1 : z0;
			if (a * x + b * y + c * z + d < -MARGIN) {
				return false;
			}
		}
		return true;
	}
}
