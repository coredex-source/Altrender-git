/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/include/ShaderPackSourceNames.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.include;

import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;
import com.coredex.altshaders.pack.loading.ProgramArrayId;
import com.coredex.altshaders.pack.loading.ProgramId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enumerates the possible program source file names to
 */
public class ShaderPackSourceNames {
	public static final List<String> POTENTIAL_STARTS = findPotentialStarts();

	public static boolean findPresentSources(ListBuilder<AbsolutePackPath> starts, Path packRoot,
											 AbsolutePackPath directory, List<String> candidates) throws IOException {
		Path directoryPath = directory.resolved(packRoot);

		if (!Files.exists(directoryPath)) {
			return false;
		}

		boolean anyFound = false;

		Set<String> found;
		try (Stream<Path> stream = Files.list(directoryPath)) {
			found = stream
				.map(path -> path.getFileName().toString())
				.collect(Collectors.toSet());
		}

		for (String candidate : candidates) {
			if (found.contains(candidate)) {
				starts.add(directory.resolve(candidate));
				anyFound = true;
			}
		}

		return anyFound;
	}

	private static List<String> findPotentialStarts() {
		ListBuilder<String> potentialFileNames = new ListBuilder<>();

		// TODO: Iterate over program groups for exact iteration order.
		for (ProgramArrayId programArrayId : ProgramArrayId.values()) {
			for (int i = 0; i < programArrayId.getNumPrograms(); i++) {
				String name = programArrayId.getSourcePrefix();
				String suffix = "";

				if (i > 0) {
					suffix = Integer.toString(i);
				}

				addComputeStarts(potentialFileNames, name + suffix);
			}
		}

		for (ProgramId programId : ProgramId.values()) {
			if (programId == ProgramId.Final || programId == ProgramId.Shadow) {
				addComputeStarts(potentialFileNames, programId.getSourceName());
			} else {
				addStarts(potentialFileNames, programId.getSourceName());
			}
		}

		return potentialFileNames.build();
	}

	private static void addStarts(ListBuilder<String> potentialFileNames, String baseName) {
		potentialFileNames.add(baseName + ".vsh");
		potentialFileNames.add(baseName + ".tcs");
		potentialFileNames.add(baseName + ".tes");
		potentialFileNames.add(baseName + ".gsh");
		potentialFileNames.add(baseName + ".fsh");
	}

	private static void addComputeStarts(ListBuilder<String> potentialFileNames, String baseName) {
		addStarts(potentialFileNames, baseName);

		for (int j = 0; j < 27; j++) {
			String suffix2;

			if (j == 0) {
				suffix2 = "";
			} else {
				char letter = (char) ('a' + j - 1);
				suffix2 = "_" + letter;
			}

			potentialFileNames.add(baseName + suffix2 + ".csh");
		}
	}
}
