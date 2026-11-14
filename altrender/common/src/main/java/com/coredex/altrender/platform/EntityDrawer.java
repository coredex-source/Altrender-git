/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.platform;

/** The version's entity render for another camera (development.md 6.7: the API's entity view). */
public interface EntityDrawer {

	/** Whether a box (world coordinates) is seen by the other camera. */
	interface BoxTest {
		boolean visible(double x0, double y0, double z0, double x1, double y1, double z1);
	}

	/** Draws the world's entities and block entities as the game does, with {@code visible} as its culling. */
	void draw(BoxTest visible);
}
