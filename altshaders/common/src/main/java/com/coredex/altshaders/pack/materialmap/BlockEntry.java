/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/materialmap/BlockEntry.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.materialmap;

import com.coredex.altshaders.pack.PackEnv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record BlockEntry(NamespacedId id, Map<String, String> propertyPredicates) implements Entry {

	/**
	 * Parses a block ID entry.
	 *
	 * @param entry The string representation of the entry. Must not be empty.
	 */
		public static Entry parse(String entry) {
		if (entry.isEmpty()) {
			throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
		}

		if (entry.startsWith("%")) {
			entry = entry.replace("%", "");
			// It's a tag!
			String[] splitStates = entry.split(":");

			if (splitStates.length == 1) {
				return new TagEntry(new NamespacedId("minecraft", entry), Map.of());
			} else if (splitStates.length == 2 && !splitStates[1].contains("=")) {
				return new TagEntry(new NamespacedId(splitStates[0], splitStates[1]), Map.of());
			} else {
				// Complex case: One or more states involved...
				int statesStart;
				NamespacedId id;

				if (splitStates[1].contains("=")) {
					// We have an entry of the form "tall_grass:half=upper"
					statesStart = 1;
					id = new NamespacedId("minecraft", splitStates[0]);
				} else {
					// We have an entry of the form "minecraft:tall_grass:half=upper"
					statesStart = 2;
					id = new NamespacedId(splitStates[0], splitStates[1]);
				}

				Map<String, String> map = new HashMap<>();

				for (int index = statesStart; index < splitStates.length; index++) {
					String[] propertyParts = splitStates[index].split("=");

					if (propertyParts.length != 2) {
						PackEnv.LOG.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
						PackEnv.LOG.warn("- Block state property filters must be of the form \"key=value\", but "
							+ splitStates[index] + " is not of that form!");

						continue;
					}

					map.put(propertyParts[0], propertyParts[1]);
				}

				return new TagEntry(id, map);
			}
		}

		// We can assume that this array is of at least array length because the input string is non-empty.
		String[] splitStates = entry.split(":");

		// Trivial case: no states, no namespace
		if (splitStates.length == 1) {
			return new BlockEntry(new NamespacedId("minecraft", entry), Collections.emptyMap());
		}

		// Less trivial case: no states involved, just a namespace
		//
		// The first term MUST be a valid Identifier component without an equals sign
		// The second term, if it does not contain an equals sign, must be a valid Identifier component.
		if (splitStates.length == 2 && !splitStates[1].contains("=")) {
			return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptyMap());
		}

		// Complex case: One or more states involved...
		int statesStart;
		NamespacedId id;

		if (splitStates[1].contains("=")) {
			// We have an entry of the form "tall_grass:half=upper"
			statesStart = 1;
			id = new NamespacedId("minecraft", splitStates[0]);
		} else {
			// We have an entry of the form "minecraft:tall_grass:half=upper"
			statesStart = 2;
			id = new NamespacedId(splitStates[0], splitStates[1]);
		}

		// We must parse each property key=value pair from the state entry.
		//
		// These pairs act as a filter on the block states. Thus, the shader pack does not have to specify all the
		// individual block properties itself; rather, it only specifies the parts of the block state that it wishes
		// to filter in/out.
		//
		// For example, some shader packs may make it so that hanging lantern blocks wave. They will put something of
		// the form "lantern:hanging=false" in the ID map as a result. Note, however, that there are also waterlogged
		// hanging lanterns, which would have "lantern:hanging=false:waterlogged=true". We must make sure that when the
		// shader pack author writes "lantern:hanging=false", that we do not just match that individual state, but that
		// we also match the waterlogged variant too.
		Map<String, String> map = new HashMap<>();

		for (int index = statesStart; index < splitStates.length; index++) {
			// Split "key=value" into the key and value
			String[] propertyParts = splitStates[index].split("=");

			if (propertyParts.length != 2) {
				PackEnv.LOG.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
				PackEnv.LOG.warn("- Block state property filters must be of the form \"key=value\", but "
					+ splitStates[index] + " is not of that form!");

				// Continue and ignore the invalid entry.
				continue;
			}

			map.put(propertyParts[0], propertyParts[1]);
		}

		return new BlockEntry(id, map);
	}


}
