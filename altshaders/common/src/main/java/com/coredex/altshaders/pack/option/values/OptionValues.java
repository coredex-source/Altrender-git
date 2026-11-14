/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/values/OptionValues.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.values;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.helpers.OptionalBoolean;
import com.coredex.altshaders.pack.option.OptionSet;

import java.util.Optional;

public interface OptionValues {
	OptionalBoolean getBooleanValue(String name);

	Optional<String> getStringValue(String name);

	default boolean getBooleanValueOrDefault(String name) {
		if ("0".equals(name)) {
			return false;
		} else if ("1".equals(name)) {
			return true;
		}

		return getBooleanValue(name).orElseGet(() -> {
			if (!getOptionSet().getBooleanOptions().containsKey(name)) {
				PackEnv.LOG.warn("Tried to get boolean value for unknown option: " + name + ", defaulting to true!");
				return true;
			}
			return getOptionSet().getBooleanOptions().get(name).getOption().getDefaultValue();
		});
	}

	default String getStringValueOrDefault(String name) {
		return getStringValue(name).orElseGet(() -> getOptionSet().getStringOptions().get(name).getOption().getDefaultValue());
	}

	int getOptionsChanged();

	MutableOptionValues mutableCopy();

	ImmutableOptionValues toImmutable();

	OptionSet getOptionSet();
}
