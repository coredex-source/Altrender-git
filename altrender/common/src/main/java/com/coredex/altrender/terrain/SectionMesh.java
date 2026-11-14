/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.util.Arrays;

/**
 * One section's meshing output (development.md 2.2): per render layer (0 opaque + cutout, 1 translucent, as vanilla
 * splits them), the quads the game's block renderer produced, in its vertex layout: 4 vertices × 8 ints (x, y, z
 * relative to the section; u, v as float bits; colour; normal; 0). Slots the renderer didn't write are 0. The vertex
 * format of 2.3 is made from this.
 */
public final class SectionMesh {

	public static final int LAYERS = 2;
	public static final int INTS_PER_VERTEX = 8;
	public static final int INTS_PER_QUAD = 4 * INTS_PER_VERTEX;

	private final int[][] quads = {new int[INTS_PER_QUAD * 64], new int[INTS_PER_QUAD * 16]};
	private final int[] size = new int[LAYERS];
	/** Whether meshing read any sky light (vanilla rebuilds such sections when the sky darkens). */
	public boolean readSkyLight;
	/** Face connectivity ({@link Visibility}); {@link Visibility#ALL} until the mesher computes it. */
	public long visibility = Visibility.ALL;

	public void clear() {
		Arrays.fill(size, 0);
		readSkyLight = false;
		visibility = Visibility.ALL;
	}

	public int quadCount(int layer) {
		return size[layer] / INTS_PER_QUAD;
	}

	public int[] data(int layer) {
		return quads[layer];
	}

	/** Makes room for {@code ints} more ints in {@code layer} and returns the offset to write at. */
	public int append(int layer, int ints) {
		int at = size[layer];
		if (at + ints > quads[layer].length) {
			quads[layer] = Arrays.copyOf(quads[layer], Math.max(at + ints, quads[layer].length * 2));
		}
		size[layer] = at + ints;
		return at;
	}

	/** FNV-1a over a layer's ints: compares two captures (the 2.2 self-test). */
	public long hash(int layer) {
		long h = 0xcbf29ce484222325L;
		int[] q = quads[layer];
		for (int i = 0; i < size[layer]; i++) {
			h = (h ^ q[i]) * 0x100000001b3L;
		}
		return h;
	}
}
