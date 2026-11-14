/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.terrain.SectionSnapshot;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * The biome source a snapshot gives the block renderer: grass, leaves and tall grass call {@code getBiomes(x, z, 1, 1)}
 * and then read {@code temperatures[0]} and {@code downfalls[0]}, so this answers from the snapshot's columns into its
 * own arrays (tall grass asks up to 31 blocks away: {@link SectionSnapshot#BIOME_REACH}). One per {@link SnapshotWorldView}, i.e. per worker; never shared.
 */
final class SnapshotBiomeSource extends BiomeSource {

	private final SectionSnapshot snapshot;
	private final Biome[] one = new Biome[1];

	SnapshotBiomeSource(SectionSnapshot snapshot) {
		this.snapshot = snapshot;
		this.temperatures = new double[1];
		this.downfalls = new double[1];
	}

	@Override
	public Biome[] getBiomes(int x, int z, int sizeX, int sizeZ) {
		if (sizeX != 1 || sizeZ != 1 || !snapshot.containsColumn(x, z)) {
			throw new UnsupportedOperationException("snapshot biome query " + x + "," + z + " " + sizeX + "x" + sizeZ);
		}
		temperatures[0] = snapshot.temperature(x, z);
		downfalls[0] = snapshot.downfall(x, z);
		one[0] = (Biome) snapshot.columnData(x, z);
		return one;
	}

	@Override
	public Biome getBiome(int x, int z) {
		return getBiomes(x, z, 1, 1)[0];
	}
}
