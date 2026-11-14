/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramFallbackResolver.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.programs;

import com.coredex.altshaders.pack.loading.ProgramId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProgramFallbackResolver {
	private final ProgramSet programs;
	private final Map<ProgramId, ProgramSource> cache;

	public ProgramFallbackResolver(ProgramSet programs) {
		this.programs = programs;
		this.cache = new HashMap<>();
	}

	public Optional<ProgramSource> resolve(ProgramId id) {
		return Optional.ofNullable(resolveNullable(id));
	}

	public boolean has(ProgramId id) {
		return programs.get(id).isPresent();
	}

		public ProgramSource resolveNullable(ProgramId id) {
		if (cache.containsKey(id)) {
			return cache.get(id);
		}

		ProgramSource source = programs.get(id).orElse(null);

		if (source == null) {
			ProgramId fallback = id.getFallback().orElse(null);

			if (fallback != null) {
				source = resolveNullable(fallback);
			}
		}

		cache.put(id, source);
		return source;
	}
}
