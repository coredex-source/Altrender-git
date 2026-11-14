/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.terrain.SectionSnapshot;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * The game's block-access interface over a {@link SectionSnapshot} (development.md 2.1): what the block renderer reads
 * while meshing on a worker. Every answer follows vanilla's {@code WorldRegion} (the view {@code RenderChunk} builds)
 * for positions within the section plus one block, which is all the block renderer asks about; the snapshot's second
 * margin block serves the neighbour light of slabs, stairs and farmland.
 * <p>
 * Block entities aren't snapshotted: no block the renderer meshes reads one (the moving piston, which does, renders
 * as a block entity instead).
 */
public final class SnapshotWorldView implements WorldView {

	private static final int HEIGHT = 128;

	private final SectionSnapshot s;
	private final SnapshotBiomeSource biomes;
	/** Set when a light query found sky light (vanilla's {@code WorldChunk.hasSkyLight}, per section here). */
	private boolean readSkyLight;

	public SnapshotWorldView(SectionSnapshot snapshot) {
		this.s = snapshot;
		this.biomes = new SnapshotBiomeSource(snapshot);
	}

	public SectionSnapshot snapshot() {
		return s;
	}

	/** Whether any light query so far saw sky light (decides whether the section rebuilds on sky changes). */
	public boolean readSkyLight() {
		return readSkyLight;
	}

	@Override
	public int getBlock(int x, int y, int z) {
		if (y < 0 || y >= HEIGHT || !s.contains(x, y, z)) {
			return 0;
		}
		return s.blocks[s.index(x, y, z)];
	}

	@Override
	public BlockEntity getBlockEntity(int x, int y, int z) {
		return null;
	}

	@Override
	public float getBrightness(int x, int y, int z, int blockLight) {
		if (s.extended) {
			return 1.0f; // a shader pack lights the terrain itself (development.md 6.6)
		}
		int i = rawBrightness(x, y, z, true);
		if (i < blockLight) {
			i = blockLight;
		}
		return s.brightness[i];
	}

	@Override
	public float getBrightness(int x, int y, int z) {
		if (s.extended) {
			return 1.0f;
		}
		return s.brightness[rawBrightness(x, y, z, true)];
	}

	/**
	 * Sky light (raw, without the sky's darkness) and block light of a cell for a shader pack's light coordinates;
	 * {@code out[0]} block, {@code out[1]} sky. Cells above the world have full sky, below none.
	 */
	public void light(int x, int y, int z, int[] out) {
		if (y >= HEIGHT) {
			out[0] = 0;
			out[1] = 15;
			return;
		}
		if (y < 0 || !s.contains(x, y, z)) {
			out[0] = 0;
			out[1] = 0;
			return;
		}
		int i = s.index(x, y, z);
		out[0] = s.blockLight[i];
		out[1] = s.skyLight[i];
		if (out[1] > 0) {
			readSkyLight = true;
		}
	}

	/** WorldRegion#getRawBrightness, on the snapshot. */
	private int rawBrightness(int x, int y, int z, boolean useNeighborLight) {
		if (x < -32000000 || z < -32000000 || x >= 32000000 || z > 32000000) {
			return 15;
		}
		if (useNeighborLight) {
			int id = getBlock(x, y, z);
			if (id == Block.STONE_SLAB.id || id == Block.FARMLAND.id || id == Block.OAK_STAIRS.id
					|| id == Block.STONE_STAIRS.id) {
				int l = rawBrightness(x, y + 1, z, false);
				l = Math.max(l, rawBrightness(x + 1, y, z, false));
				l = Math.max(l, rawBrightness(x - 1, y, z, false));
				l = Math.max(l, rawBrightness(x, y, z + 1, false));
				l = Math.max(l, rawBrightness(x, y, z - 1, false));
				return l;
			}
		}
		if (y < 0) {
			return 0;
		}
		if (y >= HEIGHT) {
			return Math.max(0, 15 - s.ambientDarkness);
		}
		if (!s.contains(x, y, z)) {
			throw new IllegalStateException("light query outside the snapshot: " + x + "," + y + "," + z);
		}
		int i = s.index(x, y, z);
		// WorldChunk#getActualLightAt
		int light = s.skyLight[i];
		if (light > 0) {
			readSkyLight = true;
		}
		light -= s.ambientDarkness;
		int block = s.blockLight[i];
		return Math.max(light, block);
	}

	@Override
	public int getBlockMetadata(int x, int y, int z) {
		if (y < 0 || y >= HEIGHT || !s.contains(x, y, z)) {
			return 0;
		}
		return s.meta[s.index(x, y, z)];
	}

	@Override
	public Material getMaterial(int x, int y, int z) {
		int id = getBlock(x, y, z);
		return id == 0 ? Material.AIR : Block.BY_ID[id].material;
	}

	@Override
	public BiomeSource getBiomeSource() {
		return biomes;
	}

	@Override
	public boolean isSolidRender(int x, int y, int z) {
		Block block = Block.BY_ID[getBlock(x, y, z)];
		return block != null && block.isSolidRender();
	}

	@Override
	public boolean isSolidBlock(int x, int y, int z) {
		Block block = Block.BY_ID[getBlock(x, y, z)];
		return block != null && block.material.blocksMovement() && block.isCube();
	}
}
