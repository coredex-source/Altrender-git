/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuElement.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.option.MergedBooleanOption;
import com.coredex.altshaders.pack.option.MergedStringOption;
import com.coredex.altshaders.pack.option.ShaderPackOptions;
import com.coredex.altshaders.pack.properties.ShaderProperties;

import java.util.Map;

public abstract class OptionMenuElement {
	public static final OptionMenuElement EMPTY = new OptionMenuElement() {
	};

	private static final String ELEMENT_EMPTY = "<empty>";
	private static final String ELEMENT_PROFILE = "<profile>";

	public static OptionMenuElement create(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions) throws IllegalArgumentException {
		// Empty element
		if (ELEMENT_EMPTY.equals(elementString)) {
			return EMPTY;
		}
		// Profile element
		// NB: We don't want to add a profile element if there aren't profiles, even if it's requested. TODO: add this to diagnostics? (lp)
		if (ELEMENT_PROFILE.equals(elementString)) {
			// null indicates that the element should be forgotten (not treated as empty)
			return container.getProfiles().size() > 0 ? new OptionMenuProfileElement(container.getProfiles(), shaderPackOptions.getOptionSet(), shaderPackOptions.getOptionValues()) : null;
		}
		// Link to sub screen element
		if (elementString.startsWith("[") && elementString.endsWith("]")) {
			return new OptionMenuLinkElement(elementString.substring(1, elementString.length() - 1));
		}

		Map<String, MergedBooleanOption> booleanOptions = shaderPackOptions.getOptionSet().getBooleanOptions();
		Map<String, MergedStringOption> stringOptions = shaderPackOptions.getOptionSet().getStringOptions();

		// Option elements (boolean and string), only succeed if the option is defined in the shader source
		if (booleanOptions.containsKey(elementString)) {
			return new OptionMenuBooleanOptionElement(elementString, container, shaderProperties, shaderPackOptions.getOptionValues(), booleanOptions.get(elementString).getOption());
		} else if (stringOptions.containsKey(elementString)) {
			return new OptionMenuStringOptionElement(elementString, container, shaderProperties, shaderPackOptions.getOptionValues(), stringOptions.get(elementString).getOption());
		}

		// Handled and ignored with log warning
		throw new IllegalArgumentException("Unable to resolve shader pack option menu element \"" + elementString + "\" defined in shaders.properties");
	}
}
