/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.adapter;

import com.coredex.altrender.platform.WorldView;
import com.coredex.altrender.terrain.SectionSnapshot;
import net.minecraft.world.World;

/** {@link WorldView} on a b1.7.3 world. */
public final class B173WorldView implements WorldView {

	private final World world;
	private final net.minecraft.client.render.world.WorldRenderer renderer;

	public B173WorldView(World world, net.minecraft.client.render.world.WorldRenderer renderer) {
		this.world = world;
		this.renderer = renderer;
	}

	@Override
	public int[] terrainAtlas() {
		// As the texture pack has it (the game keeps these pixels for its own colour lookups).
		return ((com.coredex.altrender.mc.mixin.WorldRendererTexturesAccess) renderer).altrender$textureManager()
				.getColors("/terrain.png");
	}

	@Override
	public int heightSections() {
		return 8;
	}

	@Override
	public boolean isLoaded(int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!world.getChunkSource().hasChunk(cx + dx, cz + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void prepare(int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.getChunkAt(cx + dx, cz + dz);
			}
		}
	}

	@Override
	public void capture(int sx, int sy, int sz, SectionSnapshot into) {
		SnapshotCapture.capture(world, sx, sy, sz, into);
	}

	/** As {@code RenderChunk#compile}: blocks with a block entity whose block entity has a renderer. */
	@Override
	public java.util.List<Object> renderedBlockEntities(SectionSnapshot s) {
		java.util.List<Object> out = null;
		int ox = s.originX();
		int oy = s.originY();
		int oz = s.originZ();
		for (int y = oy; y < oy + 16; y++) {
			for (int z = oz; z < oz + 16; z++) {
				for (int x = ox; x < ox + 16; x++) {
					int id = s.blocks[s.index(x, y, z)];
					if (id > 0 && net.minecraft.block.Block.HAS_BLOCK_ENTITY[id]) {
						net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(x, y, z);
						if (net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher.INSTANCE.hasRenderer(be)) {
							if (out == null) {
								out = new java.util.ArrayList<>();
							}
							out.add(be);
						}
					}
				}
			}
		}
		return out;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void replaceRenderedBlockEntities(java.util.List<Object> previous, java.util.List<Object> current) {
		java.util.List<Object> list = (java.util.List<Object>) (java.util.List<?>) renderer.blockEntitiesToBeRendered;
		if (previous != null) {
			list.removeAll(previous);
		}
		if (current != null) {
			list.addAll(current);
		}
	}

	@Override
	public Object identity() {
		return world;
	}
}
