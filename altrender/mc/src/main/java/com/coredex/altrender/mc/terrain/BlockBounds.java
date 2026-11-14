/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.terrain;

import com.coredex.altrender.terrain.MeshThread;
import net.minecraft.block.Block;

/**
 * Block shapes per thread (development.md 2.2). The game keeps a block's current shape in fields of the shared
 * {@code Block} object and sets them per block while rendering ({@code setShape}). Off the meshing workers this reads
 * and writes those fields as the game does; on a worker it uses the worker's own copy, so workers and the main thread
 * (collision, ray tests, particles) never see each other's shapes. The classes that run on workers and touch the
 * fields ({@code Block}, {@code PistonBaseBlock}, {@code BlockRenderer}; found by scanning the game's bytecode) are
 * redirected here by mixins.
 */
public final class BlockBounds {

	private BlockBounds() {
	}

	private static MeshWorkerState worker() {
		return Thread.currentThread() instanceof MeshThread m ? (MeshWorkerState) m.state : null;
	}

	private static double get(Block block, int i, double field) {
		MeshWorkerState w = worker();
		return w != null && w.hasShape[block.id] ? w.shapes[block.id * 6 + i] : field;
	}

	public static double minX(Block b) {
		return get(b, 0, b.minX);
	}

	public static double minY(Block b) {
		return get(b, 1, b.minY);
	}

	public static double minZ(Block b) {
		return get(b, 2, b.minZ);
	}

	public static double maxX(Block b) {
		return get(b, 3, b.maxX);
	}

	public static double maxY(Block b) {
		return get(b, 4, b.maxY);
	}

	public static double maxZ(Block b) {
		return get(b, 5, b.maxZ);
	}

	public static void setMinY(Block b, double v) {
		MeshWorkerState w = worker();
		if (w == null) {
			b.minY = v;
		} else {
			w.own(b);
			w.shapes[b.id * 6 + 1] = v;
		}
	}

	public static void setMaxY(Block b, double v) {
		MeshWorkerState w = worker();
		if (w == null) {
			b.maxY = v;
		} else {
			w.own(b);
			w.shapes[b.id * 6 + 4] = v;
		}
	}

	/** {@code Block#setShape} on a worker: true when handled here (the game's fields stay untouched). */
	public static boolean setShape(Block b, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		MeshWorkerState w = worker();
		if (w == null) {
			return false;
		}
		int o = b.id * 6;
		w.shapes[o] = minX;
		w.shapes[o + 1] = minY;
		w.shapes[o + 2] = minZ;
		w.shapes[o + 3] = maxX;
		w.shapes[o + 4] = maxY;
		w.shapes[o + 5] = maxZ;
		w.hasShape[b.id] = true;
		return true;
	}
}
