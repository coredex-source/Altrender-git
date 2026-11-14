/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.platform;

import com.coredex.altrender.terrain.SectionMesh;
import com.coredex.altrender.terrain.SectionSnapshot;

/**
 * Meshes a snapshot with the game's own block renderer (development.md 0.4, 2.2). Called on meshing workers
 * ({@code MeshThread}); the node's per-worker state comes from {@link #newWorkerState()}.
 */
public interface BlockMesher {

	/** One worker's state (made on the main thread, when the pool starts). */
	Object newWorkerState();

	void mesh(SectionSnapshot snapshot, SectionMesh out);
}
