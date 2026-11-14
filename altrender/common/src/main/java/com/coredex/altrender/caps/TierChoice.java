/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.caps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The tier and features for a context (requirement 4): the highest tier the driver supports unless the user chose
 * GL 3.3; driver rules and dev switches only turn features off inside it.
 *
 * @param tier     the chosen tier
 * @param reason   why, for the log
 * @param features the features in use
 * @param notes    features switched off, with the reason
 */
public record TierChoice(Tier tier, String reason, Set<Feature> features, List<String> notes) {

	/**
	 * @param caps     the probed context
	 * @param setting  the user's setting: {@code auto} or {@code gl33}
	 * @param override {@code -Daltrender.tier} ({@code gl33} or {@code gl45}), or {@code null}
	 * @param off      {@code -Daltrender.renderer=off} (dev: vanilla reference with the same window layer)
	 */
	public static TierChoice choose(GlCaps caps, String setting, String override, boolean off) {
		if (off) {
			return new TierChoice(Tier.OFF, "-Daltrender.renderer=off", Set.of(), List.of());
		}
		if (!caps.atLeast(3, 3) || caps.profile.equals("core")) {
			return new TierChoice(Tier.OFF, "OpenGL " + caps.major + "." + caps.minor + " " + caps.profile
					+ ", Altrender needs 3.3 compatibility", Set.of(), List.of());
		}
		Tier tier;
		String reason;
		if ("gl33".equals(override)) {
			tier = Tier.GL33;
			reason = "-Daltrender.tier=gl33";
		} else if (override == null && "gl33".equals(setting)) {
			tier = Tier.GL33;
			reason = "chosen in the settings";
		} else if (caps.gl45()) {
			tier = Tier.GL45;
			reason = "gl45".equals(override) ? "-Daltrender.tier=gl45" : "highest supported";
		} else {
			tier = Tier.GL33;
			reason = "gl45 needs " + String.join(", ", caps.gl45Missing());
		}
		Set<Feature> features = EnumSet.noneOf(Feature.class);
		List<String> notes = new ArrayList<>();
		for (Feature f : Feature.values()) {
			if (!f.present(caps) || f.gl45Only && tier != Tier.GL45) {
				continue;
			}
			if (Boolean.getBoolean("altrender.debug." + f.key + ".disable")) {
				notes.add(f.key + " off: -Daltrender.debug." + f.key + ".disable");
				continue;
			}
			String rule = null;
			for (DriverRules.Rule r : DriverRules.RULES) {
				if (r.feature() == f && r.applies().test(caps)) {
					rule = r.reason();
					break;
				}
			}
			if (rule != null) {
				notes.add(f.key + " off: " + rule);
				continue;
			}
			features.add(f);
		}
		return new TierChoice(tier, reason, Collections.unmodifiableSet(features), List.copyOf(notes));
	}
}
