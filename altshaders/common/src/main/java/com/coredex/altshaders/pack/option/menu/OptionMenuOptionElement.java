/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuOptionElement.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.option.values.MutableOptionValues;
import com.coredex.altshaders.pack.option.values.OptionValues;
import com.coredex.altshaders.pack.properties.ShaderProperties;

public abstract class OptionMenuOptionElement extends OptionMenuElement {
	public final boolean slider;
	public final OptionMenuContainer container;
	public final String optionId;

	private final OptionValues packAppliedValues;

	public OptionMenuOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues packAppliedValues) {
		this.slider = shaderProperties.getSliderOptions().contains(elementString);
		this.container = container;
		this.optionId = elementString;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return the {@link OptionValues} currently in use by the shader pack
	 */
	public OptionValues getAppliedOptionValues() {
		return packAppliedValues;
	}

	/**
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues() {
		MutableOptionValues values = getAppliedOptionValues().mutableCopy();
		values.addAll(PackEnv.optionQueue());

		return values;
	}
}
