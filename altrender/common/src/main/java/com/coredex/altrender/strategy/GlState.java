/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

import java.util.Arrays;

import com.coredex.altrender.gl.GlStateTracker;

/**
 * Cache of the GL state Altrender changes, so redundant calls are skipped. Every strategy changes bindings and
 * fixed-function state only through here; anything else that touches GL state (FML's early screen on NeoForge, other
 * mods) is covered by {@link #invalidate()}, which the device calls once per frame.
 *
 * <p>Deleted objects must be reported ({@code forget*}): GL reverts bindings of deleted objects to 0, and a new object
 * may get the same name, which would otherwise look "already bound". Render thread only.
 */
public final class GlState {
	private static final int UNKNOWN = Integer.MIN_VALUE;
	/** Changed in a way the cache can't name (per-buffer blending): set again on the next request or restore. */
	private static final int DIRTY = Integer.MIN_VALUE + 1;

	/** How framebuffers are bound. */
	public enum FramebufferBinding {
		/** GL 3.0 / {@code ARB_framebuffer_object}: separate draw and read bindings. */
		CORE,
		/** {@code EXT_framebuffer_object} + {@code EXT_framebuffer_blit}: separate draw and read bindings. */
		EXT_SEPARATE,
		/** {@code EXT_framebuffer_object} alone: one binding for both, so binding to read also changes the draw target. */
		EXT_SINGLE
	}

	/** Which vertex array objects exist. */
	public enum VertexArrays {
		/** GL 3.0 / {@code ARB_vertex_array_object}. ({@code APPLE_vertex_array_object} isn't used: LWJGL 3.4 has no bindings.) */
		CORE,
		/** None: attribute arrays and the element array binding are global state (GL 2.1 without extensions). */
		NONE
	}

	/** Texture targets tracked per unit. */
	public static final int TARGET_2D = 0;
	public static final int TARGET_CUBE = 1;
	public static final int TARGET_BUFFER = 2;
	public static final int TARGET_1D = 3;
	public static final int TARGET_3D = 4;
	public static final int TARGET_RECTANGLE = 5;
	private static final int[] TARGETS = {GL11C.GL_TEXTURE_2D, GL13C.GL_TEXTURE_CUBE_MAP, GL31C.GL_TEXTURE_BUFFER,
			GL11C.GL_TEXTURE_1D, org.lwjgl.opengl.GL12C.GL_TEXTURE_3D, GL31C.GL_TEXTURE_RECTANGLE};

	/** The tracked target index of a GL texture target ({@code GL_TEXTURE_2D} → {@link #TARGET_2D}), or -1. */
	public static int targetIndex(int glTarget) {
		for (int i = 0; i < TARGETS.length; i++) {
			if (TARGETS[i] == glTarget) {
				return i;
			}
		}
		return -1;
	}

	private final int units;
	private final boolean samplerObjects;
	private final FramebufferBinding framebufferBinding;
	private final VertexArrays vertexArrays;
	private int arrayBuffer;
	private int elementBuffer;
	private int program;
	private int vertexArray;
	private int activeUnit;
	private final int[][] textures;
	private final int[] samplers;
	private final int[] uboBuffer;
	private final long[] uboOffset;
	private final long[] uboSize;
	private int drawFramebuffer;
	private int readFramebuffer;
	private int vx;
	private int vy;
	private int vw;
	private int vh;
	private int scissorTest;
	private int sx;
	private int sy;
	private int sw;
	private int sh;
	private int colorMask;
	private int depthMask;
	private int stencilMask;
	private int blend;
	private int blendSrcColor;
	private int blendDstColor;
	private int blendSrcAlpha;
	private int blendDstAlpha;
	private int blendEquation;
	private int depthTest;
	private int depthFunc;
	private int cull;
	private int polygonMode;
	private int offsetFill;
	private float offsetFactor;
	private float offsetUnits;
	private int stencilTest;
	private StencilState stencil;
	private RasterState lastRaster;
	/** What the game had before this pass first changed it (read once, on demand): restored by {@link #restoreGameState}. */
	private int gameDrawFramebuffer;
	private int gameReadFramebuffer;
	private final int[] gameViewport = new int[4];
	private int gameStencilMask;
	/** Object bindings this pass changed: restored by these flags, not by the cached names (a deleted program stays current). */
	private boolean programTouched;
	private boolean vertexArrayTouched;
	private boolean arrayBufferTouched;
	private boolean elementBufferTouched;

	public GlState(int textureUnits, int uniformBindings, boolean samplerObjects) {
		this(textureUnits, uniformBindings, samplerObjects, FramebufferBinding.CORE, VertexArrays.CORE);
	}

	public GlState(int textureUnits, int uniformBindings, boolean samplerObjects, FramebufferBinding framebufferBinding,
				   VertexArrays vertexArrays) {
		this.units = textureUnits;
		this.samplerObjects = samplerObjects;
		this.framebufferBinding = framebufferBinding;
		this.vertexArrays = vertexArrays;
		this.textures = new int[textureUnits][TARGETS.length];
		this.samplers = new int[textureUnits];
		this.uboBuffer = new int[uniformBindings];
		this.uboOffset = new long[uniformBindings];
		this.uboSize = new long[uniformBindings];
		invalidate();
	}

	/** Forget everything; the next request for each piece of state issues the GL call. */
	public void invalidate() {
		program = UNKNOWN;
		vertexArray = UNKNOWN;
		arrayBuffer = UNKNOWN;
		elementBuffer = UNKNOWN;
		activeUnit = UNKNOWN;
		for (int[] unit : textures) {
			Arrays.fill(unit, UNKNOWN);
		}
		Arrays.fill(samplers, UNKNOWN);
		Arrays.fill(uboBuffer, UNKNOWN);
		drawFramebuffer = UNKNOWN;
		readFramebuffer = UNKNOWN;
		vx = UNKNOWN;
		scissorTest = UNKNOWN;
		sx = UNKNOWN;
		colorMask = UNKNOWN;
		depthMask = UNKNOWN;
		stencilMask = UNKNOWN;
		blend = UNKNOWN;
		blendSrcColor = UNKNOWN;
		blendEquation = UNKNOWN;
		depthTest = UNKNOWN;
		depthFunc = UNKNOWN;
		cull = UNKNOWN;
		polygonMode = UNKNOWN;
		offsetFill = UNKNOWN;
		offsetFactor = Float.NaN;
		stencilTest = UNKNOWN;
		stencil = null;
		lastRaster = null;
		gameDrawFramebuffer = UNKNOWN;
		gameReadFramebuffer = UNKNOWN;
		gameViewport[0] = UNKNOWN;
		gameStencilMask = UNKNOWN;
		programTouched = false;
		vertexArrayTouched = false;
		arrayBufferTouched = false;
		elementBufferTouched = false;
	}

	/**
	 * Puts back the game's state for everything this cache changed since {@link #invalidate()} (development.md 1.5).
	 * Altrender's GL calls bypass {@link GlStateTracker}, so without this the game would draw with Altrender's
	 * program, vertex array, buffers, textures and raster state. Values come from the tracker; framebuffers, viewport
	 * and stencil mask from a read just before the pass first changed them. Ends with {@link #invalidate()}.
	 */
	public void restoreGameState() {
		if (programTouched) {
			GL20C.glUseProgram(GameDraws.program());
		}
		if (vertexArrayTouched && vertexArrays == VertexArrays.CORE) {
			GL30C.glBindVertexArray(0);
		}
		if (arrayBufferTouched) {
			// The game's client-side arrays need no array buffer bound.
			GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
		}
		if (elementBufferTouched) {
			GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);
		}
		boolean unitsChanged = activeUnit != UNKNOWN;
		for (int u = 0; u < units; u++) {
			int[] override = GameDraws.texture(u);
			for (int t = 0; t < TARGETS.length; t++) {
				if (textures[u][t] != UNKNOWN) {
					unitsChanged = true;
					GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + u);
					int name = override != null && override[0] == t ? override[1]
							: t == TARGET_2D ? GlStateTracker.boundTexture2D(u) : 0;
					GL11C.glBindTexture(TARGETS[t], name);
				}
			}
			if (samplers[u] != UNKNOWN && samplers[u] != 0) {
				GL33C.glBindSampler(u, 0);
			}
		}
		if (unitsChanged) {
			GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + GlStateTracker.activeUnit());
		}
		if (gameDrawFramebuffer != UNKNOWN) {
			GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, gameDrawFramebuffer);
		}
		if (gameReadFramebuffer != UNKNOWN) {
			GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, gameReadFramebuffer);
		}
		if (gameViewport[0] != UNKNOWN) {
			GL11C.glViewport(gameViewport[0], gameViewport[1], gameViewport[2], gameViewport[3]);
		}
		if (scissorTest != UNKNOWN) {
			set(GL11C.GL_SCISSOR_TEST, GlStateTracker.isEnabled(GL11C.GL_SCISSOR_TEST));
		}
		if (colorMask != UNKNOWN) {
			GL11C.glColorMask(GlStateTracker.colorMask(0), GlStateTracker.colorMask(1), GlStateTracker.colorMask(2),
					GlStateTracker.colorMask(3));
		}
		if (depthMask != UNKNOWN) {
			GL11C.glDepthMask(GlStateTracker.depthMask());
		}
		if (gameStencilMask != UNKNOWN) {
			GL11C.glStencilMask(gameStencilMask);
		}
		if (GlStateTracker.blendOverridden() && (blend != UNKNOWN || blendSrcColor != UNKNOWN)) {
			GlStateTracker.applyBlend(); // the pipeline's blend for its program, not the game's
		} else {
			if (blend != UNKNOWN) {
				set(GL11C.GL_BLEND, GlStateTracker.isEnabled(GL11C.GL_BLEND));
			}
			if (blendSrcColor != UNKNOWN) {
				GL11C.glBlendFunc(GlStateTracker.blendSrc(), GlStateTracker.blendDst());
			}
			if (blend != UNKNOWN || blendSrcColor != UNKNOWN) {
				GlStateTracker.applyBufferBlends(); // a pipeline's per-buffer blends over the game's
			}
		}
		if (blendEquation != UNKNOWN) {
			GL14C.glBlendEquation(GL14C.GL_FUNC_ADD); // the game never changes it
		}
		if (depthTest != UNKNOWN) {
			set(GL11C.GL_DEPTH_TEST, GlStateTracker.isEnabled(GL11C.GL_DEPTH_TEST));
		}
		if (depthFunc != UNKNOWN) {
			GL11C.glDepthFunc(GlStateTracker.depthFunc());
		}
		if (offsetFill != UNKNOWN) {
			set(GL11C.GL_POLYGON_OFFSET_FILL, GlStateTracker.isEnabled(GL11C.GL_POLYGON_OFFSET_FILL));
		}
		if (!Float.isNaN(offsetFactor)) {
			GL11C.glPolygonOffset(GlStateTracker.polygonOffsetFactor(), GlStateTracker.polygonOffsetUnits());
		}
		if (cull != UNKNOWN) {
			set(GL11C.GL_CULL_FACE, GlStateTracker.isEnabled(GL11C.GL_CULL_FACE));
		}
		if (polygonMode != UNKNOWN) {
			GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
		}
		if (stencilTest != UNKNOWN) {
			set(GL11C.GL_STENCIL_TEST, GlStateTracker.isEnabled(GL11C.GL_STENCIL_TEST));
		}
		if (stencil != null) {
			GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 0xFFFFFFFF);
			GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
		}
		invalidate();
	}

	private static void set(int cap, boolean on) {
		if (on) {
			GL11C.glEnable(cap);
		} else {
			GL11C.glDisable(cap);
		}
	}

	public int textureUnits() {
		return units;
	}

	public FramebufferBinding framebufferBinding() {
		return framebufferBinding;
	}

	public VertexArrays vertexArrays() {
		return vertexArrays;
	}

	// ---- objects ---------------------------------------------------------------------------------------------

	/** The program last bound through the cache. */
	public int program() {
		return program;
	}

	public void useProgram(int name) {
		programTouched = true;
		if (program != name) {
			GL20C.glUseProgram(name);
			program = name;
		}
	}

	public void bindVertexArray(int name) {
		vertexArrayTouched = true;
		if (vertexArray != name) {
			switch (vertexArrays) {
				case CORE -> GL30C.glBindVertexArray(name);
				case NONE -> {
					if (name != 0) {
						throw new IllegalStateException("vertex array objects aren't used on this context");
					}
					// A 3.x compatibility context (testing the 2.1 paths) still has VAOs; make sure none is bound,
					// or the "global" attribute state would land in someone else's VAO.
					if (GL.getCapabilities().glBindVertexArray != 0L) {
						GL30C.glBindVertexArray(0);
					}
				}
			}
			vertexArray = name;
			elementBuffer = UNKNOWN; // the element array binding belongs to the vertex array
		}
	}

	/** {@code GL_ARRAY_BUFFER}: global state on every version (attribute pointers capture it when they're set). */
	public void bindArrayBuffer(int name) {
		arrayBufferTouched = true;
		if (arrayBuffer != name) {
			GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, name);
			arrayBuffer = name;
		}
	}

	/** {@code GL_ELEMENT_ARRAY_BUFFER} of the currently bound vertex array (or the global one without VAOs). */
	public void bindElementBuffer(int name) {
		elementBufferTouched = true;
		if (elementBuffer != name) {
			GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, name);
			elementBuffer = name;
		}
	}

	public void activeTexture(int unit) {
		if (activeUnit != unit) {
			GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
			activeUnit = unit;
		}
	}

	/** Binds {@code texture} to {@code target} ({@link #TARGET_2D} etc.) of {@code unit} and leaves that unit active. */
	public void bindTexture(int unit, int target, int texture) {
		activeTexture(unit);
		if (textures[unit][target] != texture) {
			GL11C.glBindTexture(TARGETS[target], texture);
			textures[unit][target] = texture;
		}
	}

	/**
	 * {@link #bindTexture} by DSA ({@code glBindTextureUnit}, gl45): the active unit stays as it is, so a draw whose
	 * textures are already bound makes no GL call at all. {@code texture} must not be 0 (that unbinds every target).
	 */
	public void bindTextureUnit(int unit, int target, int texture) {
		if (textures[unit][target] != texture) {
			org.lwjgl.opengl.GL45C.glBindTextureUnit(unit, texture);
			textures[unit][target] = texture;
		}
	}

	public void bindSampler(int unit, int sampler) {
		if (samplerObjects && samplers[unit] != sampler) {
			GL33C.glBindSampler(unit, sampler);
			samplers[unit] = sampler;
		}
	}

	public void bindUniformRange(int binding, int buffer, long offset, long size) {
		if (uboBuffer[binding] != buffer || uboOffset[binding] != offset || uboSize[binding] != size) {
			GL30C.glBindBufferRange(GL31C.GL_UNIFORM_BUFFER, binding, buffer, offset, size);
			uboBuffer[binding] = buffer;
			uboOffset[binding] = offset;
			uboSize[binding] = size;
		}
	}

	public void bindDrawFramebuffer(int name) {
		if (drawFramebuffer != name) {
			if (gameDrawFramebuffer == UNKNOWN) {
				int game = GameDraws.framebuffer();
				gameDrawFramebuffer = game >= 0 ? game : GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
			}
			switch (framebufferBinding) {
				case CORE -> GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, name);
				case EXT_SEPARATE -> EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, name);
				case EXT_SINGLE -> {
					EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, name);
					readFramebuffer = name;
				}
			}
			drawFramebuffer = name;
		}
	}

	/** With {@link FramebufferBinding#EXT_SINGLE} this also changes the draw target. */
	public void bindReadFramebuffer(int name) {
		if (readFramebuffer != name) {
			if (gameReadFramebuffer == UNKNOWN) {
				int game = GameDraws.framebuffer();
				gameReadFramebuffer = game >= 0 ? game : GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
			}
			switch (framebufferBinding) {
				case CORE -> GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, name);
				case EXT_SEPARATE -> EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, name);
				case EXT_SINGLE -> {
					EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, name);
					drawFramebuffer = name;
				}
			}
			readFramebuffer = name;
		}
	}

	// ---- simple state ----------------------------------------------------------------------------------------

	public void viewport(int x, int y, int w, int h) {
		if (vx != x || vy != y || vw != w || vh != h) {
			if (gameViewport[0] == UNKNOWN) {
				GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, gameViewport);
			}
			GL11C.glViewport(x, y, w, h);
			vx = x;
			vy = y;
			vw = w;
			vh = h;
		}
	}

	public void scissorTest(boolean enabled) {
		scissorTest = toggle(GL11C.GL_SCISSOR_TEST, scissorTest, enabled);
	}

	public void scissor(int x, int y, int w, int h) {
		scissorTest(true);
		if (sx != x || sy != y || sw != w || sh != h) {
			GL11C.glScissor(x, y, w, h);
			sx = x;
			sy = y;
			sw = w;
			sh = h;
		}
	}

	/** R=1, G=2, B=4, A=8. */
	public void colorMask(int mask) {
		if (colorMask != mask) {
			GL11C.glColorMask((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
			colorMask = mask;
			lastRaster = null;
		}
	}

	public void depthMask(boolean write) {
		int v = write ? 1 : 0;
		if (depthMask != v) {
			GL11C.glDepthMask(write);
			depthMask = v;
			lastRaster = null;
		}
	}

	public void stencilMask(int mask) {
		if (stencilMask != mask) {
			if (gameStencilMask == UNKNOWN) {
				gameStencilMask = GL11C.glGetInteger(GL11C.GL_STENCIL_WRITEMASK);
			}
			GL11C.glStencilMask(mask);
			stencilMask = mask;
			lastRaster = null;
		}
	}

	// ---- raw GL enums (the API's Gpu) -------------------------------------------------------------------------

	/** Blending for every draw buffer by GL factors; {@code on false} switches it off. */
	public void blendGl(boolean on, int srcColor, int dstColor, int srcAlpha, int dstAlpha) {
		blend = toggle(GL11C.GL_BLEND, blend, on);
		if (on && (srcColor != blendSrcColor || dstColor != blendDstColor || srcAlpha != blendSrcAlpha
				|| dstAlpha != blendDstAlpha)) {
			GL14C.glBlendFuncSeparate(srcColor, dstColor, srcAlpha, dstAlpha);
			blendSrcColor = srcColor;
			blendDstColor = dstColor;
			blendSrcAlpha = srcAlpha;
			blendDstAlpha = dstAlpha;
		}
		if (on && blendEquation != (GL14C.GL_FUNC_ADD << 16 | GL14C.GL_FUNC_ADD)) {
			GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
			blendEquation = GL14C.GL_FUNC_ADD << 16 | GL14C.GL_FUNC_ADD;
		}
		lastRaster = null;
	}

	/**
	 * Blending for one draw buffer ({@code glEnablei}/{@code glBlendFuncSeparatei}, GL 4.0 or
	 * {@code ARB_draw_buffers_blend}). The cache then treats the global blend state as unknown, so the next global
	 * change and the game's restore set it again for every buffer.
	 */
	public void blendGl(int drawBuffer, boolean on, int srcColor, int dstColor, int srcAlpha, int dstAlpha) {
		boolean core = org.lwjgl.opengl.GL.getCapabilities().OpenGL40;
		if (on) {
			if (core) {
				org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(drawBuffer, srcColor, dstColor, srcAlpha, dstAlpha);
			} else {
				org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(drawBuffer, srcColor, dstColor, srcAlpha,
						dstAlpha);
			}
			GL30C.glEnablei(GL11C.GL_BLEND, drawBuffer);
		} else {
			GL30C.glDisablei(GL11C.GL_BLEND, drawBuffer);
		}
		blend = DIRTY;
		blendSrcColor = DIRTY;
		blendEquation = DIRTY;
		lastRaster = null;
	}

	/** Face culling on or off (back faces). */
	public void cullGl(boolean on) {
		cull = toggle(GL11C.GL_CULL_FACE, cull, on);
		lastRaster = null;
	}

	/** Depth test with a GL compare function (0: no test) and depth writes. */
	public void depthGl(int func, boolean write) {
		depthTest = toggle(GL11C.GL_DEPTH_TEST, depthTest, func != 0);
		if (func != 0 && depthFunc != func) {
			GL11C.glDepthFunc(func);
			depthFunc = func;
		}
		depthMask(write);
		lastRaster = null;
	}

	// ---- pipeline state --------------------------------------------------------------------------------------

	/** Applies one pipeline's fixed-function state. Cheap when the same state object was applied last. */
	public void apply(RasterState r) {
		if (r == lastRaster) {
			return;
		}
		blend = toggle(GL11C.GL_BLEND, blend, r.blend());
		if (r.blend()) {
			int sc = blendFactor(r.srcColor());
			int dc = blendFactor(r.dstColor());
			int sa = blendFactor(r.srcAlpha());
			int da = blendFactor(r.dstAlpha());
			if (sc != blendSrcColor || dc != blendDstColor || sa != blendSrcAlpha || da != blendDstAlpha) {
				GL14C.glBlendFuncSeparate(sc, dc, sa, da);
				blendSrcColor = sc;
				blendDstColor = dc;
				blendSrcAlpha = sa;
				blendDstAlpha = da;
			}
			int ec = blendEquation(r.colorEquation());
			int ea = blendEquation(r.alphaEquation());
			int packed = ec << 16 | ea;
			if (packed != blendEquation) {
				org.lwjgl.opengl.GL20C.glBlendEquationSeparate(ec, ea);
				blendEquation = packed;
			}
		}
		colorMask(r.colorMask());
		depthTest = toggle(GL11C.GL_DEPTH_TEST, depthTest, r.depthTest());
		if (r.depthTest()) {
			int func = compare(r.depthFunc());
			if (depthFunc != func) {
				GL11C.glDepthFunc(func);
				depthFunc = func;
			}
		}
		depthMask(r.depthTest() && r.depthWrite());
		boolean offset = r.depthTest() && (r.offsetFactor() != 0f || r.offsetUnits() != 0f);
		offsetFill = toggle(GL11C.GL_POLYGON_OFFSET_FILL, offsetFill, offset);
		if (offset && (offsetFactor != r.offsetFactor() || offsetUnits != r.offsetUnits())) {
			GL11C.glPolygonOffset(r.offsetFactor(), r.offsetUnits());
			offsetFactor = r.offsetFactor();
			offsetUnits = r.offsetUnits();
		}
		cull = toggle(GL11C.GL_CULL_FACE, cull, r.cull());
		int mode = r.wireframe() ? GL11C.GL_LINE : GL11C.GL_FILL;
		if (polygonMode != mode) {
			GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, mode);
			polygonMode = mode;
		}
		applyStencil(r.stencil());
		lastRaster = r;
	}

	private void applyStencil(StencilState s) {
		stencilTest = toggle(GL11C.GL_STENCIL_TEST, stencilTest, s != null);
		if (s == null) {
			return;
		}
		stencilMask(s.writeMask()); // a clear may have forced it to 0xFF in between
		if (s.equals(stencil)) {
			return;
		}
		if (s.front().equals(s.back())) {
			face(GL11C.GL_FRONT_AND_BACK, s.front(), s);
		} else {
			face(GL11C.GL_FRONT, s.front(), s);
			face(GL11C.GL_BACK, s.back(), s);
		}
		stencil = s;
	}

	private static void face(int face, StencilState.Face f, StencilState s) {
		GL20C.glStencilFuncSeparate(face, compare(f.compare()), s.reference(), s.readMask());
		GL20C.glStencilOpSeparate(face, stencilOp(f.fail()), stencilOp(f.depthFail()), stencilOp(f.pass()));
	}

	/** Global switches Altrender's passes expect (seamless cube maps, shader-written point size). Once per context. */
	public void initGlobals(boolean seamlessCubeMap) {
		if (seamlessCubeMap) {
			GL11C.glEnable(GL32C.GL_TEXTURE_CUBE_MAP_SEAMLESS);
		}
		GL11C.glEnable(GL32C.GL_PROGRAM_POINT_SIZE);
	}

	// ---- deletion bookkeeping --------------------------------------------------------------------------------

	public void forgetTexture(int name) {
		for (int[] unit : textures) {
			for (int t = 0; t < unit.length; t++) {
				if (unit[t] == name) {
					unit[t] = 0;
				}
			}
		}
	}

	public void forgetSampler(int name) {
		for (int i = 0; i < samplers.length; i++) {
			if (samplers[i] == name) {
				samplers[i] = 0;
			}
		}
	}

	public void forgetBuffer(int name) {
		if (arrayBuffer == name) {
			arrayBuffer = 0;
		}
		if (elementBuffer == name) {
			elementBuffer = 0;
		}
		for (int i = 0; i < uboBuffer.length; i++) {
			if (uboBuffer[i] == name) {
				uboBuffer[i] = UNKNOWN;
			}
		}
	}

	public void forgetFramebuffer(int name) {
		if (drawFramebuffer == name) {
			drawFramebuffer = 0;
		}
		if (readFramebuffer == name) {
			readFramebuffer = 0;
		}
	}

	public void forgetProgram(int name) {
		if (program == name) {
			program = UNKNOWN;
		}
	}

	public void forgetVertexArray(int name) {
		if (vertexArray == name) {
			vertexArray = 0;
		}
	}

	// ---- enum mapping (identical on every GL version we support) ---------------------------------------------

	private static int toggle(int cap, int current, boolean enabled) {
		int v = enabled ? 1 : 0;
		if (current != v) {
			if (enabled) {
				GL11C.glEnable(cap);
			} else {
				GL11C.glDisable(cap);
			}
		}
		return v;
	}

	public static int compare(Compare c) {
		return switch (c) {
			case NEVER -> GL11C.GL_NEVER;
			case LESS -> GL11C.GL_LESS;
			case EQUAL -> GL11C.GL_EQUAL;
			case LEQUAL -> GL11C.GL_LEQUAL;
			case GREATER -> GL11C.GL_GREATER;
			case NOTEQUAL -> GL11C.GL_NOTEQUAL;
			case GEQUAL -> GL11C.GL_GEQUAL;
			case ALWAYS -> GL11C.GL_ALWAYS;
		};
	}

	private static int blendEquation(BlendEquation e) {
		return switch (e) {
			case ADD -> GL14C.GL_FUNC_ADD;
			case SUBTRACT -> GL14C.GL_FUNC_SUBTRACT;
			case REVERSE_SUBTRACT -> GL14C.GL_FUNC_REVERSE_SUBTRACT;
			case MIN -> GL14C.GL_MIN;
			case MAX -> GL14C.GL_MAX;
		};
	}

	private static int blendFactor(BlendFactor f) {
		return switch (f) {
			case ZERO -> GL11C.GL_ZERO;
			case ONE -> GL11C.GL_ONE;
			case SRC_COLOR -> GL11C.GL_SRC_COLOR;
			case ONE_MINUS_SRC_COLOR -> GL11C.GL_ONE_MINUS_SRC_COLOR;
			case DST_COLOR -> GL11C.GL_DST_COLOR;
			case ONE_MINUS_DST_COLOR -> GL11C.GL_ONE_MINUS_DST_COLOR;
			case SRC_ALPHA -> GL11C.GL_SRC_ALPHA;
			case ONE_MINUS_SRC_ALPHA -> GL11C.GL_ONE_MINUS_SRC_ALPHA;
			case DST_ALPHA -> GL11C.GL_DST_ALPHA;
			case ONE_MINUS_DST_ALPHA -> GL11C.GL_ONE_MINUS_DST_ALPHA;
			case CONSTANT_COLOR -> GL14C.GL_CONSTANT_COLOR;
			case ONE_MINUS_CONSTANT_COLOR -> GL14C.GL_ONE_MINUS_CONSTANT_COLOR;
			case CONSTANT_ALPHA -> GL14C.GL_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_ALPHA -> GL14C.GL_ONE_MINUS_CONSTANT_ALPHA;
			case SRC_ALPHA_SATURATE -> GL11C.GL_SRC_ALPHA_SATURATE;
		};
	}

	private static int stencilOp(StencilOp op) {
		return switch (op) {
			case KEEP -> GL11C.GL_KEEP;
			case ZERO -> GL11C.GL_ZERO;
			case REPLACE -> GL11C.GL_REPLACE;
			case INCR -> GL11C.GL_INCR;
			case DECR -> GL11C.GL_DECR;
			case INVERT -> GL11C.GL_INVERT;
			case INCR_WRAP -> GL14C.GL_INCR_WRAP;
			case DECR_WRAP -> GL14C.GL_DECR_WRAP;
		};
	}
}
