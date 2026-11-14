/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.terrain.SectionSnapshot;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Fills a {@link SectionSnapshot} from the live b1.7.3 world (development.md 2.1). Main thread only. */
public final class SnapshotCapture {

	private static final int HEIGHT = 128;

	private SnapshotCapture() {
	}

	/** Captures section (sectionX, sectionY, sectionZ), in section coordinates, into {@code s}. */
	public static void capture(World world, int sectionX, int sectionY, int sectionZ, SectionSnapshot s) {
		int ox = sectionX << 4;
		int oy = sectionY << 4;
		int oz = sectionZ << 4;
		s.begin(ox, oy, oz);
		s.clear();
		s.ambientDarkness = world.ambientDarkness;
		System.arraycopy(world.dimension.brightnessTable, 0, s.brightness, 0, 16);
		int m = SectionSnapshot.MARGIN;
		int minX = ox - m;
		int maxX = ox + 16 + m - 1;
		int minZ = oz - m;
		int maxZ = oz + 16 + m - 1;
		int minY = Math.max(0, oy - m);
		int maxY = Math.min(HEIGHT - 1, oy + 16 + m - 1);
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				WorldChunk chunk = world.getChunkAt(cx, cz);
				int x0 = Math.max(minX, cx << 4);
				int x1 = Math.min(maxX, (cx << 4) + 15);
				int z0 = Math.max(minZ, cz << 4);
				int z1 = Math.min(maxZ, (cz << 4) + 15);
				if (chunk.getClass() == WorldChunk.class) {
					copyArrays(chunk, s, x0, x1, z0, z1, minY, maxY);
					continue;
				}
				// Subclasses (EmptyChunk) answer through their own methods.
				for (int y = minY; y <= maxY; y++) {
					for (int z = z0; z <= z1; z++) {
						for (int x = x0; x <= x1; x++) {
							int i = s.index(x, y, z);
							int lx = x & 15;
							int lz = z & 15;
							s.blocks[i] = (short) chunk.getBlockAt(lx, y, lz);
							s.meta[i] = (byte) chunk.getBlockMetadataAt(lx, y, lz);
							s.skyLight[i] = (byte) chunk.getLightAt(LightType.SKY, lx, y, lz);
							s.blockLight[i] = (byte) chunk.getLightAt(LightType.BLOCK, lx, y, lz);
						}
					}
				}
			}
		}
		// Biome colours per column, from the per-chunk cache (the biome source's own buffers aren't thread-safe), over
		// the wider biome area (tall grass samples up to 31 blocks away).
		int maxBX = minX + SectionSnapshot.BIOME_SIZE - 1;
		int maxBZ = minZ + SectionSnapshot.BIOME_SIZE - 1;
		for (int cx = minX >> 4; cx <= maxBX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxBZ >> 4; cz++) {
				BiomeColumnCache.Chunk c = BiomeColumnCache.get(world, cx, cz);
				s.setBiomeChunk(cx, cz, c.temperature(), c.downfall(), c.biome());
			}
		}
	}

	/** A plain chunk's storage directly: index x << 11 | z << 7 | y, nibbles low half first (as WorldChunk reads it). */
	private static void copyArrays(WorldChunk chunk, SectionSnapshot s, int x0, int x1, int z0, int z1, int minY,
			int maxY) {
		byte[] blocks = chunk.blocks;
		byte[] meta = chunk.blockMetadata.data;
		byte[] sky = chunk.skyLight.data;
		byte[] block = chunk.blockLight.data;
		int strideY = SectionSnapshot.SIZE * SectionSnapshot.SIZE;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int c = (x & 15) << 11 | (z & 15) << 7;
				int i = s.index(x, minY, z);
				for (int y = minY; y <= maxY; y++, i += strideY) {
					int ci = c | y;
					int shift = (ci & 1) << 2;
					int half = ci >> 1;
					s.blocks[i] = (short) (blocks[ci] & 0xFF);
					s.meta[i] = (byte) (meta[half] >> shift & 15);
					s.skyLight[i] = (byte) (sky[half] >> shift & 15);
					s.blockLight[i] = (byte) (block[half] >> shift & 15);
				}
			}
		}
	}
}
