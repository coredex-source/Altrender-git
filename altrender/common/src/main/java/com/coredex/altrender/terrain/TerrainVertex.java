/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.coredex.altrender.strategy.VertexLayout;

/**
 * The terrain vertex format (development.md 2.3), 20 bytes, after the compact layout of Sodium 0.5 (via Celeritas):
 * <pre>
 *  0  u16 x, y, z   section-relative position over [-8, 24) blocks, 1/2048-block steps
 *  6  u16 section   the section's index in its region (8 × 4 × 8 sections): the shader adds its offset
 *  8  u8 × 4 colour the game's vertex colour (RGBA; the light is baked in, as the game does)
 * 12  f32 u, v      atlas coordinates, exactly the game's (its UVs keep a 0.01-pixel inset at tile edges, which
 *                   any 16-bit encoding rounds across texel boundaries: measured, scattered texel flips)
 * </pre>
 * Shader packs' extended attributes are an API extension point (Phase 6), not a second format here.
 */
public final class TerrainVertex {

	public static final int STRIDE = 20;
	public static final float POSITION_OFFSET = 8.0f;
	public static final float POSITION_SCALE = 2048.0f;

	private static final List<VertexLayout.Attribute> VERTEX = List.of(
			new VertexLayout.Attribute(0, "a_Pos", 4, VertexLayout.AttribType.USHORT, false, 0),
			new VertexLayout.Attribute(1, "a_Color", 4, VertexLayout.AttribType.UBYTE, true, 8),
			new VertexLayout.Attribute(2, "a_TexCoord", 2, VertexLayout.AttribType.FLOAT, false, 12));

	/**
	 * Per-draw data (development.md 4.3): the region's offsets, {@code a_RegionOffset} = (1024-block base) - camera and
	 * {@code a_RegionInner} = the region's origin within its base. Locations 6 and 7: the only ones below 8 that no
	 * driver aliases to a fixed-function attribute (NVIDIA: 3 is the colour), so a constant value set there between
	 * draws can't change the game's current colour, normal or fog coordinate.
	 */
	public static final int REGION_OFFSET = 6;
	public static final int REGION_INNER = 7;
	/** Bytes of per-draw data: two vec3. */
	public static final int DRAW_STRIDE = 24;

	/** Per-draw data as constant attributes, set before each region's draws (gl33; gl45 without indirect draws). */
	public static final VertexLayout LAYOUT = new VertexLayout(STRIDE, VERTEX);

	/** Per-draw data as instanced attributes, one record per indirect command (its base instance). */
	public static final VertexLayout LAYOUT_INDIRECT = new VertexLayout(STRIDE, VERTEX, DRAW_STRIDE, List.of(
			new VertexLayout.Attribute(REGION_OFFSET, "a_RegionOffset", 3, VertexLayout.AttribType.FLOAT, false, 0),
			new VertexLayout.Attribute(REGION_INNER, "a_RegionInner", 3, VertexLayout.AttribType.FLOAT, false, 12)));

	/** The program's attributes by location (the program strategy binds list index = location). */
	public static final List<String> ATTRIBUTES = List.of("a_Pos", "a_Color", "a_TexCoord", "_unused3", "_unused4",
			"_unused5", "a_RegionOffset", "a_RegionInner");

	private TerrainVertex() {
	}

	/** Bytes for {@code quads} quads. */
	public static int bytes(int quads) {
		return quads * 4 * STRIDE;
	}

	/** Bytes for {@code quads} quads in the extended format. */
	public static int extendedBytes(int quads) {
		return quads * 4 * EXTENDED_STRIDE;
	}

	/**
	 * Encodes a layer of a {@link SectionMesh} (the game's vertex layout) into {@code out} at its position.
	 *
	 * @param sectionIndex the section's index in its region
	 */
	public static void encode(SectionMesh mesh, int layer, int sectionIndex, ByteBuffer out) {
		out.order(ByteOrder.LITTLE_ENDIAN);
		int[] q = mesh.data(layer);
		int vertices = mesh.quadCount(layer) * 4;
		for (int v = 0; v < vertices; v++) {
			int i = v * SectionMesh.INTS_PER_VERTEX;
			out.putShort(position(Float.intBitsToFloat(q[i])));
			out.putShort(position(Float.intBitsToFloat(q[i + 1])));
			out.putShort(position(Float.intBitsToFloat(q[i + 2])));
			out.putShort((short) sectionIndex);
			out.putInt(q[i + 5]); // ABGR int = R, G, B, A bytes in little-endian order
			out.putInt(q[i + 3]);
			out.putInt(q[i + 4]);
		}
	}

	// ---- the extended format for shader packs (development.md 6.6; the API's TerrainFormat) -------------------

	/**
	 * Shader packs' terrain vertex, 48 bytes:
	 * <pre>
	 *  0  u16 x, y, z, section   as above
	 *  8  u8 × 4 colour          tint and face shade, no light
	 * 12  f32 u, v               atlas coordinates
	 * 20  u8 block, sky          light × 16 (modern lightmap coordinates), normalised; 2 bytes padding
	 * 24  s8 × 4 normal          normalised
	 * 28  s16 × 2 mc_Entity      the pipeline's block id, -1
	 * 32  f32 × 2 mc_midTexCoord the quad's centre in the atlas
	 * 40  s8 × 4 at_tangent      normalised; w the handedness
	 * 44  s8 × 4 at_midBlock     the vertex to the block's centre in 1/64 blocks; w the block's light emission
	 * </pre>
	 */
	public static final int EXTENDED_STRIDE = 48;

	private static final List<VertexLayout.Attribute> EXTENDED_VERTEX = List.of(
			new VertexLayout.Attribute(0, "ar_Pos", 4, VertexLayout.AttribType.USHORT, false, 0),
			new VertexLayout.Attribute(1, "ar_ColorIn", 4, VertexLayout.AttribType.UBYTE, true, 8),
			new VertexLayout.Attribute(2, "ar_TexCoord", 2, VertexLayout.AttribType.FLOAT, false, 12),
			new VertexLayout.Attribute(3, "ar_Light", 2, VertexLayout.AttribType.UBYTE, true, 20),
			new VertexLayout.Attribute(4, "ar_NormalIn", 4, VertexLayout.AttribType.BYTE, true, 24),
			new VertexLayout.Attribute(5, "mc_Entity", 2, VertexLayout.AttribType.SHORT_AS_FLOAT, false, 28),
			new VertexLayout.Attribute(8, "mc_midTexCoord", 2, VertexLayout.AttribType.FLOAT, false, 32),
			new VertexLayout.Attribute(9, "at_tangent", 4, VertexLayout.AttribType.BYTE, true, 40),
			new VertexLayout.Attribute(10, "at_midBlock", 4, VertexLayout.AttribType.BYTE_AS_FLOAT, false, 44));

	public static final VertexLayout EXTENDED_LAYOUT = new VertexLayout(EXTENDED_STRIDE, EXTENDED_VERTEX);

	public static final VertexLayout EXTENDED_LAYOUT_INDIRECT = new VertexLayout(EXTENDED_STRIDE, EXTENDED_VERTEX,
			DRAW_STRIDE, List.of(
					new VertexLayout.Attribute(REGION_OFFSET, "ar_RegionOffset", 3, VertexLayout.AttribType.FLOAT, false, 0),
					new VertexLayout.Attribute(REGION_INNER, "ar_RegionInner", 3, VertexLayout.AttribType.FLOAT, false, 12)));

	/**
	 * The mesher's extra data per vertex in extended meshing (slots 6 and 7 of a {@link SectionMesh} vertex): slot 6
	 * the block's section-local position (x, y, z in 5 bits each, bit 31 set); slot 7 the vertex's block light (bits
	 * 0–3) and sky light (4–7), the block's id (8–19), metadata (20–23) and light emission (24–27).
	 */
	public static int blockSlot(int x, int y, int z) {
		return x & 31 | (y & 31) << 5 | (z & 31) << 10 | 1 << 31;
	}

	public static int lightSlot(int blockLight, int skyLight, int id, int meta, int emission) {
		return blockLight & 15 | (skyLight & 15) << 4 | (id & 0xFFF) << 8 | (meta & 15) << 20 | (emission & 15) << 24;
	}

	/**
	 * Encodes a layer in the extended format; {@code blockIds} maps a block key ({@code id << 4 | meta}) to the
	 * pipeline's id (-1: none), or is null.
	 */
	public static void encodeExtended(SectionMesh mesh, int layer, int sectionIndex, int[] blockIds, ByteBuffer out) {
		out.order(ByteOrder.LITTLE_ENDIAN);
		int[] q = mesh.data(layer);
		int quads = mesh.quadCount(layer);
		float[] p = new float[12];
		float[] uv = new float[8];
		for (int quad = 0; quad < quads; quad++) {
			int base = quad * SectionMesh.INTS_PER_QUAD;
			for (int v = 0; v < 4; v++) {
				int i = base + v * SectionMesh.INTS_PER_VERTEX;
				p[v * 3] = Float.intBitsToFloat(q[i]);
				p[v * 3 + 1] = Float.intBitsToFloat(q[i + 1]);
				p[v * 3 + 2] = Float.intBitsToFloat(q[i + 2]);
				uv[v * 2] = Float.intBitsToFloat(q[i + 3]);
				uv[v * 2 + 1] = Float.intBitsToFloat(q[i + 4]);
			}
			int blockSlot = q[base + 6];
			int bx = blockSlot & 31;
			int by = blockSlot >> 5 & 31;
			int bz = blockSlot >> 10 & 31;
			boolean hasBlock = blockSlot < 0;
			// Normal: the quad's plane, on the side its winding faces (the game's faces are wound to face out: it culls
			// back faces). Facing away from the block's centre got thin blocks wrong (a snow layer's top is below it).
			float e1x = p[3] - p[0], e1y = p[4] - p[1], e1z = p[5] - p[2];
			float e2x = p[6] - p[0], e2y = p[7] - p[1], e2z = p[8] - p[2];
			float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 1e-12f) {
				nx /= len;
				ny /= len;
				nz /= len;
			} else {
				nx = 0;
				ny = 1;
				nz = 0;
			}
			// Tangent from the texture's u direction.
			float du1 = uv[2] - uv[0], dv1 = uv[3] - uv[1];
			float du2 = uv[4] - uv[0], dv2 = uv[5] - uv[1];
			float det = du1 * dv2 - du2 * dv1;
			float tx, ty, tz, tw = 1;
			if (Math.abs(det) > 1e-12f) {
				float r = 1 / det;
				tx = (e1x * dv2 - e2x * dv1) * r;
				ty = (e1y * dv2 - e2y * dv1) * r;
				tz = (e1z * dv2 - e2z * dv1) * r;
				float bxv = (e2x * du1 - e1x * du2) * r, byv = (e2y * du1 - e1y * du2) * r, bzv = (e2z * du1 - e1z * du2) * r;
				// handedness: sign(dot(cross(n, t), b))
				float cxx = ny * tz - nz * ty, cyy = nz * tx - nx * tz, czz = nx * ty - ny * tx;
				tw = cxx * bxv + cyy * byv + czz * bzv < 0 ? -1 : 1;
			} else {
				tx = 1;
				ty = 0;
				tz = 0;
			}
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl > 1e-12f) {
				tx /= tl;
				ty /= tl;
				tz /= tl;
			}
			float midU = (uv[0] + uv[2] + uv[4] + uv[6]) * 0.25f;
			float midV = (uv[1] + uv[3] + uv[5] + uv[7]) * 0.25f;
			for (int v = 0; v < 4; v++) {
				int i = base + v * SectionMesh.INTS_PER_VERTEX;
				int extra = q[i + 7];
				out.putShort(position(p[v * 3]));
				out.putShort(position(p[v * 3 + 1]));
				out.putShort(position(p[v * 3 + 2]));
				out.putShort((short) sectionIndex);
				out.putInt(q[i + 5]);
				out.putInt(q[i + 3]);
				out.putInt(q[i + 4]);
				out.put((byte) ((extra & 15) * 16)); // block light
				out.put((byte) ((extra >> 4 & 15) * 16)); // sky light
				out.putShort((short) 0);
				out.put(snorm(nx)).put(snorm(ny)).put(snorm(nz)).put((byte) 0);
				int key = (extra >> 8 & 0xFFF) << 4 | (extra >> 20 & 15);
				int id = blockIds != null && key < blockIds.length ? blockIds[key] : -1;
				out.putShort((short) id);
				out.putShort((short) -1);
				out.putFloat(midU);
				out.putFloat(midV);
				out.put(snorm(tx)).put(snorm(ty)).put(snorm(tz)).put((byte) (tw < 0 ? -127 : 127));
				if (hasBlock) {
					out.put(offset64(bx + 0.5f - p[v * 3]));
					out.put(offset64(by + 0.5f - p[v * 3 + 1]));
					out.put(offset64(bz + 0.5f - p[v * 3 + 2]));
				} else {
					out.put((byte) 0).put((byte) 0).put((byte) 0);
				}
				out.put((byte) (extra >> 24 & 15));
			}
		}
	}

	private static byte snorm(float f) {
		return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
	}

	private static byte offset64(float blocks) {
		return (byte) Math.max(-128, Math.min(127, Math.round(blocks * 64f)));
	}

	static short position(float p) {
		int v = Math.round((p + POSITION_OFFSET) * POSITION_SCALE);
		return (short) Math.max(0, Math.min(0xFFFF, v));
	}
}
