/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A long → object hash map without boxing (open addressing, linear probing; null values aren't stored). For per-frame
 * lookups, where {@code Map<Long, V>} allocates a key each time. Not thread-safe.
 */
public final class LongMap<V> {

	private long[] keys = new long[16];
	private Object[] values = new Object[16];
	private int size;

	@SuppressWarnings("unchecked")
	public V get(long key) {
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			Object v = values[i];
			if (v == null) {
				return null;
			}
			if (keys[i] == key) {
				return (V) v;
			}
		}
	}

	public boolean containsKey(long key) {
		return get(key) != null;
	}

	public void put(long key, V value) {
		if (value == null) {
			throw new IllegalArgumentException("null value");
		}
		if ((size + 1) * 4 > keys.length * 3) {
			rehash(keys.length * 2);
		}
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			if (values[i] == null) {
				keys[i] = key;
				values[i] = value;
				size++;
				return;
			}
			if (keys[i] == key) {
				values[i] = value;
				return;
			}
		}
	}

	public void remove(long key) {
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; values[i] != null; i = (i + 1) & mask) {
			if (keys[i] == key) {
				removeAt(i);
				return;
			}
		}
	}

	/** Removes every value the filter accepts (it may be asked more than once about a value it keeps). */
	@SuppressWarnings("unchecked")
	public void removeIf(Predicate<? super V> filter) {
		for (int i = 0; i < values.length; ) {
			Object v = values[i];
			if (v != null && filter.test((V) v)) {
				removeAt(i); // a later entry may shift into i: look again
			} else {
				i++;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super V> action) {
		for (Object v : values) {
			if (v != null) {
				action.accept((V) v);
			}
		}
	}

	/** The values, in no particular order; the map mustn't change during the iteration. */
	public Iterable<V> values() {
		return () -> new Iterator<>() {
			private int next = advance(0);

			private int advance(int from) {
				while (from < values.length && values[from] == null) {
					from++;
				}
				return from;
			}

			@Override
			public boolean hasNext() {
				return next < values.length;
			}

			@Override
			@SuppressWarnings("unchecked")
			public V next() {
				if (next >= values.length) {
					throw new NoSuchElementException();
				}
				V v = (V) values[next];
				next = advance(next + 1);
				return v;
			}
		};
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public void clear() {
		Arrays.fill(values, null);
		size = 0;
	}

	/** Backward-shift deletion keeps every probe chain intact. */
	private void removeAt(int i) {
		int mask = keys.length - 1;
		int hole = i;
		for (int j = (i + 1) & mask; values[j] != null; j = (j + 1) & mask) {
			int home = mix(keys[j]) & mask;
			if (hole <= j ? home <= hole || home > j : home <= hole && home > j) {
				keys[hole] = keys[j];
				values[hole] = values[j];
				hole = j;
			}
		}
		values[hole] = null;
		size--;
	}

	@SuppressWarnings("unchecked")
	private void rehash(int capacity) {
		long[] oldKeys = keys;
		Object[] oldValues = values;
		keys = new long[capacity];
		values = new Object[capacity];
		size = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldValues[i] != null) {
				put(oldKeys[i], (V) oldValues[i]);
			}
		}
	}

	private static int mix(long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h ^ (h >>> 32) ^ (h >>> 17));
	}
}
