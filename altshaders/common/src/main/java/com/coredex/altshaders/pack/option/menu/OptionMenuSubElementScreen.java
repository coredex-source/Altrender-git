/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuSubElementScreen.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.option.ShaderPackOptions;
import com.coredex.altshaders.pack.properties.ShaderProperties;

import java.util.List;
import java.util.Optional;

public class OptionMenuSubElementScreen extends OptionMenuElementScreen {
	public final String screenId;

	public OptionMenuSubElementScreen(String screenId, OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, List<String> elementStrings, Optional<Integer> columnCount) {
		super(container, shaderProperties, shaderPackOptions, elementStrings, columnCount);

		this.screenId = screenId;
	}
}
