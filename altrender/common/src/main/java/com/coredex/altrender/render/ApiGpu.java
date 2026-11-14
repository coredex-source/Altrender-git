/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import com.coredex.altrender.api.gpu.Blend;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuBuffer;
import com.coredex.altrender.api.gpu.GpuFramebuffer;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.strategy.GameDraws;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.Strategies;

/**
 * The API's {@link Gpu} on the tier's strategies (development.md 6.1): every binding goes through Altrender's
 * {@link GlState}, so the game's state comes back after each pass; fixed-function matrix and enable changes go
 * through the game's {@code GL11} (and so the tracker). Objects made here are freed by {@link #delete} (a tier
 * switch or shutdown) if the pipeline didn't. Render thread only.
 */
final class ApiGpu implements Gpu {

	private static final int GL_TEXTURE = 0x1702;
	private static final int GL_BUFFER = 0x82E0;
	private static final int GL_FRAMEBUFFER = 0x8D40;
	private static final int GL_ALPHA_TEST = 0x0BC0;
	private static final int GL_PROJECTION = 0x1701;
	private static final int GL_MODELVIEW = 0x1700;
	private static final int GL_TEXTURE_MATRIX_MODE = 0x1702;

	private final Strategies s;
	private final GlState state;
	private final GlObjectCounts counts;
	/** Unit used to create and upload textures (the last one: the game uses the first two). */
	private final int scratchUnit;
	private final List<Object> live = new ArrayList<>();
	private int passDepth;
	/** The game's own framebuffer ("the screen": 0, or the scene harness's capture target), read when a pipeline takes the game's draws away from it. */
	private int screen;
	private int quadVao;
	private int quadVbo;

	ApiGpu(Strategies s) {
		this.s = s;
		this.state = s.state();
		this.counts = s.counts();
		this.scratchUnit = state.textureUnits() - 1;
	}

	/** Frees what's left (the pipeline should have freed its own) and the game's draw override. */
	void delete() {
		for (Object o : new ArrayList<>(live)) {
			if (o instanceof Tex t) {
				t.delete();
			} else if (o instanceof Fb f) {
				f.delete();
			} else if (o instanceof Buf b) {
				b.delete();
			}
		}
		if (quadVao != 0) {
			GL30C.glDeleteVertexArrays(quadVao);
			GL15C.glDeleteBuffers(quadVbo);
			state.forgetVertexArray(quadVao);
			state.forgetBuffer(quadVbo);
			counts.deleted(GlObjectCounts.Kind.VERTEX_ARRAY);
			counts.deleted(GlObjectCounts.Kind.BUFFER);
			quadVao = 0;
		}
		if (GameDraws.framebuffer() >= 0 || GameDraws.program() != 0) {
			GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, screen);
			GL20C.glUseProgram(0);
		}
		GameDraws.clear();
		GlStateTracker.suppressAlphaTest(false);
		GlStateTracker.overrideBufferBlends(null);
		if (GlStateTracker.blendOverridden()) {
			GlStateTracker.overrideBlend(null);
		}
	}

	/** Objects made through the API and not freed yet (for leak checks across pack reloads). */
	int liveObjects() {
		return live.size();
	}

	private void own(Runnable work) {
		boolean outer = passDepth == 0;
		if (outer) {
			s.beginPass("ar:gpu");
		}
		passDepth++;
		try {
			work.run();
		} finally {
			passDepth--;
			if (outer) {
				s.endPass();
			}
		}
	}

	// ---- objects ----------------------------------------------------------------------------------------------

	@Override
	public GpuTexture createTexture(TextureSpec spec, String label) {
		int target = GlState.targetIndex(spec.target());
		if (target < 0 || target == GlState.TARGET_BUFFER || target == GlState.TARGET_CUBE) {
			throw new IllegalArgumentException("texture target 0x" + Integer.toHexString(spec.target()));
		}
		Tex t = new Tex(spec, GL11C.glGenTextures(), target);
		counts.created(GlObjectCounts.Kind.TEXTURE);
		live.add(t);
		own(() -> {
			t.bind();
			GL11C.glTexParameteri(spec.target(), GL12C.GL_TEXTURE_MAX_LEVEL, Math.max(0, spec.levels() - 1));
			t.sampling(GL11C.GL_NEAREST, GL11C.GL_NEAREST, GL12C.GL_CLAMP_TO_EDGE);
			t.allocate();
		});
		GlDebug.label(GL_TEXTURE, t.name, label);
		return t;
	}

	@Override
	public GpuFramebuffer createFramebuffer(String label) {
		Fb f = new Fb(GL30C.glGenFramebuffers());
		counts.created(GlObjectCounts.Kind.FRAMEBUFFER);
		live.add(f);
		own(() -> state.bindDrawFramebuffer(f.name)); // a framebuffer exists once first bound
		GlDebug.label(GL_FRAMEBUFFER, f.name, label);
		return f;
	}

	@Override
	public GpuBuffer createBuffer(long size, String label) {
		Buf b = new Buf(GL15C.glGenBuffers(), size);
		counts.created(GlObjectCounts.Kind.BUFFER);
		live.add(b);
		own(() -> {
			state.bindArrayBuffer(b.name);
			GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, size, GL15C.GL_DYNAMIC_DRAW);
			b.zero();
		});
		GlDebug.label(GL_BUFFER, b.name, label);
		return b;
	}

	// ---- the game's draws -------------------------------------------------------------------------------------

	@Override
	public void gameDraws(GpuFramebuffer target, int program) {
		int fb = target == null ? -1 : ((Fb) target).name;
		if (GameDraws.framebuffer() < 0 && fb >= 0) {
			screen = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
		}
		GameDraws.set(fb, program);
		GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb >= 0 ? fb : screen);
		GL20C.glUseProgram(program);
		state.invalidate();
	}

	@Override
	public void gameTexture(int unit, GpuTexture texture) {
		if (unit <= 0 || unit >= state.textureUnits()) {
			throw new IllegalArgumentException("game texture unit " + unit);
		}
		Tex t = (Tex) texture;
		GameDraws.texture(unit, t == null ? 0 : t.target, t == null ? 0 : t.name);
		GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
		if (t == null) {
			GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, GlStateTracker.boundTexture2D(unit));
		} else {
			GL11C.glBindTexture(t.spec.target(), t.name);
		}
		GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + GlStateTracker.activeUnit());
		state.invalidate();
	}

	@Override
	public void gameLightCoords(float block, float sky) {
		boolean off = Float.isNaN(block) || Float.isNaN(sky);
		org.lwjgl.opengl.GL13.glMultiTexCoord4f(GL13C.GL_TEXTURE1, off ? 0 : block, off ? 0 : sky, 0, 1);
		if (lightMatrixOn != null && lightMatrixOn == !off) {
			return; // unit 1's matrix is already right: per entity only the coordinates change (no state to resync)
		}
		lightMatrixOn = !off;
		int unit = GlStateTracker.activeUnit();
		int mode = GlStateTracker.matrixMode();
		org.lwjgl.opengl.GL13.glActiveTexture(GL13C.GL_TEXTURE1);
		GL11.glMatrixMode(GL_TEXTURE_MATRIX_MODE);
		if (off) {
			GL11.glLoadIdentity();
		} else {
			float s = 1f / 256f;
			float t = 8f / 256f;
			GL11.glLoadMatrixf(new float[] {s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, t, t, t, 1});
		}
		GL11.glMatrixMode(mode);
		org.lwjgl.opengl.GL13.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
		state.invalidate();
	}

	/** Whether unit 1's texture matrix is the modern lightmap one (null: not known, set it on the next call). */
	private Boolean lightMatrixOn;

	@Override
	public void gameProgram(int program) {
		GameDraws.program(program);
		GL20C.glUseProgram(program);
		state.invalidate();
	}

	// ---- the pipeline's own passes ----------------------------------------------------------------------------

	@Override
	public void begin(String label) {
		if (passDepth++ == 0) {
			s.beginPass(label);
		} else {
			GlDebug.push(label);
		}
	}

	@Override
	public void end() {
		if (passDepth <= 0) {
			throw new IllegalStateException("Gpu.end() without begin()");
		}
		if (--passDepth == 0) {
			s.endPass();
		} else {
			GlDebug.pop();
		}
	}

	@Override
	public void gameBlend(com.coredex.altrender.api.gpu.Blend blend) {
		GlStateTracker.overrideBlend(blend == null ? null
				: blend.equals(com.coredex.altrender.api.gpu.Blend.NONE) ? new int[] {0}
						: new int[] {1, blend.srcRgb(), blend.dstRgb(), blend.srcAlpha(), blend.dstAlpha()});
	}

	@Override
	public void gameBufferBlends(com.coredex.altrender.api.gpu.Blend[] blends) {
		int[][] b = null;
		if (blends != null && blends.length > 0) {
			b = new int[blends.length][];
			for (int i = 0; i < blends.length; i++) {
				com.coredex.altrender.api.gpu.Blend x = blends[i];
				b[i] = x == null ? null : x.equals(com.coredex.altrender.api.gpu.Blend.NONE) ? new int[] {0}
						: new int[] {1, x.srcRgb(), x.dstRgb(), x.srcAlpha(), x.dstAlpha()};
			}
		}
		GlStateTracker.overrideBufferBlends(b);
	}

	@Override
	public void gameAlphaTest(boolean gameOwn) {
		GlStateTracker.suppressAlphaTest(!gameOwn);
	}

	@Override
	public void beginTimer(com.coredex.altrender.api.gpu.TimedPass pass) {
		Renderer r = Renderer.instance();
		if (r != null) {
			r.pipelineTimer(pass.ordinal(), true);
		}
	}

	@Override
	public void endTimer(com.coredex.altrender.api.gpu.TimedPass pass) {
		Renderer r = Renderer.instance();
		if (r != null) {
			r.pipelineTimer(pass.ordinal(), false);
		}
	}

	private void inPass() {
		if (passDepth == 0) {
			throw new IllegalStateException("draw state outside Gpu.begin()/end()");
		}
	}

	@Override
	public void bindFramebuffer(GpuFramebuffer target) {
		inPass();
		int name = target == null ? screen : ((Fb) target).name;
		state.bindDrawFramebuffer(name);
		state.bindReadFramebuffer(name);
	}

	@Override
	public void viewport(int x, int y, int width, int height) {
		inPass();
		state.viewport(x, y, width, height);
	}

	@Override
	public void useProgram(int program) {
		inPass();
		state.useProgram(program);
	}

	@Override
	public int uniformLocation(int program, String name) {
		return GL20C.glGetUniformLocation(program, name);
	}

	@Override
	public java.util.Map<String, Integer> activeUniforms(int program) {
		java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
		int n = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORMS);
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			java.nio.IntBuffer size = stack.mallocInt(1);
			java.nio.IntBuffer type = stack.mallocInt(1);
			for (int i = 0; i < n; i++) {
				String name = GL20C.glGetActiveUniform(program, i, size, type);
				if (name.endsWith("[0]")) {
					name = name.substring(0, name.length() - 3);
				}
				m.put(name, type.get(0));
			}
		}
		return m;
	}

	@Override
	public void uniform1i(int location, int x) {
		GL20C.glUniform1i(location, x);
	}

	@Override
	public void uniform2i(int location, int x, int y) {
		GL20C.glUniform2i(location, x, y);
	}

	@Override
	public void uniform3i(int location, int x, int y, int z) {
		GL20C.glUniform3i(location, x, y, z);
	}

	@Override
	public void uniform4i(int location, int x, int y, int z, int w) {
		GL20C.glUniform4i(location, x, y, z, w);
	}

	@Override
	public void uniformMatrix3(int location, float[] m) {
		GL20C.glUniformMatrix3fv(location, false, m);
	}

	@Override
	public void uniform4fv(int location, float[] v) {
		GL20C.glUniform4fv(location, v);
	}

	@Override
	public void uniform1f(int location, float x) {
		GL20C.glUniform1f(location, x);
	}

	@Override
	public void uniform2f(int location, float x, float y) {
		GL20C.glUniform2f(location, x, y);
	}

	@Override
	public void uniform3f(int location, float x, float y, float z) {
		GL20C.glUniform3f(location, x, y, z);
	}

	@Override
	public void uniform4f(int location, float x, float y, float z, float w) {
		GL20C.glUniform4f(location, x, y, z, w);
	}

	@Override
	public void uniformMatrix4(int location, float[] m) {
		GL20C.glUniformMatrix4fv(location, false, m);
	}

	@Override
	public void bindTexture(int unit, GpuTexture texture) {
		inPass();
		Tex t = (Tex) texture;
		state.bindTexture(unit, t.target, t.name);
	}

	@Override
	public void bindGameTexture(int unit, int name) {
		inPass();
		state.bindTexture(unit, GlState.TARGET_2D, name);
	}

	@Override
	public void bindImage(int unit, GpuTexture texture, int level, int access, int format) {
		inPass();
		Tex t = (Tex) texture;
		if (unit >= 0 && unit < imageUnits.length) {
			long key = ((long) t.name << 32) | ((long) (level & 0xFF) << 24) | ((long) (access & 0xF) << 20)
					| (format & 0xFFFFF);
			if (imageUnits[unit] == key) {
				return; // already bound so (only this class binds images)
			}
			imageUnits[unit] = key;
		}
		GL42C.glBindImageTexture(unit, t.name, level, t.spec.target() == GL12C.GL_TEXTURE_3D, 0, access, format);
	}

	/** What each image unit has bound ({@link #bindImage}'s key; 0: nothing known). */
	private final long[] imageUnits = new long[32];

	@Override
	public void bindStorageBuffer(int index, GpuBuffer buffer) {
		inPass();
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, index, ((Buf) buffer).name);
	}

	@Override
	public void bindUniformBuffer(int index, GpuBuffer buffer) {
		inPass();
		Buf b = (Buf) buffer;
		state.bindUniformRange(index, b.name, 0, b.size);
	}

	@Override
	public void blend(Blend blend) {
		inPass();
		if (blend == null) {
			state.blendGl(false, 0, 0, 0, 0);
		} else {
			state.blendGl(true, blend.srcRgb(), blend.dstRgb(), blend.srcAlpha(), blend.dstAlpha());
		}
	}

	@Override
	public void blend(int drawBuffer, Blend blend) {
		inPass();
		if (blend == null) {
			state.blendGl(drawBuffer, false, 0, 0, 0, 0);
		} else {
			state.blendGl(drawBuffer, true, blend.srcRgb(), blend.dstRgb(), blend.srcAlpha(), blend.dstAlpha());
		}
	}

	@Override
	public void depth(int func, boolean write) {
		inPass();
		state.depthGl(func, write);
	}

	@Override
	public void colorMask(boolean r, boolean g, boolean b, boolean a) {
		inPass();
		state.colorMask((r ? 1 : 0) | (g ? 2 : 0) | (b ? 4 : 0) | (a ? 8 : 0));
	}

	@Override
	public void clearColor(int drawBuffer, float r, float g, float b, float a) {
		inPass();
		state.colorMask(15);
		GL30C.glClearBufferfv(GL11C.GL_COLOR, drawBuffer, new float[] {r, g, b, a});
	}

	@Override
	public void clearColorInt(int drawBuffer, int r, int g, int b, int a) {
		inPass();
		state.colorMask(15);
		GL30C.glClearBufferiv(GL11C.GL_COLOR, drawBuffer, new int[] {r, g, b, a});
	}

	@Override
	public void clearAlpha(int drawBuffer, float a) {
		inPass();
		state.colorMask(8); // alpha only: glClearBuffer follows the colour mask
		GL30C.glClearBufferfv(GL11C.GL_COLOR, drawBuffer, new float[] {0, 0, 0, a});
	}

	@Override
	public void clearDepth(float depth) {
		inPass();
		state.depthMask(true);
		GL30C.glClearBufferfv(GL11C.GL_DEPTH, 0, new float[] {depth});
	}

	@Override
	public void drawFullscreen() {
		inPass();
		if (quadVao == 0) {
			makeQuad();
		}
		// Fixed-function alpha test applies to shader output in a compatibility context.
		boolean alphaTest = GlStateTracker.isEnabled(GL_ALPHA_TEST);
		if (alphaTest) {
			GL11.glDisable(GL_ALPHA_TEST);
		}
		int mode = GlStateTracker.matrixMode();
		GL11.glMatrixMode(GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(0, 1, 0, 1, 0, 1);
		GL11.glMatrixMode(GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		// The texture matrix of unit 0: the active unit may be past the fixed-function texture coordinate units after
		// a pass bound its samplers (NVIDIA refuses those).
		state.activeTexture(0);
		GL11.glMatrixMode(GL_TEXTURE_MATRIX_MODE);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		state.bindVertexArray(quadVao);
		GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, 0, 4);
		GL11.glMatrixMode(GL_TEXTURE_MATRIX_MODE);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL_MODELVIEW);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(mode);
		if (alphaTest) {
			GL11.glEnable(GL_ALPHA_TEST);
		}
	}

	/** A strip of 4 vertices: position xy, texcoord uv (floats), colour white (bytes), as fixed-function arrays. */
	private void makeQuad() {
		ByteBuffer b = MemoryUtil.memAlloc(4 * 20);
		try {
			float[][] v = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
			for (float[] p : v) {
				b.putFloat(p[0]).putFloat(p[1]).putFloat(p[0]).putFloat(p[1]).putInt(-1);
			}
			b.flip();
			quadVao = GL30C.glGenVertexArrays();
			quadVbo = GL15C.glGenBuffers();
			counts.created(GlObjectCounts.Kind.VERTEX_ARRAY);
			counts.created(GlObjectCounts.Kind.BUFFER);
			state.bindVertexArray(quadVao);
			state.bindArrayBuffer(quadVbo);
			GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, b, GL15C.GL_STATIC_DRAW);
			GL11.glVertexPointer(2, GL11C.GL_FLOAT, 20, 0L);
			GL11.glColorPointer(4, GL11C.GL_UNSIGNED_BYTE, 20, 16L);
			int clientUnit = GL11C.glGetInteger(org.lwjgl.opengl.GL13.GL_CLIENT_ACTIVE_TEXTURE); // once, at creation
			org.lwjgl.opengl.GL13.glClientActiveTexture(GL13C.GL_TEXTURE0);
			GL11.glTexCoordPointer(2, GL11C.GL_FLOAT, 20, 8L);
			// Raw entry points: these arrays belong to our vertex array, not to the game's client state the tracker
			// mirrors.
			long enable = GL.getCapabilities().glEnableClientState;
			org.lwjgl.system.JNI.callV(GL11.GL_VERTEX_ARRAY, enable);
			org.lwjgl.system.JNI.callV(GL11.GL_COLOR_ARRAY, enable);
			org.lwjgl.system.JNI.callV(GL11.GL_TEXTURE_COORD_ARRAY, enable);
			org.lwjgl.opengl.GL13.glClientActiveTexture(clientUnit);
		} finally {
			MemoryUtil.memFree(b);
		}
	}

	@Override
	public void clearTexture(GpuTexture texture) {
		Tex t = (Tex) texture;
		org.lwjgl.opengl.GL44C.glClearTexImage(t.name, 0, t.spec.pixelFormat(), t.spec.pixelType(), (ByteBuffer) null);
	}

	@Override
	public int[] computeWorkGroupSize(int program) {
		int[] size = new int[3];
		GL20C.glGetProgramiv(program, GL43C.GL_COMPUTE_WORK_GROUP_SIZE, size);
		return size;
	}

	@Override
	public int gameTextureByPath(String path) {
		java.util.function.ToIntFunction<String> f = gameTextures;
		return f != null ? f.applyAsInt(path) : 0;
	}

	/** The version node's entity render for other cameras (development.md 6.7). */
	static volatile com.coredex.altrender.platform.EntityDrawer entityDrawer;
	/** True while the entity view draws (Altrender's own entity occlusion culling stays out of it). */
	static volatile boolean otherView;
	static volatile boolean otherViewEntities = true;
	static volatile boolean otherViewPlayer;
	private final com.coredex.altrender.terrain.Frustum viewFrustum = new com.coredex.altrender.terrain.Frustum();

	/** The API's entity view on the version's drawer (development.md 6.7). */
	boolean drawEntities(GpuFramebuffer target, int x, int y, int w, int h, float[] mv, float[] proj, double cx,
			double cy, double cz, float maxDistance, int program, boolean entities, boolean player) {
		com.coredex.altrender.platform.EntityDrawer d = entityDrawer;
		if (d == null) {
			return false;
		}
		viewFrustum.set(proj, mv);
		int prevFb = GameDraws.framebuffer();
		int prevProgram = GameDraws.program();
		int[] vp = new int[4];
		GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, vp);
		gameDraws(target, program);
		GL11C.glViewport(x, y, w, h);
		int mode = GlStateTracker.matrixMode();
		GL11.glMatrixMode(GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadMatrixf(proj);
		GL11.glMatrixMode(GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadMatrixf(mv);
		otherView = true;
		otherViewEntities = entities;
		otherViewPlayer = player;
		double reach2 = (double) maxDistance * maxDistance;
		try {
			d.draw((x0, y0, z0, x1, y1, z1) -> {
				double mx = (x0 + x1) * 0.5 - cx;
				double mz = (z0 + z1) * 0.5 - cz;
				if (mx * mx + mz * mz > reach2) {
					return false;
				}
				return viewFrustum.intersects((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz),
						(float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz));
			});
		} finally {
			otherView = false;
			otherViewEntities = true;
			otherViewPlayer = false;
			GL11.glMatrixMode(GL_MODELVIEW);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL_PROJECTION);
			GL11.glPopMatrix();
			GL11.glMatrixMode(mode);
			GL11C.glViewport(vp[0], vp[1], vp[2], vp[3]);
			GameDraws.set(prevFb, prevProgram);
			GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prevFb >= 0 ? prevFb : screen);
			GL20C.glUseProgram(prevProgram);
			state.invalidate();
		}
		return true;
	}

	/** The version node's lookup of its own textures by modern path (development.md 6.4). */
	static volatile java.util.function.ToIntFunction<String> gameTextures;

	@Override
	public void dispatch(int x, int y, int z) {
		inPass();
		GL43C.glDispatchCompute(x, y, z);
	}

	@Override
	public void memoryBarrier(int bits) {
		GL42C.glMemoryBarrier(bits);
	}

	@Override
	public void copyDepth(GpuFramebuffer from, GpuTexture texture) {
		copy(from, -1, texture);
	}

	@Override
	public void copyColor(GpuFramebuffer from, int attachment, GpuTexture texture) {
		copy(from, attachment, texture);
	}

	private void copy(GpuFramebuffer from, int attachment, GpuTexture texture) {
		inPass();
		Tex t = (Tex) texture;
		state.bindReadFramebuffer(from == null ? screen : ((Fb) from).name);
		if (attachment >= 0) {
			GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + attachment);
		}
		state.bindTexture(scratchUnit, t.target, t.name);
		GL11C.glCopyTexSubImage2D(t.spec.target(), 0, 0, 0, 0, 0, t.spec.width(), t.spec.height());
	}

	// ---- object classes ---------------------------------------------------------------------------------------

	private final class Tex implements GpuTexture {
		private TextureSpec spec;
		private final int name;
		private final int target;

		Tex(TextureSpec spec, int name, int target) {
			this.spec = spec;
			this.name = name;
			this.target = target;
		}

		void bind() {
			state.bindTexture(scratchUnit, target, name);
		}

		void allocate() {
			int levels = Math.max(1, spec.levels());
			for (int l = 0; l < levels; l++) {
				int w = Math.max(1, spec.width() >> l);
				int h = Math.max(1, spec.height() >> l);
				int d = Math.max(1, spec.depth() >> l);
				switch (target) {
					case GlState.TARGET_1D -> GL11C.glTexImage1D(spec.target(), l, spec.internalFormat(), w, 0,
							spec.pixelFormat(), spec.pixelType(), (ByteBuffer) null);
					case GlState.TARGET_3D -> GL12C.glTexImage3D(spec.target(), l, spec.internalFormat(), w, h, d, 0,
							spec.pixelFormat(), spec.pixelType(), (ByteBuffer) null);
					default -> GL11C.glTexImage2D(spec.target(), l, spec.internalFormat(), w, h, 0, spec.pixelFormat(),
							spec.pixelType(), (ByteBuffer) null);
				}
			}
		}

		@Override
		public TextureSpec spec() {
			return spec;
		}

		@Override
		public int name() {
			return name;
		}

		@Override
		public void resize(int width, int height) {
			spec = spec.withSize(width, height);
			own(() -> {
				bind();
				allocate();
			});
		}

		@Override
		public void upload(int level, int x, int y, int z, int width, int height, int depth, ByteBuffer pixels) {
			own(() -> {
				bind();
				// Tightly packed rows, whatever the game left in the unpack state.
				int align = GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_ALIGNMENT);
				int rowLength = GlStateTracker.pixelStoreValue(GL12C.GL_UNPACK_ROW_LENGTH);
				GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
				GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0);
				switch (target) {
					case GlState.TARGET_1D -> GL11C.glTexSubImage1D(spec.target(), level, x, width, spec.pixelFormat(),
							spec.pixelType(), pixels);
					case GlState.TARGET_3D -> GL12C.glTexSubImage3D(spec.target(), level, x, y, z, width, height, depth,
							spec.pixelFormat(), spec.pixelType(), pixels);
					default -> GL11C.glTexSubImage2D(spec.target(), level, x, y, width, height, spec.pixelFormat(),
							spec.pixelType(), pixels);
				}
				GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, align);
				GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, rowLength);
			});
		}

		@Override
		public void sampling(int minFilter, int magFilter, int wrap) {
			own(() -> {
				bind();
				int t = spec.target();
				GL11C.glTexParameteri(t, GL11C.GL_TEXTURE_MIN_FILTER, minFilter);
				GL11C.glTexParameteri(t, GL11C.GL_TEXTURE_MAG_FILTER, magFilter);
				int w = t == GL31C.GL_TEXTURE_RECTANGLE && wrap == GL11C.GL_REPEAT ? GL12C.GL_CLAMP_TO_EDGE : wrap;
				GL11C.glTexParameteri(t, GL11C.GL_TEXTURE_WRAP_S, w);
				GL11C.glTexParameteri(t, GL11C.GL_TEXTURE_WRAP_T, w);
				GL11C.glTexParameteri(t, GL12C.GL_TEXTURE_WRAP_R, w);
			});
		}

		@Override
		public void compare(int func) {
			own(() -> {
				bind();
				int t = spec.target();
				if (func == 0) {
					GL11C.glTexParameteri(t, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
				} else {
					GL11C.glTexParameteri(t, GL14C.GL_TEXTURE_COMPARE_MODE, GL30C.GL_COMPARE_REF_TO_TEXTURE);
					GL11C.glTexParameteri(t, GL14C.GL_TEXTURE_COMPARE_FUNC, func);
				}
			});
		}

		@Override
		public void generateMipmaps() {
			own(() -> {
				bind();
				GL30C.glGenerateMipmap(spec.target());
			});
		}

		@Override
		public void delete() {
			if (live.remove(this)) {
				GL11C.glDeleteTextures(name);
				state.forgetTexture(name);
				for (int u = 0; u < imageUnits.length; u++) {
					if ((int) (imageUnits[u] >>> 32) == name) {
						imageUnits[u] = 0; // deleting it unbinds it; a new texture may get its name
					}
				}
				counts.deleted(GlObjectCounts.Kind.TEXTURE);
			}
		}
	}

	private final class Fb implements GpuFramebuffer {
		private final int name;

		Fb(int name) {
			this.name = name;
		}

		@Override
		public int name() {
			return name;
		}

		private void attach(int attachment, GpuTexture texture, int level) {
			own(() -> {
				state.bindDrawFramebuffer(name);
				GL32C.glFramebufferTexture(GL30C.GL_DRAW_FRAMEBUFFER, attachment,
						texture == null ? 0 : ((Tex) texture).name, level);
			});
		}

		@Override
		public void color(int index, GpuTexture texture, int level) {
			attach(GL30C.GL_COLOR_ATTACHMENT0 + index, texture, level);
		}

		@Override
		public void depth(GpuTexture texture) {
			boolean stencil = texture != null && isDepthStencil(texture.spec().internalFormat());
			if (texture == null) {
				attach(GL30C.GL_DEPTH_STENCIL_ATTACHMENT, null, 0);
			} else {
				attach(stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT, texture, 0);
			}
		}

		private static boolean isDepthStencil(int format) {
			return format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8
					|| format == GL30C.GL_DEPTH_STENCIL;
		}

		@Override
		public void drawBuffers(int... attachments) {
			own(() -> {
				state.bindDrawFramebuffer(name);
				if (attachments.length == 0) {
					GL11C.glDrawBuffer(GL11C.GL_NONE);
				} else {
					int[] bufs = new int[attachments.length];
					for (int i = 0; i < bufs.length; i++) {
						bufs[i] = GL30C.GL_COLOR_ATTACHMENT0 + attachments[i];
					}
					GL20C.glDrawBuffers(bufs);
				}
			});
		}

		@Override
		public String check() {
			int[] status = new int[1];
			own(() -> {
				state.bindDrawFramebuffer(name);
				status[0] = GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER);
			});
			return status[0] == GL30C.GL_FRAMEBUFFER_COMPLETE ? null : "0x" + Integer.toHexString(status[0]);
		}

		@Override
		public void delete() {
			if (live.remove(this)) {
				if (GameDraws.framebuffer() == name) {
					gameDraws(null, GameDraws.program());
				}
				GL30C.glDeleteFramebuffers(name);
				state.forgetFramebuffer(name);
				counts.deleted(GlObjectCounts.Kind.FRAMEBUFFER);
			}
		}
	}

	private final class Buf implements GpuBuffer {
		private final int name;
		private final long size;

		Buf(int name, long size) {
			this.name = name;
			this.size = size;
		}

		void zero() {
			ByteBuffer z = MemoryUtil.memCalloc((int) Math.min(size, 1 << 20));
			try {
				for (long at = 0; at < size; at += z.capacity()) {
					z.limit((int) Math.min(z.capacity(), size - at));
					GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, at, z);
				}
			} finally {
				MemoryUtil.memFree(z);
			}
		}

		@Override
		public long size() {
			return size;
		}

		@Override
		public void upload(long offset, ByteBuffer data) {
			own(() -> {
				state.bindArrayBuffer(name);
				GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, offset, data);
			});
		}

		@Override
		public void clear() {
			own(() -> {
				state.bindArrayBuffer(name);
				zero();
			});
		}

		@Override
		public void delete() {
			if (live.remove(this)) {
				GL15C.glDeleteBuffers(name);
				state.forgetBuffer(name);
				counts.deleted(GlObjectCounts.Kind.BUFFER);
			}
		}
	}

}
