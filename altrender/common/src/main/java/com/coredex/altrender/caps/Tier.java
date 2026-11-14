/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.caps;

/** The GL backend tier (development.md "GL tiers"). */
public enum Tier {
	/** GL 4.5+ (4.6 extras where present): the default whenever the driver has it. */
	GL45,
	/** GL 3.3 compatibility: the floor, and the user's other choice. */
	GL33,
	/** Below GL 3.3 compatibility (or switched off for a vanilla reference run): vanilla renders. */
	OFF
}
