/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.ArrayList;
import java.util.List;

/** Options shown together on a page, with a gap before the next group. */
public final class OptionGroup {

	private final List<Option<?>> options;

	private OptionGroup(List<Option<?>> options) {
		this.options = List.copyOf(options);
	}

	public static OptionGroup of(Option<?>... options) {
		return new OptionGroup(List.of(options));
	}

	public static OptionGroup of(List<Option<?>> options) {
		return new OptionGroup(options);
	}

	public List<Option<?>> options() {
		return options;
	}

	/** Collects options, some only when a condition holds. */
	public static final class Builder {
		private final List<Option<?>> options = new ArrayList<>();

		public Builder add(Option<?> option) {
			options.add(option);
			return this;
		}

		public Builder addIf(boolean condition, java.util.function.Supplier<Option<?>> option) {
			if (condition) {
				options.add(option.get());
			}
			return this;
		}

		public OptionGroup build() {
			return new OptionGroup(options);
		}
	}
}
