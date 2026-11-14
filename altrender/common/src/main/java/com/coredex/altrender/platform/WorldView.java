/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.platform;

import com.coredex.altrender.terrain.SectionSnapshot;

/** The game world as the terrain renderer sees it (development.md 0.4). Main thread only. */
public interface WorldView {

	/** Sections per column (b1.7.3: 8, 128 blocks). */
	int heightSections();

	/** Whether the chunk column (cx, cz) and its eight neighbours are loaded (a section there can be meshed). */
	boolean isLoaded(int cx, int cz);

	/**
	 * Makes the chunk column (cx, cz) and its eight neighbours available for a capture, the way the game's own section
	 * compiler does (b1.7.3 singleplayer loads or generates chunks when its renderer asks for them; a client without
	 * the chunk gets an empty one, rebuilt when it arrives).
	 */
	void prepare(int cx, int cz);

	/** Copies section (sx, sy, sz) into {@code into} (development.md 2.1). */
	void capture(int sx, int sy, int sz, SectionSnapshot into);

	/**
	 * The block entities in the captured section that the game draws itself (signs, mob spawners, moving pistons…),
	 * found the way the game's section compiler finds them; {@code null} if none. Main thread, right after a capture.
	 */
	java.util.List<Object> renderedBlockEntities(SectionSnapshot captured);

	/** Swaps a section's block entities in the game's list of block entities to draw ({@code null} = none). */
	void replaceRenderedBlockEntities(java.util.List<Object> previous, java.util.List<Object> current);

	/** An identity that changes when the world does (another world, or the same one reloaded). */
	Object identity();

	/**
	 * The terrain atlas's pixels as the texture pack has them (ARGB, square, 16 × 16 sprites), or null. Main thread.
	 * The terrain uses it to tell blocks with see-through texels from solid ones (development.md 7.3).
	 */
	default int[] terrainAtlas() {
		return null;
	}
}
