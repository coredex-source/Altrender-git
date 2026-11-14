/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/MergedStringOption.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class MergedStringOption {
	private final StringOption option;
	private final Set<OptionLocation> locations;

	MergedStringOption(StringOption option, Set<OptionLocation> locations) {
		this.option = option;
		this.locations = locations;
	}

	public MergedStringOption(OptionLocation location, StringOption option) {
		this.option = option;
		this.locations = Set.of(location);
	}

		public MergedStringOption merge(MergedStringOption other) {
		if (!this.option.getDefaultValue().equals(other.option.getDefaultValue())) {
			return null;
		}

		StringOption option;

		// TODO: Collect all known comments
		if (this.option.getComment().isPresent()) {
			option = this.option;
		} else {
			option = other.option;
		}

		Set<OptionLocation> mergedLocations = new LinkedHashSet<>();

		mergedLocations.addAll(this.locations);
		mergedLocations.addAll(other.locations);

		return new MergedStringOption(option, Collections.unmodifiableSet(mergedLocations));
	}

	public StringOption getOption() {
		return option;
	}

	public Set<OptionLocation> getLocations() {
		return locations;
	}
}
