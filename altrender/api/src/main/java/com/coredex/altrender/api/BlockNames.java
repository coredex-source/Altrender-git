/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

import java.util.Map;

/**
 * The running version's blocks by modern name (since 0.3.0, development.md 6.1/6.6): shader packs list blocks as
 * {@code minecraft:oak_leaves}, old versions number them. A block key is the version's own ({@code id << 4 | meta}
 * where blocks have metadata).
 */
public interface BlockNames {

	/** The block's modern name ({@code minecraft:wheat}), or {@code null} for none (air, unknown ids). */
	String name(int blockKey);

	/** Its state in modern terms ({@code age=7}, {@code lit=true}); empty when it has none worth matching. */
	Map<String, String> properties(int blockKey);

	/**
	 * The modern block tags the block is in ({@code minecraft:leaves}, {@code minecraft:small_flowers}), for packs'
	 * {@code %tag} entries; empty when the version gives none. Since 0.3.0.
	 */
	default java.util.Set<String> tags(int blockKey) {
		return java.util.Set.of();
	}

	/**
	 * An item's modern name ({@code minecraft:torch}, {@code minecraft:iron_pickaxe}) from the version's item id and
	 * damage, or {@code null} for none. Shader packs' item.properties list items by these names.
	 */
	default String itemName(int itemId, int damage) {
		return null;
	}
}
