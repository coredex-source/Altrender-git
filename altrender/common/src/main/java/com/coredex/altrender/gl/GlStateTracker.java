/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.gl;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.coredex.altrender.debug.FrameStats;
import com.coredex.altrender.util.IntIntMap;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;

/**
 * Mirror of the fixed-function state the game sets through {@code GL11} (development.md 0.4, 1.4). The merged
 * {@code GL11} (and {@code GL13}, {@code ARBMultitexture}) call in here before each state call reaches the driver, so
 * every caller is seen without transforming game classes (docs/decisions.md, 0.6).
 * <p>
 * Tracked: the modelview, projection and texture (unit 0) matrix stacks, the matrix mode, enables (texture enables per
 * unit), fog, alpha test, blend function, depth function and mask, polygon offset, cull face, colour mask, 2D texture
 * bindings per unit
 * and the active unit, client arrays, and the display-list base.
 * <p>
 * Display lists: calls made while compiling a list ({@code GL_COMPILE}) don't change state; the tracker notes which
 * categories the list touches (matrix ops inside a balanced push/pop don't count). Calling such a list makes those
 * categories <em>unknown</em> until they are next read, which reads them back from the driver (a sync point, so the
 * tracker only does it when asked). The game's glyph lists translate without popping; its terrain lists are balanced.
 * {@code glPopAttrib} likewise makes the categories it restores unknown.
 * <p>
 * Render thread only.
 */
public final class GlStateTracker {

	// Categories (dirty bits and list touch bits).
	public static final int C_MODELVIEW = 1;
	public static final int C_PROJECTION = 1 << 1;
	public static final int C_TEXTURE_MATRIX = 1 << 2;
	public static final int C_MATRIX_MODE = 1 << 3;
	public static final int C_ENABLES = 1 << 4;
	public static final int C_FOG = 1 << 5;
	public static final int C_ALPHA = 1 << 6;
	public static final int C_BLEND = 1 << 7;
	public static final int C_DEPTH = 1 << 8;
	public static final int C_CULL = 1 << 9;
	public static final int C_COLOR_MASK = 1 << 10;
	public static final int C_TEXTURES = 1 << 11;
	public static final int C_CLIENT = 1 << 12;
	public static final int C_LIST_BASE = 1 << 13;
	/** A list that leaves a matrix stack deeper or shallower than it found it. */
	private static final int C_STACK_DEPTH = 1 << 14;
	public static final int C_PIXEL_STORE = 1 << 15;
	public static final int C_MATRICES = C_MODELVIEW | C_PROJECTION | C_TEXTURE_MATRIX;
	public static final int C_ALL = (1 << 16) - 1;
	static final String[] CATEGORY_NAMES = {"modelview", "projection", "texture matrix", "matrix mode", "enables", "fog",
			"alpha", "blend", "depth", "cull", "colour mask", "textures", "client arrays", "list base", "stack depth",
			"pixel store"};
	/** Pixel-store parameters tracked: pack/unpack alignment, row length, skip pixels, skip rows. */
	static final int[] PIXEL_STORE_PNAMES = {0x0D05, 0x0D02, 0x0D03, 0x0D04, 0x0CF5, 0x0CF2, 0x0CF3, 0x0CF4};
	static final int[] PIXEL_STORE = {4, 0, 0, 0, 4, 0, 0, 0};

	private static final int MV = 0;
	private static final int PROJ = 1;
	private static final int TEX = 2;
	private static final int[] STACK_DEPTH_ENUMS = {0x0BA3, 0x0BA4, 0x0BA5}; // GL_*_STACK_DEPTH
	private static final int[] MAX_DEPTH_ENUMS = {0x0D36, 0x0D38, 0x0D39}; // GL_MAX_*_STACK_DEPTH
	private static final int[] MATRIX_ENUMS = {0x0BA6, 0x0BA7, 0x0BA8}; // GL_MODELVIEW/PROJECTION/TEXTURE_MATRIX

	// GL enums the core classes don't have (compatibility profile).
	static final int GL_MODELVIEW = 0x1700;
	static final int GL_PROJECTION = 0x1701;
	static final int GL_TEXTURE = 0x1702;
	static final int GL_COMPILE = 0x1300;
	static final int GL_COMPILE_AND_EXECUTE = 0x1301;
	static final int GL_FOG_INDEX = 0x0B61;
	static final int GL_FOG_DENSITY = 0x0B62;
	static final int GL_FOG_START = 0x0B63;
	static final int GL_FOG_END = 0x0B64;
	static final int GL_FOG_MODE = 0x0B65;
	static final int GL_FOG_COLOR = 0x0B66;
	static final int GL_FOG_COORD_SRC = 0x8450;
	static final int GL_FOG_DISTANCE_MODE_NV = 0x855A;
	static final int GL_EXP = 0x0800;
	static final int GL_FRAGMENT_DEPTH = 0x8452;
	static final int GL_EYE_PLANE_ABSOLUTE_NV = 0x855C;
	static final int GL_MATRIX_MODE = 0x0BA0;
	static final int GL_ALPHA_TEST_FUNC = 0x0BC1;
	static final int GL_ALPHA_TEST_REF = 0x0BC2;
	static final int GL_LIST_BASE = 0x0B32;
	private static final int GL_DITHER = 0x0BD0;
	private static final int GL_MULTISAMPLE = 0x809D;

	/** Stack limits; the GL minimums until the context is read. */
	private static final int[] MAX_DEPTH = {32, 2, 2};
	private static int matrixMode = GL_MODELVIEW;
	private static final float[][] STACKS = new float[3][64 * 16];
	private static final boolean[][] UNKNOWN = new boolean[3][64];
	private static final int[] TOP = new int[3];
	private static final boolean[] DEPTH_UNKNOWN = new boolean[3];

	/** Non-matrix categories whose values are unknown (read back on demand). */
	private static int dirty;
	/**
	 * Redundant state calls are skipped (development.md 3.4): a call that sets what the tracker knows is already set,
	 * outside display-list compiles. {@code -Daltrender.debug.stateDedupe.disable} sends every call.
	 */
	private static final boolean DEDUPE = !Boolean.getBoolean("altrender.debug.stateDedupe.disable");
	/** Categories set through the tracker at least once (never skip against an assumed default). */
	private static int known;
	/** Per texture unit: a 2D binding set through the tracker. */
	private static final boolean[] BOUND_KNOWN = new boolean[32];

	/** Enables: key = cap, or cap | (unit + 1) << 20 for per-unit texture caps; value 0/1. */
	static final IntIntMap ENABLES = new IntIntMap();
	static final IntIntMap CLIENT = new IntIntMap();

	static int fogMode = GL_EXP;
	static float fogDensity = 1f;
	static float fogStart = 0f;
	static float fogEnd = 1f;
	static final float[] FOG_COLOR = new float[4];
	static int fogDistanceMode = GL_EYE_PLANE_ABSOLUTE_NV;
	static int fogCoordSrc = GL_FRAGMENT_DEPTH;
	static int alphaFunc = GL11C.GL_ALWAYS;
	static float alphaRef;
	static int blendSrc = GL11C.GL_ONE;
	static int blendDst = GL11C.GL_ZERO;
	static int depthFunc = GL11C.GL_LESS;
	static boolean depthMask = true;
	static float polygonOffsetFactor;
	static float polygonOffsetUnits;
	static int cullFace = GL11C.GL_BACK;
	static final boolean[] COLOR_MASK = {true, true, true, true};
	static int activeUnit;
	static final int[] BOUND_2D = new int[32];
	static int unitsUsed = 1;
	static int listBase;

	private static int compiling;
	private static boolean compileExecute;
	private static int touch;
	private static int listMode;
	private static final int[] COMPILE_DEPTH = new int[3];
	/** List name → categories it touches (only lists that touch something). */
	private static final IntIntMap LIST_TOUCH = new IntIntMap();
	private static int anyListTouch;

	private static final int[] ATTRIB_STACK = new int[32];
	private static int attribDepth;
	private static final int[] CLIENT_ATTRIB_STACK = new int[32];
	private static int clientAttribDepth;

	private static final float[] TMP = new float[16];
	private static final float[] TMP2 = new float[16];

	static {
		for (int m = 0; m < 3; m++) {
			identity(STACKS[m], 0);
		}
	}

	private GlStateTracker() {
	}

	/** The context exists: read the real stack limits (called once from the renderer install). */
	public static void contextCreated() {
		for (int m = 0; m < 3; m++) {
			MAX_DEPTH[m] = Math.min(64, GL11C.glGetInteger(MAX_DEPTH_ENUMS[m]));
		}
	}

	// ---- matrices ----

	public static void matrixMode(int mode) {
		if (compiling != 0) {
			touch |= C_MATRIX_MODE;
			listMode = mode;
			if (!compileExecute) {
				return;
			}
		}
		matrixMode = mode;
	}

	public static void loadIdentity() {
		int m = matrixOp(true);
		if (m >= 0) {
			identity(STACKS[m], TOP[m] * 16);
			UNKNOWN[m][TOP[m]] = false;
		}
	}

	public static void pushMatrix() {
		if (compiling != 0) {
			int lm = index(listMode);
			if (lm >= 0) {
				COMPILE_DEPTH[lm]++;
			}
			if (!compileExecute) {
				return;
			}
		}
		int m = index(matrixMode);
		if (m < 0 || TOP[m] + 1 >= MAX_DEPTH[m]) {
			return; // GL_STACK_OVERFLOW: no change
		}
		System.arraycopy(STACKS[m], TOP[m] * 16, STACKS[m], TOP[m] * 16 + 16, 16);
		UNKNOWN[m][TOP[m] + 1] = UNKNOWN[m][TOP[m]];
		TOP[m]++;
	}

	public static void popMatrix() {
		if (compiling != 0) {
			int lm = index(listMode);
			if (lm < 0) {
				touch |= C_MATRICES | C_STACK_DEPTH;
			} else if (COMPILE_DEPTH[lm] > 0) {
				COMPILE_DEPTH[lm]--;
			} else {
				touch |= 1 << lm | C_STACK_DEPTH;
			}
			if (!compileExecute) {
				return;
			}
		}
		int m = index(matrixMode);
		if (m < 0 || TOP[m] == 0) {
			return; // GL_STACK_UNDERFLOW: no change
		}
		TOP[m]--;
	}

	public static void translate(float x, float y, float z) {
		int m = matrixOp(false);
		if (m >= 0) {
			float[] a = STACKS[m];
			int o = TOP[m] * 16;
			for (int r = 0; r < 4; r++) {
				a[o + 12 + r] += a[o + r] * x + a[o + 4 + r] * y + a[o + 8 + r] * z;
			}
		}
	}

	public static void scale(float x, float y, float z) {
		int m = matrixOp(false);
		if (m >= 0) {
			float[] a = STACKS[m];
			int o = TOP[m] * 16;
			for (int r = 0; r < 4; r++) {
				a[o + r] *= x;
				a[o + 4 + r] *= y;
				a[o + 8 + r] *= z;
			}
		}
	}

	public static void rotate(float angle, float x, float y, float z) {
		int m = matrixOp(false);
		if (m < 0) {
			return;
		}
		float mag = (float) Math.sqrt(x * x + y * y + z * z);
		if (mag <= 1.0e-4f) {
			return; // no rotation (as Mesa)
		}
		x /= mag;
		y /= mag;
		z /= mag;
		double rad = angle * (Math.PI / 180.0);
		float s = (float) Math.sin(rad);
		float c = (float) Math.cos(rad);
		float t = 1f - c;
		float[] r = TMP;
		r[0] = x * x * t + c;
		r[1] = y * x * t + z * s;
		r[2] = x * z * t - y * s;
		r[3] = 0f;
		r[4] = x * y * t - z * s;
		r[5] = y * y * t + c;
		r[6] = y * z * t + x * s;
		r[7] = 0f;
		r[8] = x * z * t + y * s;
		r[9] = y * z * t - x * s;
		r[10] = z * z * t + c;
		r[11] = 0f;
		r[12] = 0f;
		r[13] = 0f;
		r[14] = 0f;
		r[15] = 1f;
		multiply(STACKS[m], TOP[m] * 16, r);
	}

	public static void ortho(double l, double r, double b, double t, double n, double f) {
		int m = matrixOp(false);
		if (m >= 0) {
			float[] o = TMP;
			java.util.Arrays.fill(o, 0f);
			o[0] = (float) (2.0 / (r - l));
			o[5] = (float) (2.0 / (t - b));
			o[10] = (float) (-2.0 / (f - n));
			o[12] = (float) (-(r + l) / (r - l));
			o[13] = (float) (-(t + b) / (t - b));
			o[14] = (float) (-(f + n) / (f - n));
			o[15] = 1f;
			multiply(STACKS[m], TOP[m] * 16, o);
		}
	}

	public static void frustum(double l, double r, double b, double t, double n, double f) {
		int m = matrixOp(false);
		if (m >= 0) {
			float[] o = TMP;
			java.util.Arrays.fill(o, 0f);
			o[0] = (float) (2.0 * n / (r - l));
			o[5] = (float) (2.0 * n / (t - b));
			o[8] = (float) ((r + l) / (r - l));
			o[9] = (float) ((t + b) / (t - b));
			o[10] = (float) (-(f + n) / (f - n));
			o[11] = -1f;
			o[14] = (float) (-2.0 * f * n / (f - n));
			multiply(STACKS[m], TOP[m] * 16, o);
		}
	}

	public static void loadMatrix(FloatBuffer b) {
		int m = matrixOp(true);
		if (m >= 0) {
			int p = b.position();
			for (int i = 0; i < 16; i++) {
				STACKS[m][TOP[m] * 16 + i] = b.get(p + i);
			}
			UNKNOWN[m][TOP[m]] = false;
		}
	}

	public static void loadMatrix(float[] a) {
		loadMatrix(FloatBuffer.wrap(a));
	}

	public static void loadMatrix(DoubleBuffer b) {
		int m = matrixOp(true);
		if (m >= 0) {
			int p = b.position();
			for (int i = 0; i < 16; i++) {
				STACKS[m][TOP[m] * 16 + i] = (float) b.get(p + i);
			}
			UNKNOWN[m][TOP[m]] = false;
		}
	}

	public static void loadMatrix(double[] a) {
		loadMatrix(DoubleBuffer.wrap(a));
	}

	public static void multMatrix(FloatBuffer b) {
		int m = matrixOp(false);
		if (m >= 0) {
			int p = b.position();
			for (int i = 0; i < 16; i++) {
				TMP[i] = b.get(p + i);
			}
			multiply(STACKS[m], TOP[m] * 16, TMP);
		}
	}

	public static void multMatrix(float[] a) {
		multMatrix(FloatBuffer.wrap(a));
	}

	public static void multMatrix(DoubleBuffer b) {
		int m = matrixOp(false);
		if (m >= 0) {
			int p = b.position();
			for (int i = 0; i < 16; i++) {
				TMP[i] = (float) b.get(p + i);
			}
			multiply(STACKS[m], TOP[m] * 16, TMP);
		}
	}

	public static void multMatrix(double[] a) {
		multMatrix(DoubleBuffer.wrap(a));
	}

	/**
	 * Copies the top of a matrix stack into {@code out} (column-major), reading it back from the driver first if a
	 * display list or glPopAttrib left it unknown.
	 *
	 * @param mode {@code GL_MODELVIEW}, {@code GL_PROJECTION} or {@code GL_TEXTURE} (unit 0)
	 */
	public static void matrix(int mode, float[] out) {
		int m = index(mode);
		if (m == TEX && activeUnit != 0) {
			m = -1;
		}
		if (m < 0) {
			throw new IllegalArgumentException("untracked matrix mode " + mode);
		}
		refreshMatrix(m);
		System.arraycopy(STACKS[m], TOP[m] * 16, out, 0, 16);
	}

	public static int matrixMode() {
		refresh(C_MATRIX_MODE);
		return matrixMode;
	}

	// ---- enables and client arrays ----

	/**
	 * Told when the game switches texturing (unit 0), fog or lighting, or sets a fog parameter (development.md 6.1: a
	 * shader pack's program follows the fixed-function state, as OptiFine's did). Render thread; null for none.
	 */
	public interface Listener {
		void enabled(int cap, boolean on);

		void fogParameter();
	}

	private static Listener listener;

	public static void listener(Listener l) {
		listener = l;
	}

	private static boolean alphaSuppressed;

	/**
	 * The game's alpha test kept from the driver while a world pipeline draws ({@code true}): the pipeline tests alpha
	 * in its programs, as Iris (in the compatibility profile the fixed-function test would still cut the programs'
	 * first output). The game's alpha test state is still tracked and comes back with {@code false}.
	 */
	public static void suppressAlphaTest(boolean on) {
		if (alphaSuppressed == on) {
			return;
		}
		alphaSuppressed = on;
		if (on) {
			GL11C.glDisable(0x0BC0);
		} else if (isEnabled(0x0BC0)) {
			GL11C.glEnable(0x0BC0);
		}
	}

	/** For Altrender's own draws that rely on the game's alpha test: on in the driver if the game has it on. */
	public static boolean liftAlphaSuppression() {
		if (alphaSuppressed && isEnabled(0x0BC0)) {
			GL11C.glEnable(0x0BC0);
			return true;
		}
		return false;
	}

	/** After {@link #liftAlphaSuppression}: off in the driver again. */
	public static void restoreAlphaSuppression() {
		GL11C.glDisable(0x0BC0);
	}

	/** A pipeline's blend for its program: null (the game's own), {0} (off) or {1, src, dst, srcAlpha, dstAlpha}. */
	private static int[] blendOverride;

	/**
	 * The driver's blending for the program that draws now: {@code override} (see {@link #blendOverride}) instead of
	 * the game's, whose calls are still tracked; null gives the game's own back.
	 */
	public static void overrideBlend(int[] override) {
		blendOverride = override;
		applyBlend();
	}

	/** Whether blending is the pipeline's now. */
	public static boolean blendOverridden() {
		return blendOverride != null;
	}

	/** Puts the driver's blending to the override, or to the game's tracked state (then the per-buffer ones). */
	public static void applyBlend() {
		int[] o = blendOverride;
		if (o != null) {
			if (o[0] == 0) {
				GL11C.glDisable(GL11C.GL_BLEND);
			} else {
				GL11C.glEnable(GL11C.GL_BLEND);
				org.lwjgl.opengl.GL14C.glBlendFuncSeparate(o[1], o[2], o[3], o[4]);
			}
		} else {
			if (isEnabled(GL11C.GL_BLEND)) {
				GL11C.glEnable(GL11C.GL_BLEND);
			} else {
				GL11C.glDisable(GL11C.GL_BLEND);
			}
			GL11C.glBlendFunc(blendSrc(), blendDst());
		}
		applyBufferBlends();
	}

	/** A pipeline's blend per draw buffer, over the rest (index: attachment; null entries: as the rest), or null. */
	private static int[][] bufferBlends;
	private static boolean indexedFuncWarned;

	/**
	 * The driver's blending per draw buffer of the program that draws now (a pack's {@code blend.<program>.<buffer>}):
	 * entry {@code i} for attachment {@code i}, each null (as the rest), {0} (off) or {1, src, dst, srcAlpha,
	 * dstAlpha}; null or empty: none.
	 */
	public static void overrideBufferBlends(int[][] perAttachment) {
		int[][] before = bufferBlends;
		bufferBlends = perAttachment == null || perAttachment.length == 0 ? null : perAttachment;
		if (before != null || bufferBlends != null) {
			applyBlend(); // a global call sets every buffer: the old per-buffer state goes, the new one comes after
		}
	}

	/**
	 * The per-buffer blends again, after a global blend call reached the driver (which sets every buffer). Functions
	 * need GL 4.0 or {@code ARB_draw_buffers_blend}; without, only on/off applies (told once).
	 */
	public static void applyBufferBlends() {
		int[][] b = bufferBlends;
		if (b == null) {
			return;
		}
		org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
		for (int i = 0; i < b.length; i++) {
			int[] o = b[i];
			if (o == null) {
				continue;
			}
			if (o[0] == 0) {
				org.lwjgl.opengl.GL30C.glDisablei(GL11C.GL_BLEND, i);
				continue;
			}
			if (caps.OpenGL40) {
				org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(i, o[1], o[2], o[3], o[4]);
			} else if (caps.GL_ARB_draw_buffers_blend) {
				org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i, o[1], o[2], o[3], o[4]);
			} else if (!indexedFuncWarned) {
				indexedFuncWarned = true;
				com.coredex.altrender.Log.warn("per-buffer blend functions need GL 4.0 or ARB_draw_buffers_blend: "
						+ "those buffers blend as the rest");
			}
			org.lwjgl.opengl.GL30C.glEnablei(GL11C.GL_BLEND, i);
		}
	}

	/** Whether the game's enable or disable of {@code cap} must not reach the driver now (a pipeline owns it). */
	public static boolean suppressed(int cap) {
		return compiling == 0 && (alphaSuppressed && cap == 0x0BC0 || blendOverride != null && cap == GL11C.GL_BLEND);
	}

	/** @return whether the call must reach the driver (false: provably redundant, skipped) */
	public static boolean enable(int cap) {
		return set(cap, true);
	}

	public static boolean disable(int cap) {
		return set(cap, false);
	}

	private static boolean set(int cap, boolean on) {
		if (compiling != 0) {
			touch |= C_ENABLES;
			if (compileExecute) {
				ENABLES.put(enableKey(cap), on ? 1 : 0);
			}
			return true;
		}
		int key = enableKey(cap);
		int v = on ? 1 : 0;
		if (DEDUPE && (dirty & C_ENABLES) == 0 && ENABLES.get(key, -1) == v) {
			FrameStats.skippedCalls++;
			return false;
		}
		ENABLES.put(key, v);
		Listener l = listener;
		if (l != null && (cap == 0x0B60 || cap == 0x0B50 || cap == 0x0BC0 || cap == 0x0DE1 && activeUnit == 0)) {
			l.enabled(cap, on);
		}
		return true;
	}

	/** Whether a value call can be skipped: same value, known, not dirty, not compiling. Counts the skip. */
	private static boolean redundant(int category, boolean same) {
		if (DEDUPE && same && compiling == 0 && (known & category) != 0 && (dirty & category) == 0) {
			FrameStats.skippedCalls++;
			return true;
		}
		return false;
	}

	private static void known(int category) {
		if (compiling == 0) {
			known |= category;
		}
	}

	public static boolean isEnabled(int cap) {
		refresh(C_ENABLES);
		int key = enableKey(cap);
		int v = ENABLES.get(key, -1);
		return v < 0 ? cap == GL_DITHER || cap == GL_MULTISAMPLE : v == 1;
	}

	public static void enableClient(int array) {
		setClient(array, true);
	}

	public static void disableClient(int array) {
		setClient(array, false);
	}

	private static void setClient(int array, boolean on) {
		// Client state isn't compiled into display lists: it always applies at once.
		CLIENT.put(array, on ? 1 : 0);
	}

	// ---- fog, alpha, blend, depth, cull, colour mask ----

	public static void fog(int pname, float value) {
		if (stateOp(C_FOG)) {
			fogChanged();
			switch (pname) {
				case GL_FOG_MODE -> fogMode = (int) value;
				case GL_FOG_DENSITY -> fogDensity = value;
				case GL_FOG_START -> fogStart = value;
				case GL_FOG_END -> fogEnd = value;
				case GL_FOG_DISTANCE_MODE_NV -> fogDistanceMode = (int) value;
				case GL_FOG_COORD_SRC -> fogCoordSrc = (int) value;
				default -> {
				}
			}
		}
	}

	public static void fog(int pname, FloatBuffer values) {
		if (pname == GL_FOG_COLOR) {
			if (stateOp(C_FOG)) {
				fogChanged();
				int p = values.position();
				for (int i = 0; i < 4; i++) {
					FOG_COLOR[i] = values.get(p + i);
				}
			}
		} else {
			fog(pname, values.get(values.position()));
		}
	}

	public static void fog(int pname, float[] values) {
		fog(pname, FloatBuffer.wrap(values));
	}

	public static void fog(int pname, IntBuffer values) {
		if (pname == GL_FOG_COLOR) {
			if (stateOp(C_FOG)) {
				int p = values.position();
				for (int i = 0; i < 4; i++) {
					// Integer colours map [-2^31, 2^31 - 1] to [-1, 1].
					FOG_COLOR[i] = (float) ((2.0 * values.get(p + i) + 1.0) / 4294967295.0);
				}
			}
		} else {
			fog(pname, (float) values.get(values.position()));
		}
	}

	public static void fog(int pname, int[] values) {
		fog(pname, IntBuffer.wrap(values));
	}

	public static boolean alphaFunc(int func, float ref) {
		float r = Math.max(0f, Math.min(1f, ref));
		if (redundant(C_ALPHA, alphaFunc == func && alphaRef == r)) {
			return false;
		}
		if (stateOp(C_ALPHA)) {
			alphaFunc = func;
			alphaRef = r;
			known(C_ALPHA);
		}
		return true;
	}

	public static boolean blendFunc(int src, int dst) {
		if (redundant(C_BLEND, blendSrc == src && blendDst == dst)) {
			return false;
		}
		if (stateOp(C_BLEND)) {
			blendSrc = src;
			blendDst = dst;
			known(C_BLEND);
		}
		return true;
	}

	/** Depth func and mask are known separately (both in C_DEPTH for dirtiness). */
	private static boolean depthFuncKnown;
	private static boolean depthMaskKnown;

	public static boolean depthFunc(int func) {
		if (depthFuncKnown && redundant(C_DEPTH, depthFunc == func)) {
			return false;
		}
		if (stateOp(C_DEPTH)) {
			depthFunc = func;
			depthFuncKnown = compiling == 0 || depthFuncKnown;
			known(C_DEPTH);
		}
		return true;
	}

	public static boolean depthMask(boolean mask) {
		if (depthMaskKnown && redundant(C_DEPTH, depthMask == mask)) {
			return false;
		}
		if (stateOp(C_DEPTH)) {
			depthMask = mask;
			depthMaskKnown = compiling == 0 || depthMaskKnown;
			known(C_DEPTH);
		}
		return true;
	}

	public static void polygonOffset(float factor, float units) {
		if (stateOp(C_DEPTH)) {
			polygonOffsetFactor = factor;
			polygonOffsetUnits = units;
		}
	}

	public static boolean cullFace(int mode) {
		if (redundant(C_CULL, cullFace == mode)) {
			return false;
		}
		if (stateOp(C_CULL)) {
			cullFace = mode;
			known(C_CULL);
		}
		return true;
	}

	public static boolean colorMask(boolean r, boolean g, boolean b, boolean a) {
		if (redundant(C_COLOR_MASK, COLOR_MASK[0] == r && COLOR_MASK[1] == g && COLOR_MASK[2] == b && COLOR_MASK[3] == a)) {
			return false;
		}
		if (stateOp(C_COLOR_MASK)) {
			COLOR_MASK[0] = r;
			COLOR_MASK[1] = g;
			COLOR_MASK[2] = b;
			COLOR_MASK[3] = a;
			known(C_COLOR_MASK);
		}
		return true;
	}

	// ---- pixel store (not compiled into display lists) ----

	public static void pixelStore(int pname, int value) {
		for (int i = 0; i < PIXEL_STORE_PNAMES.length; i++) {
			if (PIXEL_STORE_PNAMES[i] == pname) {
				PIXEL_STORE[i] = value;
			}
		}
	}

	/** The game's value of a tracked pixel-store parameter (e.g. {@code GL_UNPACK_ALIGNMENT}). */
	public static int pixelStoreValue(int pname) {
		refresh(C_PIXEL_STORE);
		for (int i = 0; i < PIXEL_STORE_PNAMES.length; i++) {
			if (PIXEL_STORE_PNAMES[i] == pname) {
				return PIXEL_STORE[i];
			}
		}
		throw new IllegalArgumentException("untracked pixel-store parameter 0x" + Integer.toHexString(pname));
	}

	// ---- textures ----

	public static boolean bindTexture(int target, int texture) {
		if (target != GL11C.GL_TEXTURE_2D) {
			return true;
		}
		if (BOUND_KNOWN[activeUnit] && redundant(C_TEXTURES, BOUND_2D[activeUnit] == texture)) {
			return false;
		}
		if (stateOp(C_TEXTURES)) {
			BOUND_2D[activeUnit] = texture;
			if (compiling == 0) {
				BOUND_KNOWN[activeUnit] = true;
				known(C_TEXTURES);
			}
		}
		return true;
	}

	public static void activeTexture(int unit) {
		if (stateOp(C_TEXTURES)) {
			int u = unit - GL13C.GL_TEXTURE0;
			if (u >= 0 && u < BOUND_2D.length) {
				activeUnit = u;
				unitsUsed = Math.max(unitsUsed, u + 1);
			}
		}
	}

	/** Deleting a bound texture unbinds it (not compiled into lists). */
	public static void deleteTexture(int texture) {
		for (int u = 0; u < unitsUsed; u++) {
			if (BOUND_2D[u] == texture) {
				BOUND_2D[u] = 0;
			}
		}
	}

	public static void deleteTextures(IntBuffer textures) {
		for (int i = textures.position(); i < textures.limit(); i++) {
			deleteTexture(textures.get(i));
		}
	}

	public static void deleteTextures(int[] textures) {
		for (int t : textures) {
			deleteTexture(t);
		}
	}

	public static int boundTexture2D() {
		refresh(C_TEXTURES);
		return BOUND_2D[activeUnit];
	}

	public static int boundTexture2D(int unit) {
		refresh(C_TEXTURES);
		return unit < BOUND_2D.length ? BOUND_2D[unit] : 0;
	}

	public static int blendSrc() {
		refresh(C_BLEND);
		return blendSrc;
	}

	public static int blendDst() {
		refresh(C_BLEND);
		return blendDst;
	}

	public static int depthFunc() {
		refresh(C_DEPTH);
		return depthFunc;
	}

	public static boolean depthMask() {
		refresh(C_DEPTH);
		return depthMask;
	}

	public static float polygonOffsetFactor() {
		refresh(C_DEPTH);
		return polygonOffsetFactor;
	}

	public static float polygonOffsetUnits() {
		refresh(C_DEPTH);
		return polygonOffsetUnits;
	}

	public static boolean colorMask(int channel) {
		refresh(C_COLOR_MASK);
		return COLOR_MASK[channel];
	}

	public static int cullFace() {
		refresh(C_CULL);
		return cullFace;
	}

	public static int activeUnit() {
		refresh(C_TEXTURES);
		return activeUnit;
	}

	public static int fogMode() {
		refresh(C_FOG);
		return fogMode;
	}

	public static float fogStart() {
		refresh(C_FOG);
		return fogStart;
	}

	public static float fogEnd() {
		refresh(C_FOG);
		return fogEnd;
	}

	public static float fogDensity() {
		refresh(C_FOG);
		return fogDensity;
	}

	public static void fogColor(float[] out) {
		refresh(C_FOG);
		System.arraycopy(FOG_COLOR, 0, out, 0, 4);
	}

	public static int fogDistanceMode() {
		refresh(C_FOG);
		return fogDistanceMode;
	}

	public static int alphaFunc() {
		refresh(C_ALPHA);
		return alphaFunc;
	}

	public static float alphaRef() {
		refresh(C_ALPHA);
		return alphaRef;
	}

	// ---- display lists ----

	public static void newList(int list, int mode) {
		if (compiling != 0) {
			return; // GL_INVALID_OPERATION
		}
		compiling = list;
		compileExecute = mode == GL_COMPILE_AND_EXECUTE;
		com.coredex.altrender.immediate.ListCapture.onNewList(list);
		touch = 0;
		listMode = matrixMode;
		java.util.Arrays.fill(COMPILE_DEPTH, 0);
	}

	public static void endList() {
		if (compiling == 0) {
			return;
		}
		for (int m = 0; m < 3; m++) {
			if (COMPILE_DEPTH[m] != 0) {
				touch |= 1 << m | C_STACK_DEPTH;
			}
		}
		com.coredex.altrender.immediate.ListCapture.onEndList(compiling, touch);
		if (touch != 0) {
			LIST_TOUCH.put(compiling, touch);
			anyListTouch |= touch;
		} else {
			LIST_TOUCH.remove(compiling);
		}
		compiling = 0;
	}

	public static boolean compiling() {
		return compiling != 0;
	}

	public static void callList(int list) {
		if (compiling != 0) {
			touch |= LIST_TOUCH.get(list, 0);
			return;
		}
		FrameStats.drawCalls++;
		if (!LIST_TOUCH.isEmpty()) {
			called(LIST_TOUCH.get(list, 0));
		}
	}

	public static void callLists(int type, ByteBuffer lists) {
		int n = lists.remaining();
		switch (type) {
			case GL11C.GL_UNSIGNED_SHORT, GL11C.GL_SHORT -> n /= 2;
			case GL11C.GL_UNSIGNED_INT, GL11C.GL_INT, GL11C.GL_FLOAT -> n /= 4;
			default -> {
			}
		}
		int touched = 0;
		if (!LIST_TOUCH.isEmpty()) {
			int p = lists.position();
			for (int i = 0; i < n; i++) {
				int id = switch (type) {
					case GL11C.GL_BYTE -> lists.get(p + i);
					case GL11C.GL_UNSIGNED_BYTE -> lists.get(p + i) & 0xFF;
					case GL11C.GL_SHORT -> lists.getShort(p + i * 2);
					case GL11C.GL_UNSIGNED_SHORT -> lists.getShort(p + i * 2) & 0xFFFF;
					case GL11C.GL_INT, GL11C.GL_UNSIGNED_INT -> lists.getInt(p + i * 4);
					default -> -1;
				};
				touched |= id < 0 ? anyListTouch : LIST_TOUCH.get(listBase + id, 0);
			}
		}
		lists(n, touched);
	}

	public static void callLists(ShortBuffer lists) {
		int touched = 0;
		if (!LIST_TOUCH.isEmpty()) {
			for (int i = lists.position(); i < lists.limit(); i++) {
				touched |= LIST_TOUCH.get(listBase + (lists.get(i) & 0xFFFF), 0);
			}
		}
		lists(lists.remaining(), touched);
	}

	public static void callLists(IntBuffer lists) {
		int touched = 0;
		if (!LIST_TOUCH.isEmpty()) {
			for (int i = lists.position(); i < lists.limit(); i++) {
				touched |= LIST_TOUCH.get(listBase + lists.get(i), 0);
			}
		}
		lists(lists.remaining(), touched);
	}

	private static void lists(int n, int touched) {
		if (compiling != 0) {
			touch |= touched;
			return;
		}
		FrameStats.drawCalls += n;
		called(touched);
	}

	public static void listBase(int base) {
		if (stateOp(C_LIST_BASE)) {
			listBase = base;
		}
	}

	public static void deleteLists(int list, int range) {
		com.coredex.altrender.immediate.ListCapture.onDeleteLists(list, range);
		if (LIST_TOUCH.isEmpty() || range <= 0) {
			return;
		}
		for (int key : LIST_TOUCH.keys()) {
			if (key >= list && key - list < range) {
				LIST_TOUCH.remove(key);
			}
		}
	}

	// ---- attribute stacks ----

	public static void pushAttrib(int mask) {
		if (compiling != 0) {
			touch |= attribCategories(mask);
			if (!compileExecute) {
				return;
			}
		}
		if (attribDepth < ATTRIB_STACK.length) {
			ATTRIB_STACK[attribDepth] = mask;
		}
		attribDepth++;
	}

	public static void popAttrib() {
		if (compiling != 0) {
			touch |= C_ALL & ~C_MATRICES & ~C_STACK_DEPTH;
			if (!compileExecute) {
				return;
			}
		}
		if (attribDepth > 0) {
			attribDepth--;
			int mask = attribDepth < ATTRIB_STACK.length ? ATTRIB_STACK[attribDepth] : 0xFFFFFFFF;
			dirty |= attribCategories(mask);
		}
	}

	public static void pushClientAttrib(int mask) {
		if (clientAttribDepth < CLIENT_ATTRIB_STACK.length) {
			CLIENT_ATTRIB_STACK[clientAttribDepth] = mask;
		}
		clientAttribDepth++;
	}

	public static void popClientAttrib() {
		if (clientAttribDepth > 0) {
			clientAttribDepth--;
			int mask = clientAttribDepth < CLIENT_ATTRIB_STACK.length ? CLIENT_ATTRIB_STACK[clientAttribDepth] : -1;
			if ((mask & 0x2) != 0) { // GL_CLIENT_VERTEX_ARRAY_BIT
				dirty |= C_CLIENT;
			}
			if ((mask & 0x1) != 0) { // GL_CLIENT_PIXEL_STORE_BIT
				dirty |= C_PIXEL_STORE;
			}
		}
	}

	// ---- draws and uploads (FrameStats) ----

	public static void draw() {
		if (compiling == 0) {
			FrameStats.drawCalls++;
		}
	}

	public static void upload() {
		FrameStats.uploads++;
	}

	// ---- internals ----

	/** Category bits a glPopAttrib with {@code mask} restores. */
	private static int attribCategories(int mask) {
		int c = 0;
		if ((mask & 0x8) != 0) { // POLYGON
			c |= C_CULL;
		}
		if ((mask & 0x80) != 0) { // FOG
			c |= C_FOG;
		}
		if ((mask & 0x100) != 0) { // DEPTH_BUFFER
			c |= C_DEPTH;
		}
		if ((mask & 0x1000) != 0) { // TRANSFORM
			c |= C_MATRIX_MODE;
		}
		if ((mask & 0x4000) != 0) { // COLOR_BUFFER
			c |= C_ALPHA | C_BLEND | C_COLOR_MASK;
		}
		if ((mask & 0x20000) != 0) { // LIST
			c |= C_LIST_BASE;
		}
		if ((mask & 0x40000) != 0) { // TEXTURE
			c |= C_TEXTURES;
		}
		// Every group saves its own enables (and ENABLE_BIT all of them).
		if ((mask & (0x2 | 0x4 | 0x8 | 0x40 | 0x80 | 0x100 | 0x400 | 0x1000 | 0x2000 | 0x4000 | 0x40000 | 0x80000
				| 0x20000000)) != 0) {
			c |= C_ENABLES;
		}
		return c;
	}

	/** Bookkeeping for a non-matrix state call; returns whether to apply it now. */
	private static void fogChanged() {
		Listener l = listener;
		if (l != null && compiling == 0) {
			l.fogParameter();
		}
	}

	private static boolean stateOp(int category) {
		if (compiling != 0) {
			touch |= category;
			return compileExecute;
		}
		return true;
	}

	/**
	 * Bookkeeping for a matrix call; returns the stack to change now, or -1.
	 *
	 * @param absolute whether it replaces the matrix (load) rather than multiplying it
	 */
	private static int matrixOp(boolean absolute) {
		if (compiling != 0) {
			int lm = index(listMode);
			if (lm < 0) {
				touch |= C_MATRICES;
			} else if (COMPILE_DEPTH[lm] == 0) {
				touch |= 1 << lm;
			}
			if (!compileExecute) {
				return -1;
			}
		}
		int m = index(matrixMode);
		if (m == TEX && activeUnit != 0) {
			return -1; // only unit 0's texture matrix is tracked
		}
		return m;
	}

	/** A list (or lists) touching {@code touched} ran: those categories are unknown now. */
	private static void called(int touched) {
		if (touched == 0) {
			return;
		}
		for (int m = 0; m < 3; m++) {
			if ((touched & 1 << m) != 0) {
				UNKNOWN[m][TOP[m]] = true;
				if ((touched & C_STACK_DEPTH) != 0) {
					DEPTH_UNKNOWN[m] = true;
				}
			}
		}
		dirty |= touched & ~(C_MATRICES | C_STACK_DEPTH);
	}

	private static int enableKey(int cap) {
		return switch (cap) {
			// per texture unit
			case 0x0DE0, 0x0DE1, 0x806F, 0x8513, 0x84F5, 0x0C60, 0x0C61, 0x0C62, 0x0C63 -> cap | (activeUnit + 1) << 20;
			default -> cap;
		};
	}

	private static int index(int mode) {
		return switch (mode) {
			case GL_MODELVIEW -> MV;
			case GL_PROJECTION -> PROJ;
			case GL_TEXTURE -> TEX;
			default -> -1;
		};
	}

	static void identity(float[] a, int o) {
		for (int i = 0; i < 16; i++) {
			a[o + i] = i % 5 == 0 ? 1f : 0f;
		}
	}

	/** a[o..o+15] = a[o..o+15] × n (column-major). */
	private static void multiply(float[] a, int o, float[] n) {
		float[] r = TMP2;
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				r[col * 4 + row] = a[o + row] * n[col * 4] + a[o + 4 + row] * n[col * 4 + 1]
						+ a[o + 8 + row] * n[col * 4 + 2] + a[o + 12 + row] * n[col * 4 + 3];
			}
		}
		System.arraycopy(r, 0, a, o, 16);
	}

	// ---- read-back of unknown state ----

	static boolean matrixUnknown(int m) {
		return UNKNOWN[m][TOP[m]] || DEPTH_UNKNOWN[m];
	}

	static int dirty() {
		return dirty;
	}

	private static void refreshMatrix(int m) {
		if (!matrixUnknown(m)) {
			return;
		}
		int saved = activeUnit;
		if (m == TEX && saved != 0) {
			GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
		}
		if (DEPTH_UNKNOWN[m]) {
			int depth = GL11C.glGetInteger(STACK_DEPTH_ENUMS[m]);
			TOP[m] = Math.max(0, Math.min(depth - 1, 63));
			// Entries below are unknown too: a pop reads them back.
			for (int i = 0; i < TOP[m]; i++) {
				UNKNOWN[m][i] = true;
			}
			DEPTH_UNKNOWN[m] = false;
		}
		float[] tmp = new float[16];
		GL11C.glGetFloatv(MATRIX_ENUMS[m], tmp);
		System.arraycopy(tmp, 0, STACKS[m], TOP[m] * 16, 16);
		UNKNOWN[m][TOP[m]] = false;
		if (m == TEX && saved != 0) {
			GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + saved);
		}
	}

	private static void refresh(int category) {
		if ((dirty & category) == 0) {
			return;
		}
		dirty &= ~category;
		switch (category) {
			case C_MATRIX_MODE -> matrixMode = GL11C.glGetInteger(GL_MATRIX_MODE);
			case C_ENABLES -> {
				refresh(C_TEXTURES); // per-unit keys switch units
				for (int key : ENABLES.keys()) {
					ENABLES.put(key, readEnable(key) ? 1 : 0);
				}
			}
			case C_FOG -> {
				fogMode = GL11C.glGetInteger(GL_FOG_MODE);
				fogDensity = GL11C.glGetFloat(GL_FOG_DENSITY);
				fogStart = GL11C.glGetFloat(GL_FOG_START);
				fogEnd = GL11C.glGetFloat(GL_FOG_END);
				GL11C.glGetFloatv(GL_FOG_COLOR, FOG_COLOR);
				if (org.lwjgl.opengl.GL.getCapabilities().GL_NV_fog_distance) {
					fogDistanceMode = GL11C.glGetInteger(GL_FOG_DISTANCE_MODE_NV);
				}
				fogCoordSrc = GL11C.glGetInteger(GL_FOG_COORD_SRC);
			}
			case C_ALPHA -> {
				alphaFunc = GL11C.glGetInteger(GL_ALPHA_TEST_FUNC);
				alphaRef = GL11C.glGetFloat(GL_ALPHA_TEST_REF);
			}
			case C_BLEND -> {
				blendSrc = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
				blendDst = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
			}
			case C_DEPTH -> {
				depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
				depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
				polygonOffsetFactor = GL11C.glGetFloat(GL11C.GL_POLYGON_OFFSET_FACTOR);
				polygonOffsetUnits = GL11C.glGetFloat(GL11C.GL_POLYGON_OFFSET_UNITS);
			}
			case C_CULL -> cullFace = GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE);
			case C_COLOR_MASK -> {
				ByteBuffer b = ByteBuffer.allocateDirect(4);
				GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, b);
				for (int i = 0; i < 4; i++) {
					COLOR_MASK[i] = b.get(i) != 0;
				}
			}
			case C_TEXTURES -> {
				activeUnit = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE) - GL13C.GL_TEXTURE0;
				for (int u = 0; u < unitsUsed; u++) {
					GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + u);
					BOUND_2D[u] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
				}
				GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + activeUnit);
			}
			case C_CLIENT -> {
				for (int key : CLIENT.keys()) {
					CLIENT.put(key, GL11C.glIsEnabled(key) ? 1 : 0);
				}
			}
			case C_LIST_BASE -> listBase = GL11C.glGetInteger(GL_LIST_BASE);
			case C_PIXEL_STORE -> {
				for (int i = 0; i < PIXEL_STORE_PNAMES.length; i++) {
					PIXEL_STORE[i] = GL11C.glGetInteger(PIXEL_STORE_PNAMES[i]);
				}
			}
			default -> throw new IllegalArgumentException("category " + category);
		}
	}

	/** glIsEnabled for an {@link #ENABLES} key (switching texture unit for per-unit caps). */
	static boolean readEnable(int key) {
		int unit = (key >>> 20) - 1;
		int cap = key & 0xFFFFF;
		if (unit < 0) {
			return GL11C.glIsEnabled(cap);
		}
		GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
		boolean on = GL11C.glIsEnabled(cap);
		GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + activeUnit);
		return on;
	}

	static float[] stackTop(int m) {
		float[] out = new float[16];
		System.arraycopy(STACKS[m], TOP[m] * 16, out, 0, 16);
		return out;
	}

	static int stackDepth(int m) {
		return TOP[m] + 1;
	}

	static int rawMatrixMode() {
		return matrixMode;
	}
}
