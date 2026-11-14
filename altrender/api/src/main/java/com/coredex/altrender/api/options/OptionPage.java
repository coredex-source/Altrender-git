/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.ArrayList;
import java.util.List;

/** A page in the settings' side bar: a name and its groups of options. */
public final class OptionPage {

	private final String id;
	private final String name;
	private final List<OptionGroup> groups;
	private final List<Option<?>> options;

	public OptionPage(String id, String name, List<OptionGroup> groups) {
		this.id = id;
		this.name = name;
		this.groups = List.copyOf(groups);
		List<Option<?>> all = new ArrayList<>();
		for (OptionGroup g : this.groups) {
			all.addAll(g.options());
		}
		this.options = List.copyOf(all);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public List<OptionGroup> groups() {
		return groups;
	}

	public List<Option<?>> options() {
		return options;
	}
}
