/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Modern block tags for b1.7.3's blocks (development.md 6.6; shader packs' {@code %tag} entries in block.properties):
 * which tags the modern blocks that b1.7.3 has belong to, in 1.21.11 or 26.1–26.3 (a block in a tag in either line:
 * packs are written for both), written for Altrender as facts of the versions. Only tags with b1.7.3 members, and not
 * the tool, spawning or sound ones packs don't shade by.
 */
final class B173BlockTags {

	/** Modern block name (no namespace) → its tags ({@code minecraft:leaves}). */
	private static final Map<String, Set<String>> TAGS = new HashMap<>();

	static {
		String[] woods = {"oak", "spruce", "birch"};
		for (String w : woods) {
			tag(w + "_leaves", "leaves");
			tag(w + "_sapling", "saplings");
			tag(w + "_log", w + "_logs", "logs", "logs_that_burn", "overworld_natural_logs");
		}
		tag("oak_planks", "planks");
		tag("dandelion", "small_flowers", "flowers");
		tag("poppy", "small_flowers", "flowers");
		tag("wheat", "crops");
		tag("dirt", "dirt");
		// #dirt until 1.21.11 (packs written for it reach grass blocks there), #grass_blocks since 26.1.
		tag("grass_block", "dirt", "grass_blocks");
		tag("sand", "sand");
		tag("ice", "ice", "polar_bears_spawnable_on_alternate");
		tag("snow", "snow");
		tag("snow_block", "snow");
		tag("rail", "rails");
		tag("powered_rail", "rails");
		tag("detector_rail", "rails");
		tag("fire", "fire");
		for (String c : new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
				"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
			tag(c + "_wool", "wool");
		}
		tag("coal_ore", "coal_ores", "ores");
		tag("iron_ore", "iron_ores", "ores");
		tag("gold_ore", "gold_ores", "ores");
		tag("diamond_ore", "diamond_ores", "ores");
		tag("lapis_ore", "lapis_ores", "ores");
		tag("redstone_ore", "redstone_ores", "ores");
		for (String b : new String[] {"short_grass", "fern", "dead_bush"}) {
			tag(b, "replaceable", "replaceable_by_trees");
		}
		for (String b : new String[] {"fire", "lava", "snow", "water"}) {
			tag(b, "replaceable");
		}
		for (String b : new String[] {"oak_leaves", "spruce_leaves", "birch_leaves", "dandelion", "poppy", "water"}) {
			tag(b, "replaceable_by_trees");
		}
		tag("stone", "base_stone_overworld");
		tag("netherrack", "base_stone_nether");
		tag("oak_door", "wooden_doors", "doors");
		tag("iron_door", "doors");
		tag("oak_fence", "wooden_fences", "fences");
		tag("oak_pressure_plate", "wooden_pressure_plates", "pressure_plates");
		tag("stone_pressure_plate", "stone_pressure_plates", "pressure_plates");
		tag("oak_slab", "wooden_slabs", "slabs");
		tag("cobblestone_slab", "slabs");
		tag("sandstone_slab", "slabs");
		tag("smooth_stone_slab", "slabs");
		tag("oak_stairs", "wooden_stairs", "stairs");
		tag("cobblestone_stairs", "stairs");
		tag("oak_trapdoor", "wooden_trapdoors", "trapdoors");
		tag("stone_button", "stone_buttons", "buttons");
		tag("oak_sign", "all_signs", "signs", "standing_signs");
		tag("oak_wall_sign", "all_signs", "signs", "wall_signs");
		tag("bookshelf", "enchantment_power_provider");
		tag("soul_sand", "soul_fire_base_blocks", "soul_speed_blocks");
		tag("red_bed", "beds");
		tag("nether_portal", "portals");
		tag("ladder", "climbable");
	}

	private B173BlockTags() {
	}

	private static void tag(String block, String... tags) {
		Set<String> set = TAGS.computeIfAbsent(block, k -> new LinkedHashSet<>());
		for (String t : tags) {
			set.add("minecraft:" + t);
		}
	}

	/** The tags of a modern block name ({@code minecraft:oak_leaves}); empty for none. */
	static Set<String> of(String name) {
		if (name == null) {
			return Set.of();
		}
		Set<String> t = TAGS.get(name.startsWith("minecraft:") ? name.substring(10) : name);
		return t != null ? t : Set.of();
	}
}
