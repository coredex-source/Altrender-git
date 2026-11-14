/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

/**
 * A pipeline's own GPU work that Altrender times next to its passes (F3's GPU breakdown, development.md 6.7; since
 * 0.3.0). Each frame may time a pass several times; the times add up.
 */
public enum TimedPass {
	/** The shadow pass (shadow terrain, entities and shadowcomp). */
	SHADOW,
	/** Full-screen and compute passes (begin, prepare, deferred, composite, final). */
	PASSES
}
