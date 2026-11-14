/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/** How source and destination combine after the blend factors (GL 1.4 core: every tier has all five). */
public enum BlendEquation {
	ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX
}
