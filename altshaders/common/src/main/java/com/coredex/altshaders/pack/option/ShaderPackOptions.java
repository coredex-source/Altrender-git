/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/ShaderPackOptions.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;
import com.coredex.altshaders.pack.include.AbsolutePackPath;
import com.coredex.altshaders.pack.include.IncludeGraph;
import com.coredex.altshaders.pack.option.values.MutableOptionValues;
import com.coredex.altshaders.pack.option.values.OptionValues;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A helper class that dispatches all the heavy lifting needed to discover, merge, and apply shader pack options to
 * an existing {@link IncludeGraph}.
 */
public class ShaderPackOptions {
	private final OptionSet optionSet;
	private final OptionValues optionValues;
	private final IncludeGraph includes;

	public ShaderPackOptions(IncludeGraph graph, Map<String, String> changedConfigs) {
		Map<AbsolutePackPath, OptionAnnotatedSource> allAnnotations = new HashMap<>();
		OptionSet.Builder setBuilder = OptionSet.builder();

		graph.computeWeaklyConnectedComponents().forEach(subgraph -> {
			MapBuilder<AbsolutePackPath, OptionAnnotatedSource> annotationBuilder = new MapBuilder<>();
			Set<String> referencedBooleanDefines = new HashSet<>();

			subgraph.getNodes().forEach((path, node) -> {
				OptionAnnotatedSource annotatedSource = new OptionAnnotatedSource(node.getLines());
				annotationBuilder.put(path, annotatedSource);
				referencedBooleanDefines.addAll(annotatedSource.getBooleanDefineReferences().keySet());
			});

			Map<AbsolutePackPath, OptionAnnotatedSource> annotations = annotationBuilder.build();
			Set<String> referencedBooleanDefinesU = Collections.unmodifiableSet(referencedBooleanDefines);

			annotations.forEach((path, annotatedSource) -> {
				OptionSet set = annotatedSource.getOptionSet(path, referencedBooleanDefinesU);
				setBuilder.addAll(set);
			});

			allAnnotations.putAll(annotations);
		});

		this.optionSet = setBuilder.build();
		this.optionValues = new MutableOptionValues(optionSet, changedConfigs);

		this.includes = graph.map(path -> allAnnotations.get(path).asTransform(optionValues));
	}

	public OptionSet getOptionSet() {
		return optionSet;
	}

	public OptionValues getOptionValues() {
		return optionValues;
	}

	public IncludeGraph getIncludes() {
		return includes;
	}
}
