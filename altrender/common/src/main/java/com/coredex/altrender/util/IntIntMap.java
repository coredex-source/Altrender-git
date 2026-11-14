/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.util;

import java.util.Arrays;

/** An int → int hash map without boxing (open addressing, linear probing). Key 0 is reserved. Not thread-safe. */
public final class IntIntMap {

	private int[] keys = new int[16];
	private int[] values = new int[16];
	private int size;

	public int get(int key, int absent) {
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			int k = keys[i];
			if (k == key) {
				return values[i];
			}
			if (k == 0) {
				return absent;
			}
		}
	}

	public boolean containsKey(int key) {
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			int k = keys[i];
			if (k == key) {
				return true;
			}
			if (k == 0) {
				return false;
			}
		}
	}

	public void put(int key, int value) {
		if (key == 0) {
			throw new IllegalArgumentException("key 0 is reserved");
		}
		if ((size + 1) * 4 > keys.length * 3) {
			rehash(keys.length * 2);
		}
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			int k = keys[i];
			if (k == key) {
				values[i] = value;
				return;
			}
			if (k == 0) {
				keys[i] = key;
				values[i] = value;
				size++;
				return;
			}
		}
	}

	public void remove(int key) {
		int mask = keys.length - 1;
		int i = mix(key) & mask;
		while (keys[i] != key) {
			if (keys[i] == 0) {
				return;
			}
			i = (i + 1) & mask;
		}
		// Backward-shift deletion keeps every probe chain intact.
		int hole = i;
		for (int j = (i + 1) & mask; keys[j] != 0; j = (j + 1) & mask) {
			int home = mix(keys[j]) & mask;
			if (hole <= j ? home <= hole || home > j : home <= hole && home > j) {
				keys[hole] = keys[j];
				values[hole] = values[j];
				hole = j;
			}
		}
		keys[hole] = 0;
		values[hole] = 0;
		size--;
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	/** The keys, in no particular order (a copy). */
	public int[] keys() {
		int[] out = new int[size];
		int n = 0;
		for (int k : keys) {
			if (k != 0) {
				out[n++] = k;
			}
		}
		return out;
	}

	public void clear() {
		Arrays.fill(keys, 0);
		Arrays.fill(values, 0);
		size = 0;
	}

	private void rehash(int capacity) {
		int[] oldKeys = keys;
		int[] oldValues = values;
		keys = new int[capacity];
		values = new int[capacity];
		size = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldKeys[i] != 0) {
				put(oldKeys[i], oldValues[i]);
			}
		}
	}

	private static int mix(int key) {
		int h = key * 0x9E3779B9;
		return h ^ (h >>> 16);
	}
}
