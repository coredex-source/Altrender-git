/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/parsing/ComputeDirectiveParser.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.parsing;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.programs.ComputeSource;
import com.coredex.altshaders.pack.helpers.Vector2f;
import com.coredex.altshaders.pack.helpers.Vector3i;

public class ComputeDirectiveParser {
	public static void setComputeWorkGroups(ComputeSource source, ConstDirectiveParser.ConstDirective directive) {
		if (!directive.getValue().startsWith("ivec3")) {
			PackEnv.LOG.error("Failed to process " + directive + ": value was not a valid ivec3 constructor");
		}

		String ivec3Args = directive.getValue().substring("ivec3".length()).trim();

		if (!ivec3Args.startsWith("(") || !ivec3Args.endsWith(")")) {
			PackEnv.LOG.error("Failed to process " + directive + ": value was not a valid ivec3 constructor");
		}

		ivec3Args = ivec3Args.substring(1, ivec3Args.length() - 1);

		String[] parts = ivec3Args.split(",");

		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}

		if (parts.length != 3) {
			PackEnv.LOG.error("Failed to process " + directive +
				": expected 3 arguments to a ivec3 constructor, got " + parts.length);
		}

		try {
			source.setWorkGroups(new Vector3i(
				Integer.parseInt(parts[0]),
				Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2])));
		} catch (NumberFormatException e) {
			PackEnv.LOG.error("Failed to process " + directive, e);
		}
	}

	public static void setComputeWorkGroupsRelative(ComputeSource source, ConstDirectiveParser.ConstDirective directive) {
		if (!directive.getValue().startsWith("vec2")) {
			PackEnv.LOG.error("Failed to process " + directive + ": value was not a valid vec2 constructor");
		}

		String vec2Args = directive.getValue().substring("vec2".length()).trim();

		if (!vec2Args.startsWith("(") || !vec2Args.endsWith(")")) {
			PackEnv.LOG.error("Failed to process " + directive + ": value was not a valid vec2 constructor");
		}

		vec2Args = vec2Args.substring(1, vec2Args.length() - 1);

		String[] parts = vec2Args.split(",");

		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}

		if (parts.length != 2) {
			PackEnv.LOG.error("Failed to process " + directive +
				": expected 2 arguments to a vec2 constructor, got " + parts.length);
		}

		try {
			source.setWorkGroupRelative(new Vector2f(
				Float.parseFloat(parts[0]),
				Float.parseFloat(parts[1])
			));
		} catch (NumberFormatException e) {
			PackEnv.LOG.error("Failed to process " + directive, e);
		}
	}
}
