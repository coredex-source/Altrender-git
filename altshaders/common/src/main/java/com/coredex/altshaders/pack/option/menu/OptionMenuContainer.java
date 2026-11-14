/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuContainer.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.PackEnv;
import java.util.ArrayList;
import com.coredex.altshaders.pack.option.ProfileSet;
import com.coredex.altshaders.pack.option.ShaderPackOptions;
import com.coredex.altshaders.pack.properties.ShaderProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OptionMenuContainer {
	public final OptionMenuElementScreen mainScreen;
	public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();

	private final List<OptionMenuOptionElement> usedOptionElements = new ArrayList<>();
	private final List<String> usedOptions = new ArrayList<>();
	private final List<String> unusedOptions = new ArrayList<>(); // To be used when screens contain a "*" element
	private final Map<List<OptionMenuElement>, Integer> unusedOptionDumpQueue = new HashMap<>(); // Used by screens with "*" element
	private final ProfileSet profiles;

	public OptionMenuContainer(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles) {
		this.profiles = profiles;

		// note: if the Shader Pack does not provide a list of options for the main screen, then dump all options on to
		// the main screen by default.
		this.mainScreen = new OptionMenuMainElementScreen(
			this, shaderProperties, shaderPackOptions,
			shaderProperties.getMainScreenOptions().orElseGet(() -> Collections.singletonList("*")),
			shaderProperties.getMainScreenColumnCount());

		this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getBooleanOptions().keySet());
		this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getStringOptions().keySet());

		Map<String, Integer> subScreenColumnCounts = shaderProperties.getSubScreenColumnCount();
		shaderProperties.getSubScreenOptions().forEach((screenKey, options) -> subScreens.put(screenKey, new OptionMenuSubElementScreen(
			screenKey, this, shaderProperties, shaderPackOptions, options, Optional.ofNullable(subScreenColumnCounts.get(screenKey)))));

		// Dump all unused options into screens containing "*"
		for (Map.Entry<List<OptionMenuElement>, Integer> entry : unusedOptionDumpQueue.entrySet()) {
			List<OptionMenuElement> elementsToInsert = new ArrayList<>();
			List<String> unusedOptionsCopy = new ArrayList<>(this.unusedOptions);

			for (String optionId : unusedOptionsCopy) {
				try {
					OptionMenuElement element = OptionMenuElement.create(optionId, this, shaderProperties, shaderPackOptions);
					if (element != null) {
						elementsToInsert.add(element);

						if (element instanceof OptionMenuOptionElement) {
							this.notifyOptionAdded(optionId, (OptionMenuOptionElement) element);
						}
					}
				} catch (IllegalArgumentException error) {
					PackEnv.LOG.warn(String.valueOf(error.getMessage()));

					elementsToInsert.add(OptionMenuElement.EMPTY);
				}
			}

			entry.getKey().addAll(entry.getValue(), elementsToInsert);
		}
	}

	public ProfileSet getProfiles() {
		return profiles;
	}

	// Screens will call this when they contain a "*" element, so that the list of
	// unused options can be added after all other screens have been resolved
	public void queueForUnusedOptionDump(int index, List<OptionMenuElement> elementList) {
		this.unusedOptionDumpQueue.put(elementList, index);
	}

	public void notifyOptionAdded(String optionId, OptionMenuOptionElement option) {
		if (!usedOptions.contains(optionId)) {
			usedOptionElements.add(option);
			usedOptions.add(optionId);
		}

		unusedOptions.remove(optionId);
	}
}
