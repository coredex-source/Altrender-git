/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.coredex.altrender.Log;
import com.coredex.altrender.mc.adapter.SnapshotCapture;
import com.coredex.altrender.mc.adapter.SnapshotWorldView;
import com.coredex.altrender.terrain.SectionSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * {@code -Daltrender.debug.snapshotTest} (development.md 2.1): once the world has rendered for a while, 1,000 random
 * loaded sections (fixed seed) are snapshotted, and every query the block renderer can make at every position of the
 * section plus one block is compared with the {@code WorldRegion} vanilla's {@code RenderChunk} builds for that
 * section; so are the biome temperature, downfall and biome per column. Only sections whose surrounding chunks are
 * loaded are picked (so the test never generates chunks).
 */
public final class SnapshotSelfTest {

	private static final boolean ENABLED = Boolean.getBoolean("altrender.debug.snapshotTest");
	private static final int WAIT_FRAMES = 200;
	private static final int SECTIONS = 1000;
	private static int frames;
	private static boolean done;

	private SnapshotSelfTest() {
	}

	/** Called at the start of each world frame. */
	public static void onWorldFrame(Minecraft minecraft) {
		if (!ENABLED || done || minecraft.world == null || minecraft.player == null || ++frames < WAIT_FRAMES) {
			return;
		}
		done = true;
		run(minecraft.world, (int) Math.floor(minecraft.player.x) >> 4, (int) Math.floor(minecraft.player.z) >> 4);
	}

	private static void run(World world, int playerCx, int playerCz) {
		long start = System.nanoTime();
		Random random = new Random(2026);
		Map<String, Integer> mismatches = new LinkedHashMap<>();
		List<String> examples = new ArrayList<>();
		SectionSnapshot snapshot = new SectionSnapshot();
		long queries = 0;
		long captureNs = 0;
		int sections = 0;
		int tries = 0;
		while (sections < SECTIONS && tries++ < SECTIONS * 50) {
			int cx = playerCx + random.nextInt(17) - 8;
			int cz = playerCz + random.nextInt(17) - 8;
			int sy = random.nextInt(8);
			if (!loaded(world, cx, cz)) {
				continue;
			}
			sections++;
			long t0 = System.nanoTime();
			SnapshotCapture.capture(world, cx, sy, cz, snapshot);
			captureNs += System.nanoTime() - t0;
			SnapshotWorldView view = new SnapshotWorldView(snapshot);
			int ox = cx << 4;
			int oy = sy << 4;
			int oz = cz << 4;
			// As RenderChunk#compile builds it: the section plus one block.
			WorldRegion region = new WorldRegion(world, ox - 1, oy - 1, oz - 1, ox + 17, oy + 17, oz + 17);
			for (int y = oy - 1; y <= oy + 16; y++) {
				for (int z = oz - 1; z <= oz + 16; z++) {
					for (int x = ox - 1; x <= ox + 16; x++) {
						queries += 7;
						compare(mismatches, examples, "block", x, y, z, region.getBlock(x, y, z), view.getBlock(x, y, z));
						compare(mismatches, examples, "meta", x, y, z, region.getBlockMetadata(x, y, z),
								view.getBlockMetadata(x, y, z));
						compare(mismatches, examples, "material", x, y, z, region.getMaterial(x, y, z),
								view.getMaterial(x, y, z));
						compare(mismatches, examples, "solidRender", x, y, z, region.isSolidRender(x, y, z),
								view.isSolidRender(x, y, z));
						compare(mismatches, examples, "solidBlock", x, y, z, region.isSolidBlock(x, y, z),
								view.isSolidBlock(x, y, z));
						compare(mismatches, examples, "brightness", x, y, z, region.getBrightness(x, y, z),
								view.getBrightness(x, y, z));
						compare(mismatches, examples, "brightness(7)", x, y, z, region.getBrightness(x, y, z, 7),
								view.getBrightness(x, y, z, 7));
					}
				}
			}
			BiomeSource live = world.getBiomeSource();
			BiomeSource ours = view.getBiomeSource();
			for (int z = oz - 1; z <= oz + 16; z++) {
				for (int x = ox - 1; x <= ox + 16; x++) {
					queries += 3;
					Object liveBiome = live.getBiomes(x, z, 1, 1)[0];
					double liveT = live.temperatures[0];
					double liveD = live.downfalls[0];
					Object ourBiome = ours.getBiomes(x, z, 1, 1)[0];
					compare(mismatches, examples, "biome", x, 0, z, liveBiome, ourBiome);
					compare(mismatches, examples, "temperature", x, 0, z, liveT, ours.temperatures[0]);
					compare(mismatches, examples, "downfall", x, 0, z, liveD, ours.downfalls[0]);
				}
			}
		}
		long ms = (System.nanoTime() - start) / 1_000_000;
		String capture = sections == 0 ? "" : ", capture " + captureNs / sections / 1000 + " µs per section";
		int total = mismatches.values().stream().mapToInt(Integer::intValue).sum();
		if (total == 0) {
			Log.info("snapshot test: " + sections + " sections, " + queries + " queries, 0 mismatches (" + ms + " ms" + capture + ")");
		} else {
			Log.warn("snapshot test: " + sections + " sections, " + queries + " queries, " + total + " mismatches "
					+ mismatches + "; first: " + examples);
		}
	}

	/** The section's chunk and its eight neighbours are loaded. */
	private static boolean loaded(World world, int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!world.getChunkSource().hasChunk(cx + dx, cz + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void compare(Map<String, Integer> mismatches, List<String> examples, String what, int x, int y, int z,
			Object live, Object ours) {
		if (!java.util.Objects.equals(live, ours)) {
			mismatches.merge(what, 1, Integer::sum);
			if (examples.size() < 10) {
				examples.add(what + "@" + x + "," + y + "," + z + " live=" + live + " ours=" + ours);
			}
		}
	}
}
