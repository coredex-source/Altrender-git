/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.List;

/**
 * A mod's pages in Altrender's video settings (development.md 5.2, the API for extra pages). Registered once through
 * {@link com.coredex.altrender.api.AltrenderApi#registerOptionPages}; {@link #pages} is called each time the screen
 * opens, so options can bind to live values.
 */
public interface OptionPageProvider {

	/** The mod's id (its side-bar header groups its pages). */
	String modId();

	/** The header's text. */
	String modName();

	List<OptionPage> pages();
}
