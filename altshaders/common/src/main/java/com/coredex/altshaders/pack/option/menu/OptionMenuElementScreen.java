/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuElementScreen.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.option.ShaderPackOptions;
import com.coredex.altshaders.pack.properties.ShaderProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OptionMenuElementScreen {
	public final List<OptionMenuElement> elements = new ArrayList<>();

	private final Optional<Integer> columnCount;

	public OptionMenuElementScreen(OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, List<String> elementStrings, Optional<Integer> columnCount) {
		this.columnCount = columnCount;

		for (String elementString : elementStrings) {
			if ("*".equals(elementString)) {
				container.queueForUnusedOptionDump(this.elements.size(), this.elements);

				continue;
			}

			try {
				OptionMenuElement element = OptionMenuElement.create(elementString, container, shaderProperties, shaderPackOptions);

				if (element != null) {
					this.elements.add(element);

					if (element instanceof OptionMenuOptionElement) {
						container.notifyOptionAdded(elementString, (OptionMenuOptionElement) element);
					}
				}
			} catch (IllegalArgumentException error) {
				PackEnv.LOG.warn(error.getMessage());

				this.elements.add(OptionMenuElement.EMPTY);
			}
		}
	}

	public int getColumnCount() {
		return columnCount.orElse(elements.size() > 18 ? 3 : 2);
	}
}
