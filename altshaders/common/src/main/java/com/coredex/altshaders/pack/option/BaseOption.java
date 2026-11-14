/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/BaseOption.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;


import java.util.Optional;

public abstract class BaseOption {
		private final OptionType type;
		private final String name;
		private final String comment;

	BaseOption(OptionType type, String name, String comment) {
		this.type = type;
		this.name = name;

		if (comment == null || comment.isEmpty()) {
			this.comment = null;
		} else {
			this.comment = comment;
		}
	}

		public OptionType getType() {
		return type;
	}

		public String getName() {
		return name;
	}

	public Optional<String> getComment() {
		return Optional.ofNullable(comment);
	}
}
