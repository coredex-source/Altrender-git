/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/transform/line/LineTransform.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.transform.line;

import java.util.ArrayList;
import java.util.List;

public interface LineTransform {
	static List<String> apply(List<String> lines, LineTransform transform) {
		List<String> newLines = new ArrayList<>(lines.size());
		int index = 0;

		for (String line : lines) {
			newLines.add(transform.transform(index, line));
			index += 1;
		}

		return List.copyOf(newLines);
	}

	String transform(int index, String line);
}
