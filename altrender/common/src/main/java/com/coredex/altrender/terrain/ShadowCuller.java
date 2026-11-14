/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * Shadow-map culling as Iris does it (development.md 7.3; the API's {@code ViewCulling}): a section is drawn into the
 * shadow map only if it lies within the distance box around the player's camera and, with {@code advanced}, only if
 * its shadow can fall into the player's view. That region is the view frustum swept towards the light: the frustum's
 * planes the sweep doesn't cross, and a plane through each edge between a kept and a crossed plane, along the light
 * (the technique Iris's advanced shadow culling uses). Within the safe box everything is drawn. Boxes are
 * camera-relative, as the terrain's; conservative by {@link Frustum#MARGIN}.
 */
final class ShadowCuller {

	/** At most the six frustum planes and the edges between them (12; the light crosses at most four). */
	private final float[] planes = new float[4 * 18];
	private int count;
	private float distance;
	private float safe;
	private boolean advanced;

	/**
	 * @param view     the player's view frustum (camera-relative)
	 * @param toLight  the direction to the light (normalised), or null for no advanced culling
	 * @param distance the box's half-size in blocks (0: none)
	 * @param safe     the box within which everything is drawn (0: none)
	 */
	void set(Frustum view, float[] toLight, float distance, float safe) {
		this.distance = distance;
		this.safe = safe;
		this.advanced = toLight != null;
		count = 0;
		if (!advanced) {
			return;
		}
		float lx = toLight[0], ly = toLight[1], lz = toLight[2];
		float[] f = view.planes();
		boolean[] kept = new boolean[6];
		for (int p = 0; p < 6; p++) {
			float dot = f[p * 4] * lx + f[p * 4 + 1] * ly + f[p * 4 + 2] * lz;
			// Sweeping towards the light stays inside a plane whose inward normal faces the light.
			if (dot >= 0) {
				System.arraycopy(f, p * 4, planes, count * 4, 4);
				count++;
				kept[p] = dot > 0;
			}
		}
		// A point inside the frustum (a few blocks down the view axis, past the near plane): inside every plane of the
		// swept region too, so it orients the edge planes.
		float t = Math.max(1f, -f[4 * 4 + 3] + 4f);
		float qx = f[16] * t, qy = f[17] * t, qz = f[18] * t;
		for (int a = 0; a < 6; a++) {
			if (!kept[a]) {
				continue;
			}
			for (int b = 0; b < 6; b++) {
				if (b == a || (b ^ 1) == a || kept[b]) {
					continue; // itself, its opposite, or another kept plane: no silhouette edge
				}
				addEdge(f, a, b, lx, ly, lz, qx, qy, qz);
			}
		}
	}

	private void addEdge(float[] f, int a, int b, float lx, float ly, float lz, float qx, float qy, float qz) {
		float ax = f[a * 4], ay = f[a * 4 + 1], az = f[a * 4 + 2], ad = f[a * 4 + 3];
		float bx = f[b * 4], by = f[b * 4 + 1], bz = f[b * 4 + 2], bd = f[b * 4 + 3];
		// The edge's direction, and a point on it: p = (-ad (nb × u) - bd (u × na)) / |u|².
		float ux = ay * bz - az * by, uy = az * bx - ax * bz, uz = ax * by - ay * bx;
		float uu = ux * ux + uy * uy + uz * uz;
		if (uu < 1e-12f) {
			return;
		}
		float c1x = by * uz - bz * uy, c1y = bz * ux - bx * uz, c1z = bx * uy - by * ux;
		float c2x = uy * az - uz * ay, c2y = uz * ax - ux * az, c2z = ux * ay - uy * ax;
		float px = (-ad * c1x - bd * c2x) / uu, py = (-ad * c1y - bd * c2y) / uu, pz = (-ad * c1z - bd * c2z) / uu;
		// The plane holds the edge and the light's direction.
		float mx = uy * lz - uz * ly, my = uz * lx - ux * lz, mz = ux * ly - uy * lx;
		float len = (float) Math.sqrt(mx * mx + my * my + mz * mz);
		if (len < 1e-6f || count * 4 + 4 > planes.length) {
			return;
		}
		mx /= len;
		my /= len;
		mz /= len;
		float md = -(mx * px + my * py + mz * pz);
		if (mx * qx + my * qy + mz * qz + md < 0) {
			mx = -mx;
			my = -my;
			mz = -mz;
			md = -md;
		}
		planes[count * 4] = mx;
		planes[count * 4 + 1] = my;
		planes[count * 4 + 2] = mz;
		planes[count * 4 + 3] = md;
		count++;
	}

	/** Whether the camera-relative box [x0, x1] × [y0, y1] × [z0, z1] is drawn. */
	boolean visible(float x0, float y0, float z0, float x1, float y1, float z1) {
		if (safe > 0 && within(safe, x0, y0, z0, x1, y1, z1)) {
			return true;
		}
		if (distance > 0 && !within(distance, x0, y0, z0, x1, y1, z1)) {
			return false;
		}
		for (int p = 0; p < count; p++) {
			float a = planes[p * 4];
			float b = planes[p * 4 + 1];
			float c = planes[p * 4 + 2];
			float d = planes[p * 4 + 3];
			float x = a >= 0 ? x1 : x0;
			float y = b >= 0 ? y1 : y0;
			float z = c >= 0 ? z1 : z0;
			if (a * x + b * y + c * z + d < -Frustum.MARGIN) {
				return false;
			}
		}
		return true;
	}

	/** Whether the box reaches into the cube of half-size {@code d} around the camera. */
	private static boolean within(float d, float x0, float y0, float z0, float x1, float y1, float z1) {
		return x1 >= -d && x0 <= d && y1 >= -d && y0 <= d && z1 >= -d && z0 <= d;
	}
}
