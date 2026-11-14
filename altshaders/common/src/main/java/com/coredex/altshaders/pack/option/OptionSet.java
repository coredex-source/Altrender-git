/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/OptionSet.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import com.coredex.altshaders.pack.PackEnv;
import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;

import java.util.HashMap;
import java.util.Map;

public class OptionSet {
	private final Map<String, MergedBooleanOption> booleanOptions;
	private final Map<String, MergedStringOption> stringOptions;

	private OptionSet(Builder builder) {
		this.booleanOptions = Map.copyOf(builder.booleanOptions);
		this.stringOptions = Map.copyOf(builder.stringOptions);
	}

	public static Builder builder() {
		return new Builder();
	}

	public Map<String, MergedBooleanOption> getBooleanOptions() {
		return this.booleanOptions;
	}

	public Map<String, MergedStringOption> getStringOptions() {
		return this.stringOptions;
	}

	public boolean isBooleanOption(String name) {
		return booleanOptions.containsKey(name);
	}

	public static class Builder {
		private final Map<String, MergedBooleanOption> booleanOptions;
		private final Map<String, MergedStringOption> stringOptions;

		public Builder() {
			this.booleanOptions = new HashMap<>();
			this.stringOptions = new HashMap<>();
		}

		public void addAll(OptionSet other) {
			if (this.booleanOptions.isEmpty()) {
				this.booleanOptions.putAll(other.booleanOptions);
			} else {
				other.booleanOptions.values().forEach(this::addBooleanOption);
			}

			if (this.stringOptions.isEmpty()) {
				this.stringOptions.putAll(other.stringOptions);
			} else {
				other.stringOptions.values().forEach(this::addStringOption);
			}
		}

		public void addBooleanOption(OptionLocation location, BooleanOption option) {
			addBooleanOption(new MergedBooleanOption(location, option));
		}

		public void addBooleanOption(MergedBooleanOption proposed) {
			BooleanOption option = proposed.getOption();
			MergedBooleanOption existing = booleanOptions.get(option.getName());

			MergedBooleanOption merged;

			if (existing != null) {
				merged = existing.merge(proposed);

				if (merged == null) {
					// TODO: Warn about ambiguous options better
					PackEnv.LOG.warn("Ignoring ambiguous boolean option " + option.getName());
					booleanOptions.remove(option.getName());
					return;
				}
			} else {
				merged = proposed;
			}

			booleanOptions.put(option.getName(), merged);
		}

		public void addStringOption(OptionLocation location, StringOption option) {
			addStringOption(new MergedStringOption(location, option));
		}

		public void addStringOption(MergedStringOption proposed) {
			StringOption option = proposed.getOption();
			MergedStringOption existing = stringOptions.get(option.getName());

			MergedStringOption merged;

			if (existing != null) {
				merged = existing.merge(proposed);

				if (merged == null) {
					// TODO: Warn about ambiguous options better
					PackEnv.LOG.warn("Ignoring ambiguous string option " + option.getName());
					stringOptions.remove(option.getName());
					return;
				}
			} else {
				merged = proposed;
			}

			stringOptions.put(option.getName(), merged);
		}

		public OptionSet build() {
			return new OptionSet(this);
		}
	}
}
