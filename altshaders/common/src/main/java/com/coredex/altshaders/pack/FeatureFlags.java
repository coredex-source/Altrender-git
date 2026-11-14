/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/features/FeatureFlags.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack;


import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public enum FeatureFlags {
	SEPARATE_HARDWARE_SAMPLERS(() -> true, () -> true),
	HIGHER_SHADOWCOLOR(() -> true, () -> true),
	CUSTOM_IMAGES(() -> true, () -> PackEnv.caps().imageLoadStore()),
	PER_BUFFER_BLENDING(() -> true, () -> PackEnv.caps().bufferBlending()),
	COMPUTE_SHADERS(() -> true, () -> PackEnv.caps().compute()),
	TESSELLATION_SHADERS(() -> true, () -> PackEnv.caps().tessellation()),
	ENTITY_TRANSLUCENT(() -> true, () -> true),
	REVERSED_CULLING(() -> true, () -> true),
	BLOCK_EMISSION_ATTRIBUTE(() -> true, () -> true),
	CAN_DISABLE_WEATHER(() -> true, () -> true),
	SSBO(() -> true, () -> PackEnv.caps().ssbo()),
	FADE_VARIABLE(() -> true, () -> true),
	TEXTURE_FILTERING(() -> true, () -> true),
	UNKNOWN(() -> false, () -> false);

	private final BooleanSupplier irisRequirement;
	private final BooleanSupplier hardwareRequirement;

	FeatureFlags(BooleanSupplier irisRequirement, BooleanSupplier hardwareRequirement) {
		this.irisRequirement = irisRequirement;
		this.hardwareRequirement = hardwareRequirement;
	}

	/** The lang key saying who lacks the features: AltShaders, the GPU or both (Iris's message keys, renamed). */
	public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
		boolean unsupportedHardware = false, unsupportedIris = false;
		FeatureFlags[] flags = invalidFeatureFlags.toArray(new FeatureFlags[0]);
		for (FeatureFlags flag : flags) {
			unsupportedIris |= !flag.irisRequirement.getAsBoolean();
			unsupportedHardware |= !flag.hardwareRequirement.getAsBoolean();
		}

		if (unsupportedIris) {
			if (unsupportedHardware) {
				return "altshaders.unsupported.both";
			}

			return "altshaders.unsupported.altshaders";
		} else if (unsupportedHardware) {
			return "altshaders.unsupported.gpu";
		} else {
			return null;
		}
	}

	public static boolean isInvalid(String name) {
		try {
			return !FeatureFlags.valueOf(name.toUpperCase(Locale.US)).isUsable();
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	public static FeatureFlags getValue(String value) {
		if (value.equalsIgnoreCase("TESSELATION_SHADERS")) {
			// fix the sins of the past
			value = "TESSELLATION_SHADERS";
		}

		try {
			return FeatureFlags.valueOf(value.toUpperCase(Locale.US));
		} catch (IllegalArgumentException e) {
			return FeatureFlags.UNKNOWN;
		}
	}

	public String getHumanReadableName() {
		String s = name().replace("_", " ").toLowerCase(Locale.ROOT);
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public boolean isUsable() {
		return irisRequirement.getAsBoolean() && hardwareRequirement.getAsBoolean();
	}
}
