/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Collects entries, in order, into an unmodifiable map (in place of Guava's {@code ImmutableMap.Builder}). */
public final class MapBuilder<K, V> {
	private final Map<K, V> items = new LinkedHashMap<>();

	public MapBuilder<K, V> put(K key, V value) {
		if (items.putIfAbsent(key, value) != null) {
			throw new IllegalArgumentException("duplicate key " + key);
		}
		return this;
	}

	public MapBuilder<K, V> putAll(Map<? extends K, ? extends V> all) {
		all.forEach(this::put);
		return this;
	}

	public Map<K, V> build() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(items));
	}
}
