/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.caps;

import java.util.List;
import java.util.function.Predicate;

/**
 * Known-bad drivers (requirement 4): a rule switches one {@link Feature} off inside the tier, with the reason logged.
 * Rules can't lower the tier: they only name features. Each rule cites the measurement or bug report behind it.
 */
public final class DriverRules {

	/** One rule: when {@code applies} holds for the context, {@code feature} is switched off. */
	public record Rule(Feature feature, Predicate<GlCaps> applies, String reason) {
	}

	/** None known yet. */
	static final List<Rule> RULES = List.of();

	private DriverRules() {
	}
}
