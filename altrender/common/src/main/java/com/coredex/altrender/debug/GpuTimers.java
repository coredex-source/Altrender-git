/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug;

import java.util.Arrays;
import java.util.Locale;

import com.coredex.altrender.Log;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * GPU time per pass (development.md 1.3) from timestamp queries, which nest. A frame's results are read
 * {@value #SLOTS} - 1 frames later and only when the GPU has them: never a stall. A frame whose results aren't ready by then
 * is dropped (counted). Averages over the last second feed the log ({@code -Daltrender.debug.gpuTimers}), the F3 screen
 * (Phase 5) and benchmarks.
 */
public final class GpuTimers {

	private static final int SLOTS = 8; // PRIME offload and heavy packs keep several frames in flight

	private final String[] passes;
	private final int[][] queries = new int[SLOTS][0];
	private final int[][] passOf = new int[SLOTS][0];
	private final int[] pairs = new int[SLOTS];
	private final int[] open;
	private final long[] sumNs;
	private final double[] lastMs;
	private final boolean log;
	private int slot;
	private int frames;
	private int dropped;
	private long windowStart = System.nanoTime();

	/** @param passes the pass names, indexed by the {@code pass} arguments below */
	public GpuTimers(boolean log, String... passes) {
		this.passes = passes.clone();
		this.log = log;
		open = new int[passes.length];
		sumNs = new long[passes.length];
		lastMs = new double[passes.length];
		Arrays.fill(open, -1);
	}

	public void begin(int pass) {
		int s = slot;
		int i = pairs[s];
		if (i * 2 == queries[s].length) {
			grow(s);
		}
		passOf[s][i] = pass;
		open[pass] = i;
		pairs[s] = i + 1;
		GL33C.glQueryCounter(queries[s][i * 2], GL33C.GL_TIMESTAMP);
	}

	public void end(int pass) {
		int i = open[pass];
		if (i >= 0) {
			GL33C.glQueryCounter(queries[slot][i * 2 + 1], GL33C.GL_TIMESTAMP);
			open[pass] = -1;
		}
	}

	/** Call once per frame, after the frame's last {@link #end}. */
	public void endFrame() {
		slot = (slot + 1) % SLOTS;
		collect(slot);
		long now = System.nanoTime();
		if (now - windowStart >= 1_000_000_000L) {
			StringBuilder line = new StringBuilder("gpu:");
			for (int p = 0; p < passes.length; p++) {
				lastMs[p] = frames > 0 ? sumNs[p] / 1e6 / frames : 0;
				line.append(p == 0 ? " " : ", ").append(passes[p]).append(' ')
						.append(String.format(Locale.ROOT, "%.3f", lastMs[p])).append(" ms");
			}
			if (log) {
				Log.info(line.append(" (average of ").append(frames).append(" frames, ").append(dropped)
						.append(" not ready in time)").toString());
			}
			Arrays.fill(sumNs, 0);
			frames = 0;
			dropped = 0;
			windowStart = now;
		}
	}

	/** Average GPU milliseconds per frame of each pass over the last full second. */
	public double lastMs(int pass) {
		return lastMs[pass];
	}

	public void delete() {
		for (int[] q : queries) {
			if (q.length > 0) {
				GL15C.glDeleteQueries(q);
			}
		}
	}

	/** Reads the frame recorded in {@code s}, {@value #SLOTS} - 1 frames ago, before the slot is reused. */
	private void collect(int s) {
		int n = pairs[s];
		if (n == 0) {
			return;
		}
		pairs[s] = 0;
		int[] q = queries[s];
		if (GL15C.glGetQueryObjecti(q[n * 2 - 1], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) {
			dropped++;
			return;
		}
		for (int i = 0; i < n; i++) {
			long start = GL33C.glGetQueryObjecti64(q[i * 2], GL15C.GL_QUERY_RESULT);
			long end = GL33C.glGetQueryObjecti64(q[i * 2 + 1], GL15C.GL_QUERY_RESULT);
			sumNs[passOf[s][i]] += end - start;
		}
		frames++;
	}

	private void grow(int s) {
		int old = queries[s].length;
		int[] q = Arrays.copyOf(queries[s], Math.max(8, old * 2));
		int[] fresh = new int[q.length - old];
		GL15C.glGenQueries(fresh);
		for (int i = 0; i < fresh.length; i++) {
			q[old + i] = fresh[i];
			// A generated name becomes a query object on first use; label it after that.
			GL33C.glQueryCounter(fresh[i], GL33C.GL_TIMESTAMP);
			GlDebug.label(org.lwjgl.opengl.GL43C.GL_QUERY, fresh[i], "gpu-timer");
		}
		queries[s] = q;
		passOf[s] = Arrays.copyOf(passOf[s], q.length / 2);
	}
}
