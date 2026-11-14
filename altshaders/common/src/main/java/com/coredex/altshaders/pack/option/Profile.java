/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/Profile.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;
import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;
import com.coredex.altshaders.pack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Profile {
	public final String name;
	public final int precedence; // Used for prioritizing during matching
	public final Map<String, String> optionValues;
	public final List<String> disabledPrograms;

	private Profile(String name, Map<String, String> optionValues, List<String> disabledPrograms) {
		this.name = name;
		this.optionValues = optionValues;
		this.precedence = optionValues.size();
		this.disabledPrograms = disabledPrograms;
	}

	public boolean matches(OptionSet options, OptionValues values) {
		for (Map.Entry<String, String> entry : this.optionValues.entrySet()) {
			String option = entry.getKey();
			String value = entry.getValue();

			if (options.getBooleanOptions().containsKey(option)) {
				boolean currentValue = values.getBooleanValueOrDefault(option);

				if (!Boolean.toString(currentValue).equals(value)) {
					return false;
				}
			}
			if (options.getStringOptions().containsKey(option)) {
				String currentValue = values.getStringValueOrDefault(option);

				if (!value.equals(currentValue)) {
					return false;
				}
			}
		}

		return true;
	}

	public static class Builder {
		private final String name;
		private final Map<String, String> optionValues = new HashMap<>();
		private final List<String> disabledPrograms = new ArrayList<>();

		public Builder(String name) {
			this.name = name;
		}

		public Builder option(String optionId, String value) {
			this.optionValues.put(optionId, value);

			return this;
		}

		public Builder disableProgram(String programId) {
			this.disabledPrograms.add(programId);

			return this;
		}

		public Builder addAll(Profile other) {
			this.optionValues.putAll(other.optionValues);
			this.disabledPrograms.addAll(other.disabledPrograms);

			return this;
		}

		public Profile build() {
			return new Profile(name, Map.copyOf(optionValues), List.copyOf(disabledPrograms));
		}
	}
}
