/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/menu/OptionMenuProfileElement.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option.menu;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.option.OptionSet;
import com.coredex.altshaders.pack.option.ProfileSet;
import com.coredex.altshaders.pack.option.values.MutableOptionValues;
import com.coredex.altshaders.pack.option.values.OptionValues;

public class OptionMenuProfileElement extends OptionMenuElement {
	public final ProfileSet profiles;
	public final OptionSet options;

	private final OptionValues packAppliedValues;

	public OptionMenuProfileElement(ProfileSet profiles, OptionSet options, OptionValues packAppliedValues) {
		this.profiles = profiles;
		this.options = options;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues() {
		MutableOptionValues values = packAppliedValues.mutableCopy();
		values.addAll(PackEnv.optionQueue());

		return values;
	}
}
