/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import java.util.HashMap;
import java.util.Map;

import com.coredex.altrender.api.BlockNames;

/**
 * b1.7.3's blocks under their modern names (development.md 6.6), id and metadata to name and state: the facts of the
 * two versions' block lists, written for Altrender. States are the ones packs match on (crop ages, lit furnaces and
 * ore, torch walls, slab halves, snow layers, wool and leaf kinds by name).
 */
public final class B173BlockNames implements BlockNames {

	private final String[] names = new String[256 * 16];
	private final Map<Integer, Map<String, String>> states = new HashMap<>();

	private static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
			"gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	public B173BlockNames() {
		all(1, "stone");
		all(2, "grass_block");
		all(3, "dirt");
		all(4, "cobblestone");
		all(5, "oak_planks");
		for (int m = 0; m < 16; m++) {
			set(6, m, switch (m & 3) {
				case 1 -> "spruce_sapling";
				case 2 -> "birch_sapling";
				default -> "oak_sapling";
			});
		}
		all(7, "bedrock");
		for (int m = 0; m < 16; m++) {
			set(8, m, "water", "level", Integer.toString(m & 7));
			set(9, m, "water", "level", Integer.toString(m & 7));
			set(10, m, "lava", "level", Integer.toString(m & 7));
			set(11, m, "lava", "level", Integer.toString(m & 7));
		}
		all(12, "sand");
		all(13, "gravel");
		all(14, "gold_ore");
		all(15, "iron_ore");
		all(16, "coal_ore");
		for (int m = 0; m < 16; m++) {
			String wood = switch (m & 3) {
				case 1 -> "spruce";
				case 2 -> "birch";
				default -> "oak";
			};
			set(17, m, wood + "_log", "axis", "y");
			set(18, m, wood + "_leaves");
			set(35, m, COLOURS[m] + "_wool");
		}
		all(19, "sponge");
		all(20, "glass");
		all(21, "lapis_ore");
		all(22, "lapis_block");
		all(23, "dispenser");
		all(24, "sandstone");
		all(25, "note_block");
		for (int m = 0; m < 16; m++) {
			set(26, m, "red_bed", "part", (m & 8) != 0 ? "head" : "foot");
		}
		all(27, "powered_rail");
		all(28, "detector_rail");
		all(29, "sticky_piston");
		all(30, "cobweb");
		set(31, 0, "dead_bush");
		set(31, 1, "short_grass");
		set(31, 2, "fern");
		set(31, 3, "short_grass");
		all(32, "dead_bush");
		all(33, "piston");
		all(34, "piston_head");
		all(36, "moving_piston");
		all(37, "dandelion");
		all(38, "poppy");
		all(39, "brown_mushroom");
		all(40, "red_mushroom");
		all(41, "gold_block");
		all(42, "iron_block");
		String[] slabs = {"smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab"};
		for (int m = 0; m < 16; m++) {
			set(43, m, slabs[m & 3], "type", "double");
			set(44, m, slabs[m & 3], "type", "bottom");
		}
		all(45, "bricks");
		all(46, "tnt");
		all(47, "bookshelf");
		all(48, "mossy_cobblestone");
		all(49, "obsidian");
		torch(50, "torch", "wall_torch", null);
		all(51, "fire");
		all(52, "spawner");
		all(53, "oak_stairs");
		all(54, "chest");
		for (int m = 0; m < 16; m++) {
			set(55, m, "redstone_wire", "power", Integer.toString(m));
			set(59, m, "wheat", "age", Integer.toString(Math.min(m, 7)));
			set(60, m, "farmland", "moisture", Integer.toString(Math.min(m, 7)));
			set(78, m, "snow", "layers", Integer.toString(Math.min((m & 7) + 1, 8)));
			set(92, m, "cake", "bites", Integer.toString(Math.min(m, 6)));
		}
		all(56, "diamond_ore");
		all(57, "diamond_block");
		all(58, "crafting_table");
		all(61, "furnace", "lit", "false");
		all(62, "furnace", "lit", "true");
		all(63, "oak_sign");
		all(64, "oak_door");
		all(65, "ladder");
		all(66, "rail");
		all(67, "cobblestone_stairs");
		all(68, "oak_wall_sign");
		all(69, "lever");
		all(70, "stone_pressure_plate");
		all(71, "iron_door");
		all(72, "oak_pressure_plate");
		all(73, "redstone_ore", "lit", "false");
		all(74, "redstone_ore", "lit", "true");
		torch(75, "redstone_torch", "redstone_wall_torch", "false");
		torch(76, "redstone_torch", "redstone_wall_torch", "true");
		all(77, "stone_button");
		all(79, "ice");
		all(80, "snow_block");
		all(81, "cactus");
		all(82, "clay");
		all(83, "sugar_cane");
		all(84, "jukebox");
		all(85, "oak_fence");
		all(86, "carved_pumpkin");
		all(87, "netherrack");
		all(88, "soul_sand");
		all(89, "glowstone");
		all(90, "nether_portal");
		all(91, "jack_o_lantern");
		all(93, "repeater", "powered", "false");
		all(94, "repeater", "powered", "true");
		all(95, "chest");
		all(96, "oak_trapdoor");
	}

	private void torch(int id, String standing, String wall, String lit) {
		for (int m = 0; m < 16; m++) {
			String name = m >= 1 && m <= 4 ? wall : standing;
			if (lit != null) {
				set(id, m, name, "lit", lit);
			} else {
				set(id, m, name);
			}
		}
	}

	private void all(int id, String name) {
		for (int m = 0; m < 16; m++) {
			set(id, m, name);
		}
	}

	private void all(int id, String name, String key, String value) {
		for (int m = 0; m < 16; m++) {
			set(id, m, name, key, value);
		}
	}

	private void set(int id, int meta, String name) {
		names[id << 4 | meta] = "minecraft:" + name;
	}

	private void set(int id, int meta, String name, String key, String value) {
		set(id, meta, name);
		states.put(id << 4 | meta, Map.of(key, value));
	}

	@Override
	public String name(int blockKey) {
		return blockKey >= 0 && blockKey < names.length ? names[blockKey] : null;
	}

	@Override
	public Map<String, String> properties(int blockKey) {
		return states.getOrDefault(blockKey, Map.of());
	}

	@Override
	public java.util.Set<String> tags(int blockKey) {
		return B173BlockTags.of(name(blockKey));
	}

	/** b1.7.3's items 256–359 in id order, by modern name. */
	private static final String[] ITEMS = {
			"iron_shovel", "iron_pickaxe", "iron_axe", "flint_and_steel", "apple", "bow", "arrow", "coal",
			"diamond", "iron_ingot", "gold_ingot", "iron_sword", "wooden_sword", "wooden_shovel", "wooden_pickaxe", "wooden_axe",
			"stone_sword", "stone_shovel", "stone_pickaxe", "stone_axe", "diamond_sword", "diamond_shovel", "diamond_pickaxe", "diamond_axe",
			"stick", "bowl", "mushroom_stew", "golden_sword", "golden_shovel", "golden_pickaxe", "golden_axe", "string",
			"feather", "gunpowder", "wooden_hoe", "stone_hoe", "iron_hoe", "diamond_hoe", "golden_hoe", "wheat_seeds",
			"wheat", "bread", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate",
			"chainmail_leggings", "chainmail_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate",
			"diamond_leggings", "diamond_boots", "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "flint", "porkchop",
			"cooked_porkchop", "painting", "golden_apple", "oak_sign", "oak_door", "bucket", "water_bucket", "lava_bucket",
			"minecart", "saddle", "iron_door", "redstone", "snowball", "oak_boat", "leather", "milk_bucket",
			"brick", "clay_ball", "sugar_cane", "paper", "book", "slime_ball", "chest_minecart", "furnace_minecart",
			"egg", "compass", "fishing_rod", "clock", "glowstone_dust", "cod", "cooked_cod", "dye",
			"bone", "sugar", "cake", "red_bed", "repeater", "cookie", "filled_map", "shears"};

	/** Dye by damage (b1.7.3's 16 dyes, ink sac to bone meal). */
	private static final String[] DYES = {"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye",
			"cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye",
			"magenta_dye", "orange_dye", "bone_meal"};

	@Override
	public String itemName(int itemId, int damage) {
		if (itemId > 0 && itemId < 256) {
			return name(itemId << 4 | damage & 15);
		}
		if (itemId == 2256 || itemId == 2257) {
			return itemId == 2256 ? "minecraft:music_disc_13" : "minecraft:music_disc_cat";
		}
		int i = itemId - 256;
		if (i < 0 || i >= ITEMS.length) {
			return null;
		}
		return switch (ITEMS[i]) {
			case "coal" -> damage == 1 ? "minecraft:charcoal" : "minecraft:coal";
			case "dye" -> "minecraft:" + DYES[damage & 15];
			default -> "minecraft:" + ITEMS[i];
		};
	}
}
