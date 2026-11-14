/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/values/ImmutableOptionValues.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.values;

import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;
import com.coredex.altshaders.pack.helpers.OptionalBoolean;
import com.coredex.altshaders.pack.option.OptionSet;

import java.util.HashMap;
import java.util.Optional;

public class ImmutableOptionValues implements OptionValues {
	private final OptionSet options;
	private final Map<String, Boolean> booleanValues;
	private final Map<String, String> stringValues;

	ImmutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues,
						  Map<String, String> stringValues) {
		this.options = options;
		this.booleanValues = booleanValues;
		this.stringValues = stringValues;
	}

	@Override
	public OptionalBoolean getBooleanValue(String name) {
		if (booleanValues.containsKey(name)) {
			return booleanValues.get(name) ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
		} else {
			return OptionalBoolean.DEFAULT;
		}
	}

	@Override
	public Optional<String> getStringValue(String name) {
		return Optional.ofNullable(stringValues.get(name));
	}

	@Override
	public int getOptionsChanged() {
		return this.stringValues.size() + this.booleanValues.size();
	}

	@Override
	public MutableOptionValues mutableCopy() {
		return new MutableOptionValues(options, new HashMap<>(booleanValues), new HashMap<>(stringValues));
	}

	@Override
	public ImmutableOptionValues toImmutable() {
		return this;
	}

	@Override
	public OptionSet getOptionSet() {
		return options;
	}
}
