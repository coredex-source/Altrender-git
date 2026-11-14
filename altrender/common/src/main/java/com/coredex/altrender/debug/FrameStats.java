/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug;

import java.util.Locale;

import com.coredex.altrender.Log;

/**
 * Per-frame counters (development.md 1.3), counted on the render thread by whoever does the work: GL calls and draw
 * calls by the merged {@code GL11} (1.4), uploads by the strategies, sections by the terrain (vanilla's compiles until
 * Phase 2). {@code -Daltrender.debug.frameStats} logs the per-frame averages once a second. Also the time the render
 * thread spends presenting (the buffer swap, where it waits when the GPU is behind): the bench's CPU frame time is a
 * frame's time without it (development.md 7.1).
 */
public final class FrameStats {

	public static int glCalls;
	public static int drawCalls;
	/** State calls the tracker found redundant and didn't send (3.4). */
	public static int skippedCalls;
	private static long sumSkipped;
	public static int uploads;
	public static int sectionsBuilt;

	private static final boolean LOG = Boolean.getBoolean("altrender.debug.frameStats");
	private static long sumGl;
	private static long sumDraw;
	private static long sumUploads;
	private static long sumSections;
	private static int frames;
	private static long windowStart = System.nanoTime();

	private static long swapNanos;

	private FrameStats() {
	}

	/** Adds one buffer swap's duration (render thread). */
	public static void swapped(long nanos) {
		swapNanos += nanos;
	}

	/** Every buffer swap's duration so far, added up (render thread). */
	public static long swapNanos() {
		return swapNanos;
	}

	/** Call once per frame. */
	public static void endFrame() {
		sumGl += glCalls;
		sumDraw += drawCalls;
		sumUploads += uploads;
		sumSections += sectionsBuilt;
		sumSkipped += skippedCalls;
		skippedCalls = 0;
		glCalls = 0;
		drawCalls = 0;
		uploads = 0;
		sectionsBuilt = 0;
		frames++;
		long now = System.nanoTime();
		if (now - windowStart >= 1_000_000_000L) {
			if (LOG) {
				Log.info(String.format(Locale.ROOT,
						"frame stats (per frame, %d frames): gl calls %.0f (%.0f redundant, skipped), draw calls %.0f, uploads %.1f, sections built %.2f",
						frames, (double) sumGl / frames, (double) sumSkipped / frames, (double) sumDraw / frames, (double) sumUploads / frames,
						(double) sumSections / frames));
			}
			sumGl = sumDraw = sumUploads = sumSections = sumSkipped = 0;
			frames = 0;
			windowStart = now;
		}
	}
}
