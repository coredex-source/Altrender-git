/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/include/FileNode.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.include;

import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;
import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;
import com.coredex.altshaders.pack.transform.line.LineTransform;

import java.util.Objects;

public class FileNode {
	private final AbsolutePackPath path;
	private final List<String> lines;
	private final Map<Integer, AbsolutePackPath> includes;

	// NB: The caller is responsible for ensuring that the includes map
	//     is in sync with the lines list.
	private FileNode(AbsolutePackPath path, List<String> lines,
					 Map<Integer, AbsolutePackPath> includes) {
		this.path = path;
		this.lines = lines;
		this.includes = includes;
	}

	public FileNode(AbsolutePackPath path, List<String> lines) {
		this.path = path;
		this.lines = lines;

		AbsolutePackPath currentDirectory = path.parent().orElseThrow(
			() -> new IllegalArgumentException("Not a valid shader file name: " + path));

		this.includes = findIncludes(currentDirectory, lines);
	}

	private static Map<Integer, AbsolutePackPath> findIncludes(AbsolutePackPath currentDirectory,
																		List<String> lines) {
		MapBuilder<Integer, AbsolutePackPath> foundIncludes = new MapBuilder<>();

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();

			if (!line.startsWith("#include")) {
				continue;
			}

			// Remove the "#include " part so that we just have the file path
			String target = line.substring("#include ".length()).trim();

			// Remove quotes if they're present
			// All include directives should have quotes, but I'm not sure whether they're required to.
			// TODO: Check if quotes are required, and don't permit mismatched quotes
			// TODO: This shouldn't be accepted:
			//       #include "test.glsl
			//       #include test.glsl"
			if (target.startsWith("\"")) {
				target = target.substring(1);
			}

			if (target.endsWith("\"")) {
				target = target.substring(0, target.length() - 1);
			}

			foundIncludes.put(i, currentDirectory.resolve(target));
		}

		return foundIncludes.build();
	}

	public AbsolutePackPath getPath() {
		return path;
	}

	public List<String> getLines() {
		return lines;
	}

	public Map<Integer, AbsolutePackPath> getIncludes() {
		return includes;
	}

	public FileNode map(LineTransform transform) {
		ListBuilder<String> newLines = new ListBuilder<>();
		int index = 0;

		for (String line : lines) {
			String transformedLine = transform.transform(index, line);

			if (includes.containsKey(index)) {
				if (!Objects.equals(line, transformedLine)) {
					throw new IllegalStateException("Attempted to modify an #include line in LineTransform.");
				}
			}

			newLines.add(transformedLine);
			index += 1;
		}

		return new FileNode(path, newLines.build(), includes);
	}
}
