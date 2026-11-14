/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Collects elements into an unmodifiable list (in place of Guava's {@code ImmutableList.Builder} in ported code). */
public final class ListBuilder<T> {
	private final List<T> items = new ArrayList<>();

	public ListBuilder<T> add(T item) {
		items.add(item);
		return this;
	}

	public ListBuilder<T> addAll(Collection<? extends T> all) {
		items.addAll(all);
		return this;
	}

	public List<T> build() {
		return List.copyOf(items);
	}
}
