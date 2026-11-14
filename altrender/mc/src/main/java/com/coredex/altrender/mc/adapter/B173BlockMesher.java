/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.mc.terrain.MeshWorkerState;
import com.coredex.altrender.mc.terrain.SectionMesher;
import com.coredex.altrender.platform.BlockMesher;
import com.coredex.altrender.terrain.MeshThread;
import com.coredex.altrender.terrain.SectionMesh;
import com.coredex.altrender.terrain.SectionSnapshot;

/** {@link BlockMesher} through b1.7.3's block renderer (development.md 2.2). */
public final class B173BlockMesher implements BlockMesher {

	@Override
	public Object newWorkerState() {
		return new MeshWorkerState();
	}

	@Override
	public void mesh(SectionSnapshot snapshot, SectionMesh out) {
		SnapshotWorldView view = new SnapshotWorldView(snapshot);
		SectionMesher.mesh(view, snapshot.originX(), snapshot.originY(), snapshot.originZ(),
				((MeshWorkerState) MeshThread.currentState()).tesselator, out);
		out.readSkyLight = view.readSkyLight();
		out.visibility = visibility(snapshot);
	}

	/** Face connectivity through cells that aren't full opaque cubes (development.md 2.5). */
	private static long visibility(SectionSnapshot s) {
		boolean[] opaque = new boolean[4096];
		int ox = s.originX();
		int oy = s.originY();
		int oz = s.originZ();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					int id = s.blocks[s.index(ox + x, oy + y, oz + z)];
					opaque[(y * 16 + z) * 16 + x] = id > 0 && net.minecraft.block.Block.IS_SOLID_RENDER[id];
				}
			}
		}
		return com.coredex.altrender.terrain.Visibility.compute(opaque);
	}
}
