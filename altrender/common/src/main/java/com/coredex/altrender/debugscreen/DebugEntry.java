/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debugscreen;

import java.util.List;
import java.util.function.Consumer;

/**
 * One block of lines on the debug screen (development.md 5.3): its id (saved visibility), its name in the debug
 * options, its column, whether it shows by default, and the lines it adds when drawn.
 */
public record DebugEntry(String id, String name, Column column, Visibility defaultVisibility,
		Consumer<List<String>> lines) {

	public enum Column {
		LEFT, RIGHT
	}

	/** When an entry shows: with F3 (the default), always (an overlay without F3), or never. */
	public enum Visibility {
		ALWAYS, F3, NEVER
	}
}
