/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack;

import java.util.List;
import java.util.Locale;

/**
 * The biome names packs know ({@code BIOME_<NAME>} defines and the {@code biome} uniform), with an id each. Packs
 * are written for the modern game's biomes, so all of those are defined (a pack's {@code biome == BIOME_CHERRY_GROVE}
 * must compile); b1.7.3's biomes report the closest one ({@link #of}). Only the names matter: packs compare the
 * uniform with the defines and never hard-code the numbers.
 */
public final class Biomes {

	/** The modern game's biome names; the index is the id. */
	public static final List<String> NAMES = List.of("the_void", "plains", "sunflower_plains", "snowy_plains",
			"ice_spikes", "desert", "swamp", "mangrove_swamp", "forest", "flower_forest", "birch_forest",
			"dark_forest", "pale_garden", "old_growth_birch_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga",
			"taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
			"windswept_forest", "windswept_savanna", "jungle", "sparse_jungle", "bamboo_jungle", "badlands",
			"eroded_badlands", "wooded_badlands", "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks",
			"jagged_peaks", "stony_peaks", "river", "frozen_river", "beach", "snowy_beach", "stony_shore", "warm_ocean",
			"lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean",
			"frozen_ocean", "deep_frozen_ocean", "mushroom_fields", "dripstone_caves", "lush_caves", "deep_dark",
			"nether_wastes", "warped_forest", "crimson_forest", "soul_sand_valley", "basalt_deltas", "the_end",
			"end_highlands", "end_midlands", "small_end_islands", "end_barrens");

	private Biomes() {
	}

	public static int id(String name) {
		return NAMES.indexOf(name);
	}

	/**
	 * The modern biome closest to a b1.7.3 one (by the beta biome's name, lower case with underscores:
	 * {@code rainforest}, {@code swampland}, {@code seasonal_forest}, {@code forest}, {@code savanna},
	 * {@code shrubland}, {@code taiga}, {@code desert}, {@code plains}, {@code ice_desert}, {@code tundra},
	 * {@code hell}, {@code sky}).
	 */
	public static String of(String betaBiome) {
		return switch (betaBiome.toLowerCase(Locale.ROOT)) {
			case "rainforest" -> "jungle";
			case "swampland" -> "swamp";
			case "seasonal_forest" -> "flower_forest";
			case "forest" -> "forest";
			case "savanna" -> "savanna";
			case "shrubland" -> "sparse_jungle";
			case "taiga" -> "taiga";
			case "desert" -> "desert";
			case "ice_desert", "tundra" -> "snowy_plains";
			case "hell" -> "nether_wastes";
			case "sky" -> "the_end";
			default -> "plains";
		};
	}
}
