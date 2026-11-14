/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.Set;

/** Where options keep their values: saved once after an Apply that changed any of its options. */
@FunctionalInterface
public interface OptionStorage {

	/** @param flags every flag of the options applied together */
	void save(Set<OptionFlag> flags);
}
