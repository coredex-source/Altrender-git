/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * An immutable copy of one 16³ section plus a {@value #MARGIN}-block margin (development.md 2.1): block ids, metadata,
 * raw sky and block light, and per column the biome temperature and downfall (over a wider area, see
 * {@link #BIOME_REACH}), taken on the main thread so meshing
 * workers never touch the live world. Plain data: each mc node fills it ({@code adapter/SnapshotCapture}) and reads it
 * through its version's block-access interface ({@code adapter/SnapshotWorldView}).
 * <p>
 * The margin is 2, not 1: the block renderer reads every neighbour of the section's blocks, and slabs, stairs and
 * farmland take the brightest light of their own neighbours, one block further.
 * <p>
 * Light stays raw (sky and block separately, plus the world's sky darkening at capture time), so the lighting design
 * of 2.3 can resolve it either at meshing time (vanilla) or in the shader.
 */
public final class SectionSnapshot {

	public static final int MARGIN = 2;
	public static final int SIZE = 16 + 2 * MARGIN;
	public static final int CELLS = SIZE * SIZE * SIZE;
	/**
	 * Biome columns reach further than blocks: tall grass samples its colour at a pseudo-random offset of up to 31
	 * blocks in +x and +z, so biome data covers the margin plus 16 + 31 blocks along each axis.
	 */
	public static final int BIOME_REACH = 31;
	public static final int BIOME_SIZE = 16 + 2 * MARGIN + BIOME_REACH;
	/** Chunks the biome area can touch along one axis. */
	public static final int BIOME_CHUNKS = (BIOME_SIZE + 15) / 16 + 1;

	private int originX;
	private int originY;
	private int originZ;
	/** Block ids (b1.7.3–1.12: below 4096). */
	public final short[] blocks = new short[CELLS];
	public final byte[] meta = new byte[CELLS];
	public final byte[] skyLight = new byte[CELLS];
	public final byte[] blockLight = new byte[CELLS];
	/**
	 * Biome data by chunk: shared, immutable 16 × 16 arrays (x-major) from the node's per-chunk cache, so a capture
	 * stores references instead of copying columns.
	 */
	private final double[][] chunkTemperature = new double[BIOME_CHUNKS * BIOME_CHUNKS][];
	private final double[][] chunkDownfall = new double[BIOME_CHUNKS * BIOME_CHUNKS][];
	private final Object[][] chunkColumnData = new Object[BIOME_CHUNKS * BIOME_CHUNKS][];
	private int biomeChunkX;
	private int biomeChunkZ;
	/** The world's sky darkening when captured (0 at noon, 11 at night in b1.7.3). */
	public int ambientDarkness;
	/**
	 * Mesh for a shader pack (development.md 6.6): the light stays out of the vertex colour and the mesher fills the
	 * extended format's extra data ({@link TerrainVertex#blockSlot}, {@link TerrainVertex#lightSlot}).
	 */
	public boolean extended;
	/** In extended meshing: the game's per-face shading kept (a pipeline's {@code faceShading}). */
	public boolean faceShade = true;
	/** In extended meshing: ambient occlusion in the colour's alpha instead of its colour. */
	public boolean separateAo;
	/** Light level → brightness, the dimension's table. */
	public final float[] brightness = new float[16];

	/** Starts a capture of the section whose minimum corner is at (x, y, z) in world blocks. */
	public void begin(int x, int y, int z) {
		originX = x;
		originY = y;
		originZ = z;
		biomeChunkX = (x - MARGIN) >> 4;
		biomeChunkZ = (z - MARGIN) >> 4;
	}

	public int originX() {
		return originX;
	}

	public int originY() {
		return originY;
	}

	public int originZ() {
		return originZ;
	}

	/** Whether world position (x, y, z) is inside the snapshot (section plus margin). */
	public boolean contains(int x, int y, int z) {
		return x - originX + MARGIN >= 0 && x - originX + MARGIN < SIZE && y - originY + MARGIN >= 0
				&& y - originY + MARGIN < SIZE && z - originZ + MARGIN >= 0 && z - originZ + MARGIN < SIZE;
	}

	/** Cell index of world position (x, y, z); the position must be {@link #contains contained}. */
	public int index(int x, int y, int z) {
		return ((y - originY + MARGIN) * SIZE + (z - originZ + MARGIN)) * SIZE + (x - originX + MARGIN);
	}

	public boolean containsColumn(int x, int z) {
		return x - originX + MARGIN >= 0 && x - originX + MARGIN < BIOME_SIZE && z - originZ + MARGIN >= 0
				&& z - originZ + MARGIN < BIOME_SIZE;
	}

	/** Sets the biome data of chunk (cx, cz), which must touch the biome area. Arrays are x-major, 256 entries. */
	public void setBiomeChunk(int cx, int cz, double[] temperature, double[] downfall, Object[] columnData) {
		int i = (cx - biomeChunkX) * BIOME_CHUNKS + (cz - biomeChunkZ);
		chunkTemperature[i] = temperature;
		chunkDownfall[i] = downfall;
		chunkColumnData[i] = columnData;
	}

	public double temperature(int x, int z) {
		return chunkTemperature[biomeChunk(x, z)][(x & 15) * 16 + (z & 15)];
	}

	public double downfall(int x, int z) {
		return chunkDownfall[biomeChunk(x, z)][(x & 15) * 16 + (z & 15)];
	}

	/** The node's per-column object (b1.7.3: the column's biome). */
	public Object columnData(int x, int z) {
		return chunkColumnData[biomeChunk(x, z)][(x & 15) * 16 + (z & 15)];
	}

	private int biomeChunk(int x, int z) {
		return ((x >> 4) - biomeChunkX) * BIOME_CHUNKS + ((z >> 4) - biomeChunkZ);
	}

	/** Clears the cells (air, no light) before a capture fills the loaded part. */
	public void clear() {
		java.util.Arrays.fill(blocks, (short) 0);
		java.util.Arrays.fill(meta, (byte) 0);
		java.util.Arrays.fill(skyLight, (byte) 0);
		java.util.Arrays.fill(blockLight, (byte) 0);
		java.util.Arrays.fill(chunkTemperature, null);
		java.util.Arrays.fill(chunkDownfall, null);
		java.util.Arrays.fill(chunkColumnData, null);
	}
}
