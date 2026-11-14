/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.terrain;

import com.coredex.altrender.mc.mixin.mesh.TesselatorAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.render.vertex.Tesselator;

/**
 * One meshing worker's own copies of the game's shared per-call state (development.md 2.2): a tessellator (the game
 * has one, {@code Tesselator.INSTANCE}) and block shapes (kept in fields of the shared {@code Block} objects, which the
 * renderer sets per block). Made on the main thread.
 */
public final class MeshWorkerState {

	/** 16k ints: one block's output at most (drained after each block), not a section's. */
	private static final int TESSELATOR_INTS = 16384;

	public final Tesselator tesselator = TesselatorAccessor.altrender$create(TESSELATOR_INTS);
	/** Per block id: minX, minY, minZ, maxX, maxY, maxZ, valid where {@link #hasShape} is set. */
	final double[] shapes = new double[Block.BY_ID.length * 6];
	final boolean[] hasShape = new boolean[Block.BY_ID.length];

	/** Copies the block's current shape the first time this worker changes one coordinate of it. */
	void own(Block block) {
		int id = block.id;
		if (!hasShape[id]) {
			int o = id * 6;
			shapes[o] = block.minX;
			shapes[o + 1] = block.minY;
			shapes[o + 2] = block.minZ;
			shapes[o + 3] = block.maxX;
			shapes[o + 4] = block.maxY;
			shapes[o + 5] = block.maxZ;
			hasShape[id] = true;
		}
	}
}
