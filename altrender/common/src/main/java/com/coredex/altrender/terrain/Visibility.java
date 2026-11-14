/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * Section face connectivity for cave culling (development.md 2.5): which of a section's six faces can see each other
 * through cells that aren't full opaque cubes. 36 bits, bit {@code from * 6 + to}. Computed on the meshing workers by
 * a flood fill over the section's 16³ cells.
 */
public final class Visibility {

	/** Faces, in this order: down, up, north (−z), south (+z), west (−x), east (+x). */
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int WEST = 4;
	public static final int EAST = 5;
	public static final int[] DX = {0, 0, 0, 0, -1, 1};
	public static final int[] DY = {-1, 1, 0, 0, 0, 0};
	public static final int[] DZ = {0, 0, -1, 1, 0, 0};
	/** Every face sees every other: unknown, empty, or not built yet. */
	public static final long ALL = (1L << 36) - 1;

	private Visibility() {
	}

	public static int opposite(int face) {
		return face ^ 1;
	}

	public static boolean connected(long visibility, int from, int to) {
		return (visibility >>> (from * 6 + to) & 1L) != 0;
	}

	/**
	 * @param opaque 4096 cells, index {@code (y * 16 + z) * 16 + x}, true for full opaque cubes
	 */
	public static long compute(boolean[] opaque) {
		int open = 0;
		for (boolean o : opaque) {
			if (!o) {
				open++;
			}
		}
		if (open == 0) {
			return 0L;
		}
		if (open == 4096) {
			return ALL;
		}
		boolean[] seen = new boolean[4096];
		int[] queue = new int[4096];
		long result = 0L;
		for (int start = 0; start < 4096; start++) {
			if (opaque[start] || seen[start]) {
				continue;
			}
			int faces = 0;
			int head = 0;
			int tail = 0;
			queue[tail++] = start;
			seen[start] = true;
			while (head < tail) {
				int c = queue[head++];
				int x = c & 15;
				int z = (c >> 4) & 15;
				int y = c >> 8;
				for (int f = 0; f < 6; f++) {
					int nx = x + DX[f];
					int ny = y + DY[f];
					int nz = z + DZ[f];
					if (nx < 0 || nx > 15 || ny < 0 || ny > 15 || nz < 0 || nz > 15) {
						faces |= 1 << f;
						continue;
					}
					int n = (ny * 16 + nz) * 16 + nx;
					if (!opaque[n] && !seen[n]) {
						seen[n] = true;
						queue[tail++] = n;
					}
				}
			}
			for (int a = 0; a < 6; a++) {
				if ((faces & 1 << a) != 0) {
					for (int b = 0; b < 6; b++) {
						if ((faces & 1 << b) != 0) {
							result |= 1L << (a * 6 + b);
						}
					}
				}
			}
		}
		return result;
	}
}
