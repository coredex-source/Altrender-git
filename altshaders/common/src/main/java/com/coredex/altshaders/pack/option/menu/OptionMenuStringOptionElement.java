/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuStringOptionElement.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.option.StringOption;
import com.coredex.altshaders.pack.option.values.OptionValues;
import com.coredex.altshaders.pack.properties.ShaderProperties;

public class OptionMenuStringOptionElement extends OptionMenuOptionElement {
	public final StringOption option;

	public OptionMenuStringOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, StringOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
