/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.gl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.Log;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;

/**
 * The tracker's shadow check (development.md 1.4, {@code -Daltrender.debug.trackerCheck}): at pass boundaries,
 * compares everything the tracker knows with {@code glGet}. Unknown categories (after a display list or glPopAttrib)
 * are counted, not compared. Matrices compare with a relative tolerance: the driver's float maths may round
 * differently. Debug only: every check is a pipeline sync.
 */
public final class GlTrackerCheck {

	public static final boolean ENABLED = Boolean.getBoolean("altrender.debug.trackerCheck");
	private static final int DETAIL_LINES = 30;
	private static final float TOLERANCE = 1e-4f;

	private static long checks;
	private static long mismatches;
	private static long unknown;
	private static int detailLines;
	private static final Map<String, Integer> BY_FIELD = new HashMap<>();
	private static final float[] GL_MATRIX = new float[16];

	private GlTrackerCheck() {
	}

	/** Compares now; {@code where} names the pass boundary for the log. */
	public static void check(String where) {
		if (GlStateTracker.compiling()) {
			return;
		}
		checks++;
		List<String> bad = new ArrayList<>();
		int dirty = GlStateTracker.dirty();
		for (int m = 0; m < 3; m++) {
			if (m == 2 && GlStateTracker.activeUnit != 0) {
				continue;
			}
			if (GlStateTracker.matrixUnknown(m)) {
				unknown++;
				continue;
			}
			int depth = GL11C.glGetInteger(0x0BA3 + m);
			if (depth != GlStateTracker.stackDepth(m)) {
				bad.add(GlStateTracker.CATEGORY_NAMES[m] + " depth " + GlStateTracker.stackDepth(m) + " vs gl " + depth);
			}
			GL11C.glGetFloatv(0x0BA6 + m, GL_MATRIX);
			float[] ours = GlStateTracker.stackTop(m);
			for (int i = 0; i < 16; i++) {
				float scale = Math.max(1f, Math.max(Math.abs(ours[i]), Math.abs(GL_MATRIX[i])));
				if (Math.abs(ours[i] - GL_MATRIX[i]) > TOLERANCE * scale) {
					bad.add(GlStateTracker.CATEGORY_NAMES[m] + "[" + i + "] " + ours[i] + " vs gl " + GL_MATRIX[i]);
					break;
				}
			}
		}
		if (known(dirty, GlStateTracker.C_MATRIX_MODE)) {
			compare(bad, "matrix mode", GlStateTracker.rawMatrixMode(), GL11C.glGetInteger(GlStateTracker.GL_MATRIX_MODE));
		}
		if (known(dirty, GlStateTracker.C_TEXTURES)) {
			int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE) - GL13C.GL_TEXTURE0;
			compare(bad, "active unit", GlStateTracker.activeUnit, active);
			if (active == GlStateTracker.activeUnit) {
				for (int u = 0; u < GlStateTracker.unitsUsed; u++) {
					GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + u);
					compare(bad, "texture2D[" + u + "]", GlStateTracker.BOUND_2D[u],
							GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
				}
				GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + active);
			}
		}
		if (known(dirty, GlStateTracker.C_ENABLES)) {
			for (int key : GlStateTracker.ENABLES.keys()) {
				boolean ours = GlStateTracker.ENABLES.get(key, 0) == 1;
				if (ours != GlStateTracker.readEnable(key)) {
					bad.add("enable 0x" + Integer.toHexString(key) + " " + ours);
				}
			}
		}
		if (known(dirty, GlStateTracker.C_CLIENT)) {
			for (int key : GlStateTracker.CLIENT.keys()) {
				boolean ours = GlStateTracker.CLIENT.get(key, 0) == 1;
				if (ours != GL11C.glIsEnabled(key)) {
					bad.add("client array 0x" + Integer.toHexString(key) + " " + ours);
				}
			}
		}
		if (known(dirty, GlStateTracker.C_FOG)) {
			compare(bad, "fog mode", GlStateTracker.fogMode, GL11C.glGetInteger(GlStateTracker.GL_FOG_MODE));
			compare(bad, "fog density", GlStateTracker.fogDensity, GL11C.glGetFloat(GlStateTracker.GL_FOG_DENSITY));
			compare(bad, "fog start", GlStateTracker.fogStart, GL11C.glGetFloat(GlStateTracker.GL_FOG_START));
			compare(bad, "fog end", GlStateTracker.fogEnd, GL11C.glGetFloat(GlStateTracker.GL_FOG_END));
			float[] color = new float[4];
			GL11C.glGetFloatv(GlStateTracker.GL_FOG_COLOR, color);
			for (int i = 0; i < 4; i++) {
				compare(bad, "fog colour[" + i + "]", GlStateTracker.FOG_COLOR[i], color[i]);
			}
			if (GL.getCapabilities().GL_NV_fog_distance) {
				compare(bad, "fog distance mode", GlStateTracker.fogDistanceMode,
						GL11C.glGetInteger(GlStateTracker.GL_FOG_DISTANCE_MODE_NV));
			}
		}
		if (known(dirty, GlStateTracker.C_ALPHA)) {
			compare(bad, "alpha func", GlStateTracker.alphaFunc, GL11C.glGetInteger(GlStateTracker.GL_ALPHA_TEST_FUNC));
			compare(bad, "alpha ref", GlStateTracker.alphaRef, GL11C.glGetFloat(GlStateTracker.GL_ALPHA_TEST_REF));
		}
		if (known(dirty, GlStateTracker.C_BLEND)) {
			compare(bad, "blend src", GlStateTracker.blendSrc, GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB));
			compare(bad, "blend dst", GlStateTracker.blendDst, GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB));
		}
		if (known(dirty, GlStateTracker.C_DEPTH)) {
			compare(bad, "depth func", GlStateTracker.depthFunc, GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC));
			compare(bad, "depth mask", GlStateTracker.depthMask ? 1 : 0,
					GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK) ? 1 : 0);
			compare(bad, "polygon offset factor", GlStateTracker.polygonOffsetFactor,
					GL11C.glGetFloat(GL11C.GL_POLYGON_OFFSET_FACTOR));
			compare(bad, "polygon offset units", GlStateTracker.polygonOffsetUnits,
					GL11C.glGetFloat(GL11C.GL_POLYGON_OFFSET_UNITS));
		}
		if (known(dirty, GlStateTracker.C_CULL)) {
			compare(bad, "cull face", GlStateTracker.cullFace, GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE));
		}
		if (known(dirty, GlStateTracker.C_COLOR_MASK)) {
			ByteBuffer b = ByteBuffer.allocateDirect(4);
			GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, b);
			for (int i = 0; i < 4; i++) {
				compare(bad, "colour mask[" + i + "]", GlStateTracker.COLOR_MASK[i] ? 1 : 0, b.get(i) != 0 ? 1 : 0);
			}
		}
		if (known(dirty, GlStateTracker.C_PIXEL_STORE)) {
			for (int i = 0; i < GlStateTracker.PIXEL_STORE_PNAMES.length; i++) {
				compare(bad, "pixel store 0x" + Integer.toHexString(GlStateTracker.PIXEL_STORE_PNAMES[i]),
						GlStateTracker.PIXEL_STORE[i], GL11C.glGetInteger(GlStateTracker.PIXEL_STORE_PNAMES[i]));
			}
		}
		if (known(dirty, GlStateTracker.C_LIST_BASE)) {
			compare(bad, "list base", GlStateTracker.listBase, GL11C.glGetInteger(GlStateTracker.GL_LIST_BASE));
		}
		if (!bad.isEmpty()) {
			mismatches += bad.size();
			for (String b : bad) {
				String field = b.substring(0, Math.max(1, b.indexOf(' ')));
				BY_FIELD.merge(field, 1, Integer::sum);
			}
			if (detailLines < DETAIL_LINES) {
				detailLines++;
				Log.warn("tracker check at " + where + ": " + bad);
			}
		}
	}

	/** Totals, logged at shutdown. */
	public static void summary() {
		Log.info("tracker check: " + checks + " checks, " + mismatches + " mismatches" + (BY_FIELD.isEmpty() ? ""
				: " " + BY_FIELD) + ", " + unknown + " category reads skipped as unknown");
	}

	private static boolean known(int dirty, int category) {
		if ((dirty & category) != 0) {
			unknown++;
			return false;
		}
		return true;
	}

	private static void compare(List<String> bad, String what, int ours, int gl) {
		if (ours != gl) {
			bad.add(what + " 0x" + Integer.toHexString(ours) + " vs gl 0x" + Integer.toHexString(gl));
		}
	}

	private static void compare(List<String> bad, String what, float ours, float gl) {
		if (Math.abs(ours - gl) > TOLERANCE * Math.max(1f, Math.abs(gl))) {
			bad.add(what + " " + ours + " vs gl " + gl);
		}
	}
}
