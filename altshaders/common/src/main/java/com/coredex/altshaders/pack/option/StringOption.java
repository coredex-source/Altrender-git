/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/StringOption.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;

import java.util.Objects;

public class StringOption extends BaseOption {
	private final String defaultValue;
	private final List<String> allowedValues;

	private StringOption(OptionType type, String name, String defaultValue) {
		super(type, name, null);

		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.allowedValues = List.of(defaultValue);
	}

	private StringOption(OptionType type, String name, String comment, String defaultValue, List<String> allowedValues) {
		super(type, name, comment);

		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.allowedValues = allowedValues;
	}

		public static StringOption create(OptionType type, String name, String comment, String defaultValue) {
		if (comment == null) {
			return null;
		}

		int openingBracket = comment.indexOf('[');

		if (openingBracket == -1) {
			return null;
		}

		int closingBracket = comment.indexOf(']', openingBracket);

		if (closingBracket == -1) {
			return null;
		}

		String[] allowedValues = comment.substring(openingBracket + 1, closingBracket).split(" ");
		comment = comment.substring(0, openingBracket) + comment.substring(closingBracket + 1);
		boolean allowedValuesContainsDefaultValue = false;

		for (String value : allowedValues) {
			if (defaultValue.equals(value)) {
				allowedValuesContainsDefaultValue = true;
				break;
			}
		}

		ListBuilder<String> builder = new ListBuilder<>();

		builder.addAll(java.util.Arrays.asList(allowedValues));

		if (!allowedValuesContainsDefaultValue) {
			builder.add(defaultValue);
		}

		return new StringOption(type, name, comment.trim(), defaultValue, builder.build());
	}

		public String getDefaultValue() {
		return defaultValue;
	}

		public List<String> getAllowedValues() {
		return allowedValues;
	}
}
