/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack;

import java.util.List;

/**
 * A pack requires features ({@code iris.features.required}) that AltShaders or this GPU lacks: it isn't loaded, and
 * the shader pack page says why (Iris shows its "unsupported pack" screen and turns shaders off).
 */
public final class UnsupportedPackException extends IllegalStateException {

	private final List<FeatureFlags> missing;

	public UnsupportedPackException(List<FeatureFlags> missing) {
		super("the pack requires features " + who(FeatureFlags.getInvalidStatus(missing)) + " lacks: "
				+ String.join(", ", missing.stream().map(FeatureFlags::getHumanReadableName).toList()));
		this.missing = List.copyOf(missing);
	}

	private static String who(String status) {
		if (status == null) {
			return "AltShaders";
		}
		return switch (status) {
			case "altshaders.unsupported.gpu" -> "this GPU";
			case "altshaders.unsupported.both" -> "AltShaders and this GPU";
			default -> "AltShaders";
		};
	}

	public List<FeatureFlags> missing() {
		return missing;
	}
}
