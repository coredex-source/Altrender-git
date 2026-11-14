/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.terrain;

import com.coredex.altrender.mc.mixin.mesh.TesselatorAccessor;
import com.coredex.altrender.terrain.SectionMesh;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.world.WorldView;

/**
 * Meshes one section with the game's own block renderer (development.md 2.2), the way {@code RenderChunk#compile}
 * does: layer 0, then layer 1 only if some block wanted it; blocks in y, z, x order; one tessellator stream per layer
 * started at the section's first block, offset by the section origin. The stream is drained after each block (so a
 * worker's tessellator stays small) without resetting the colour/texture/normal state a stream carries.
 */
public final class SectionMesher {

	private static final int[] QUAD_FROM_TRIANGLES = {0, 1, 2, 5}; // the tessellator writes v0 v1 v2 v0 v2 v3
	private static final int[] QUAD = {0, 1, 2, 3};

	private SectionMesher() {
	}

	/**
	 * @param tesselator the current thread's: {@code Tesselator.INSTANCE} on the main thread, the worker's own on a
	 *                   worker (BlockRendererMeshMixin gives the block renderer the same one)
	 */
	public static void mesh(WorldView world, int ox, int oy, int oz, Tesselator tesselator, SectionMesh out) {
		out.clear();
		BlockRenderer renderer = new BlockRenderer(world);
		// A shader pack's terrain (development.md 6.6): the light goes into the extra slots, not the colour.
		com.coredex.altrender.mc.adapter.SnapshotWorldView extended =
				world instanceof com.coredex.altrender.mc.adapter.SnapshotWorldView v && v.snapshot().extended ? v : null;
		TesselatorAccessor t = (TesselatorAccessor) tesselator;
		boolean triangles = TesselatorAccessor.altrender$triangleMode();
		for (int pass = 0; pass < SectionMesh.LAYERS; pass++) {
			boolean begun = false;
			boolean otherLayer = false;
			for (int y = oy; y < oy + 16; y++) {
				for (int z = oz; z < oz + 16; z++) {
					for (int x = ox; x < ox + 16; x++) {
						int id = world.getBlock(x, y, z);
						if (id <= 0) {
							continue;
						}
						if (!begun) {
							begun = true;
							tesselator.begin();
							tesselator.offset(-ox, -oy, -oz);
						}
						Block block = Block.BY_ID[id];
						if (block.getRenderLayer() != pass) {
							otherLayer = true;
							continue;
						}
						renderer.tesselateInWorld(block, x, y, z);
						if (extended != null) {
							drainExtended(t, triangles, out, pass, extended, ox, oy, oz, x, y, z, id);
						} else {
							drain(t, triangles, out, pass);
						}
					}
				}
			}
			if (begun) {
				// Instead of end(), which would draw.
				t.altrender$setTesselating(false);
				drainReset(t);
				tesselator.offset(0.0, 0.0, 0.0);
			}
			if (!otherLayer) {
				break;
			}
		}
	}

	/** Moves the stream's quads into {@code out}, as 4 vertices each; slots the stream hasn't written are 0. */
	private static void drain(TesselatorAccessor t, boolean triangles, SectionMesh out, int layer) {
		int vertices = t.altrender$vertexCount();
		if (vertices == 0) {
			return;
		}
		int[] order = triangles ? QUAD_FROM_TRIANGLES : QUAD;
		int perQuad = triangles ? 6 : 4;
		if (vertices % perQuad != 0) {
			throw new IllegalStateException("tessellator holds " + vertices + " vertices, not whole quads");
		}
		boolean textured = t.altrender$textured();
		boolean colored = t.altrender$colored();
		boolean normals = t.altrender$hasNormals();
		int[] src = t.altrender$vertexBuffer();
		int quads = vertices / perQuad;
		int at = out.append(layer, quads * SectionMesh.INTS_PER_QUAD);
		int[] dst = out.data(layer);
		for (int q = 0; q < quads; q++) {
			int base = q * perQuad * SectionMesh.INTS_PER_VERTEX;
			for (int v : order) {
				int s = base + v * SectionMesh.INTS_PER_VERTEX;
				dst[at] = src[s];
				dst[at + 1] = src[s + 1];
				dst[at + 2] = src[s + 2];
				dst[at + 3] = textured ? src[s + 3] : 0;
				dst[at + 4] = textured ? src[s + 4] : 0;
				dst[at + 5] = colored ? src[s + 5] : 0;
				dst[at + 6] = normals ? src[s + 6] : 0;
				dst[at + 7] = 0;
				at += SectionMesh.INTS_PER_VERTEX;
			}
		}
		drainReset(t);
	}

	/**
	 * {@link #drain} for a shader pack: also the block's position, id, metadata and light emission, and each vertex's
	 * smooth light, the average over the open cells in front of the face around the vertex (sky and block light
	 * apart, as modern lightmap coordinates).
	 */
	private static void drainExtended(TesselatorAccessor t, boolean triangles, SectionMesh out, int layer,
			com.coredex.altrender.mc.adapter.SnapshotWorldView world, int ox, int oy, int oz, int bx, int by, int bz, int id) {
		int start = out.quadCount(layer) * SectionMesh.INTS_PER_QUAD;
		drain(t, triangles, out, layer);
		int end = out.quadCount(layer) * SectionMesh.INTS_PER_QUAD;
		if (end == start) {
			return;
		}
		int meta = world.getBlockMetadata(bx, by, bz);
		int emission = id < Block.LIGHT.length ? Block.LIGHT[id] : 0;
		int blockSlot = com.coredex.altrender.terrain.TerrainVertex.blockSlot(bx - ox, by - oy, bz - oz);
		int[] d = out.data(layer);
		int[] light = new int[2];
		// Ambient occlusion for the pack (the game's smooth lighting is in the brightness, which is 1 here): as modern
		// smooth lighting, each solid cell of the four in front of a corner takes 0.2 off; in the colour's alpha for a
		// pack that wants it apart (separateAo), else in its colour. The opaque layer only.
		boolean ao = layer == 0 && net.minecraft.client.Minecraft.isAmbientOcclusionEnabled();
		boolean aoInAlpha = world.snapshot().separateAo;
		for (int q = start; q < end; q += SectionMesh.INTS_PER_QUAD) {
			float[] p = new float[12];
			for (int v = 0; v < 4; v++) {
				int i = q + v * SectionMesh.INTS_PER_VERTEX;
				p[v * 3] = Float.intBitsToFloat(d[i]) + ox;
				p[v * 3 + 1] = Float.intBitsToFloat(d[i + 1]) + oy;
				p[v * 3 + 2] = Float.intBitsToFloat(d[i + 2]) + oz;
			}
			// The face's axis and side, from the quad's plane and winding (the game's faces face out; as the normal in
			// TerrainVertex).
			float e1x = p[3] - p[0], e1y = p[4] - p[1], e1z = p[5] - p[2];
			float e2x = p[6] - p[0], e2y = p[7] - p[1], e2z = p[8] - p[2];
			float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
			float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			int axis = len > 0 && Math.max(ax, Math.max(ay, az)) > 0.9f * len ? (ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2) : -1;
			int sign = axis == 0 ? (nx > 0 ? 1 : -1) : axis == 1 ? (ny > 0 ? 1 : -1) : (nz > 0 ? 1 : -1);
			for (int v = 0; v < 4; v++) {
				int i = q + v * SectionMesh.INTS_PER_VERTEX;
				int blockLight;
				int skyLight;
				float occlusion = 1f;
				if (axis < 0) {
					// Crossed sprites and other slanted quads: the block's own cell.
					world.light(bx, by, bz, light);
					blockLight = light[0];
					skyLight = light[1];
				} else {
					int sumB = 0;
					int sumS = 0;
					int n = 0;
					int maxB = 0;
					int maxS = 0;
					for (int a = 0; a < 2; a++) {
						for (int b = 0; b < 2; b++) {
							int[] c = cell(p, v, axis, sign, a, b);
							world.light(c[0], c[1], c[2], light);
							maxB = Math.max(maxB, light[0]);
							maxS = Math.max(maxS, light[1]);
							if (!world.isSolidRender(c[0], c[1], c[2])) {
								sumB += light[0];
								sumS += light[1];
								n++;
							}
						}
					}
					blockLight = n > 0 ? Math.round((float) sumB / n) : maxB;
					skyLight = n > 0 ? Math.round((float) sumS / n) : maxS;
					if (ao) {
						occlusion = 1f - 0.2f * (4 - n);
					}
				}
				if (occlusion < 1f) {
					int c = d[i + 5]; // ABGR
					if (aoInAlpha) {
						int a = Math.round((c >>> 24) * occlusion);
						d[i + 5] = (c & 0x00FFFFFF) | a << 24;
					} else {
						int r = Math.round((c & 255) * occlusion);
						int g = Math.round((c >> 8 & 255) * occlusion);
						int b = Math.round((c >> 16 & 255) * occlusion);
						d[i + 5] = (c & 0xFF000000) | b << 16 | g << 8 | r;
					}
				}
				d[i + 6] = blockSlot;
				d[i + 7] = com.coredex.altrender.terrain.TerrainVertex.lightSlot(blockLight, skyLight, id, meta, emission);
			}
		}
	}

	/** One of the four cells in front of a face around vertex {@code v} ({@code a}, {@code b}: which side on the face's two other axes). */
	private static int[] cell(float[] p, int v, int axis, int sign, int a, int b) {
		int[] c = new int[3];
		int k = 0;
		for (int ax = 0; ax < 3; ax++) {
			float coord = p[v * 3 + ax];
			if (ax == axis) {
				c[ax] = (int) Math.floor(coord + sign * 0.01f);
			} else {
				int side = k++ == 0 ? a : b;
				c[ax] = (int) Math.floor(coord + (side == 0 ? -0.5f : 0.5f));
			}
		}
		return c;
	}

	private static void drainReset(TesselatorAccessor t) {
		t.altrender$setVertexCount(0);
		t.altrender$setIndex(0);
		t.altrender$setNextVertexCount(0);
	}
}
