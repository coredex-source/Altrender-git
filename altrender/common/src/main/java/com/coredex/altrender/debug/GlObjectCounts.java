/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Live GL object counts per kind, maintained by the strategies (the only code that creates or deletes GL objects).
 * Render thread only. Used by leak checks: a create/destroy round trip must return every count to its baseline.
 */
public final class GlObjectCounts {
	public enum Kind { BUFFER, TEXTURE, SAMPLER, FRAMEBUFFER, SYNC, QUERY, PROGRAM, SHADER, VERTEX_ARRAY }

	private final int[] counts = new int[Kind.values().length];

	public void created(Kind kind) {
		counts[kind.ordinal()]++;
	}

	public void deleted(Kind kind) {
		counts[kind.ordinal()]--;
	}

	public int get(Kind kind) {
		return counts[kind.ordinal()];
	}

	public Map<Kind, Integer> snapshot() {
		Map<Kind, Integer> out = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			out.put(kind, counts[kind.ordinal()]);
		}
		return out;
	}

	public static String describe(Map<Kind, Integer> snapshot) {
		StringJoiner sj = new StringJoiner(" ");
		snapshot.forEach((kind, n) -> {
			if (n != 0) {
				sj.add(kind.name().toLowerCase(java.util.Locale.ROOT) + "=" + n);
			}
		});
		return sj.length() == 0 ? "none" : sj.toString();
	}
}
