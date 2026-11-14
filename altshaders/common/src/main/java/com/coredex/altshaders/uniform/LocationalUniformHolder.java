/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform;

import java.util.OptionalInt;

/** A program's uniform locations by name (the ported custom uniforms ask through this). */
public interface LocationalUniformHolder {
	/** The location of uniform {@code name}, if the program has it. */
	OptionalInt location(String name, UniformType type);
}
