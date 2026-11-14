/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/include/IncludeProcessor.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.include;

import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;
import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// TODO: Write tests for this code
public class IncludeProcessor {
	private final IncludeGraph graph;
	private final Map<AbsolutePackPath, List<String>> cache;

	public IncludeProcessor(IncludeGraph graph) {
		this.graph = graph;
		this.cache = new HashMap<>();
	}

	// TODO: Actual error handling

	public List<String> getIncludedFile(AbsolutePackPath path) {
		List<String> lines = cache.get(path);

		if (lines == null) {
			lines = process(path);
			cache.put(path, lines);
		}

		return lines;
	}

	private List<String> process(AbsolutePackPath path) {
		FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return null;
		}

		ListBuilder<String> builder = new ListBuilder<>();

		List<String> lines = fileNode.getLines();
		Map<Integer, AbsolutePackPath> includes = fileNode.getIncludes();

		for (int i = 0; i < lines.size(); i++) {
			AbsolutePackPath include = includes.get(i);

			if (include != null) {
				// TODO: Don't recurse like this, and check for cycles
				// TODO: Better diagnostics
				builder.addAll(Objects.requireNonNull(getIncludedFile(include)));
			} else {
				builder.add(lines.get(i));
			}
		}

		return builder.build();
	}
}
