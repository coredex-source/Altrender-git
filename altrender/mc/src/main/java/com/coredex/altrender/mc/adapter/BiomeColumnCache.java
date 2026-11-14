/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.util.LongMap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * Biome temperature, downfall and biome per chunk column, computed once from the world's biome source (three noise
 * regions per query, the bulk of a snapshot's cost) and kept for the last {@value #CAPACITY} chunks computed. A b1.7.3
 * column's biome never changes. Main thread only; no allocation on a hit (it's asked every frame by the weather).
 */
public final class BiomeColumnCache {

	/** One chunk column: 16 × 16, x-major like the biome source's regions. */
	record Chunk(double[] temperature, double[] downfall, Biome[] biome) {
	}

	private static final int CAPACITY = 2048;

	private static World world;
	private static final LongMap<Chunk> CHUNKS = new LongMap<>();
	/** Keys in the order they were added; the oldest goes when the cache is full. */
	private static final long[] ORDER = new long[CAPACITY];
	private static int oldest;

	private BiomeColumnCache() {
	}

	/**
	 * The biomes of [x, x + sizeX) × [z, z + sizeZ), x-major as {@code BiomeSource#getBiomes} returns them, from the
	 * cache (development.md 3.1: weather asked the noise for 441 columns twice a frame). Into {@code into} if big enough.
	 */
	public static Biome[] region(World w, int x, int z, int sizeX, int sizeZ, Biome[] into) {
		Biome[] out = into != null && into.length >= sizeX * sizeZ ? into : new Biome[sizeX * sizeZ];
		int i = 0;
		for (int bx = x; bx < x + sizeX; bx++) {
			Biome[] chunk = null;
			for (int bz = z; bz < z + sizeZ; bz++) {
				if (chunk == null || (bz & 15) == 0) {
					chunk = get(w, bx >> 4, bz >> 4).biome();
				}
				out[i++] = chunk[(bx & 15) * 16 + (bz & 15)];
			}
		}
		return out;
	}

	static Chunk get(World w, int cx, int cz) {
		if (w != world) {
			CHUNKS.clear();
			oldest = 0;
			world = w;
		}
		long key = (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
		Chunk c = CHUNKS.get(key);
		if (c == null) {
			BiomeSource source = w.getBiomeSource();
			Biome[] region = source.getBiomes(cx << 4, cz << 4, 16, 16);
			c = new Chunk(source.temperatures.clone(), source.downfalls.clone(), region.clone());
			if (CHUNKS.size() >= CAPACITY) {
				CHUNKS.remove(ORDER[oldest]);
			}
			CHUNKS.put(key, c);
			ORDER[oldest] = key;
			oldest = (oldest + 1) % CAPACITY;
		}
		return c;
	}
}
