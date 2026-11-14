/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.Log;
import com.coredex.altrender.caps.Tier;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.strategy.BlendFactor;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.Compare;
import com.coredex.altrender.strategy.IndexType;
import com.coredex.altrender.strategy.PixelLayout;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.strategy.RasterState;
import com.coredex.altrender.strategy.SamplerState;
import com.coredex.altrender.strategy.ShaderStage;
import com.coredex.altrender.strategy.Strategies;
import com.coredex.altrender.strategy.TexFormat;
import com.coredex.altrender.strategy.VertexLayout;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

/**
 * {@code -Daltrender.debug.resourceTest} (development.md 1.5): once, inside a real pass in the middle of the game's
 * frame, every strategy creates its objects, draws into a 16×16 offscreen target and deletes everything again.
 * <p>
 * The draw covers known GL pitfalls: two quads from one index buffer through {@code multiDrawElements} with
 * different base vertices (a swapped count/base-vertex argument draws nothing or garbage), each coloured from an
 * {@code R32I} texel buffer indexed by quad, times a UBO tint, times a sampled 1×1 texture, at an offset from a plain
 * uniform (per-draw data). On gl45 the same draw runs again as indirect draws. Passes when the pixels read back right,
 * every object count is back at its baseline and the driver reported no error.
 */
public final class ResourceSelfTest {

	private static final int SIZE = 16;
	private static final int RED = 0x0000FF;
	private static final int GREEN = 0x00FF00;

	private static final String VERTEX = """
			#version 330 core
			in vec2 position;
			uniform vec2 offset;
			flat out int quad;
			void main() {
				quad = gl_VertexID / 4; // gl_VertexID includes the base vertex
				gl_Position = vec4(position + offset, 0.0, 1.0);
			}
			""";

	private static final String FRAGMENT = """
			#version 330 core
			flat in int quad;
			uniform isamplerBuffer colours;
			uniform sampler2D white;
			layout(std140) uniform Tint {
				vec4 tint;
			};
			out vec4 colour;
			void main() {
				int c = texelFetch(colours, quad).r;
				vec4 rgb = vec4(float(c & 255), float((c >> 8) & 255), float((c >> 16) & 255), 255.0) / 255.0;
				colour = rgb * tint * texture(white, vec2(0.5));
			}
			""";

	private ResourceSelfTest() {
	}

	/** Runs the test; logs the result. Call between {@link Strategies#beginPass} and {@link Strategies#endPass}. */
	public static boolean run(Strategies s) {
		List<String> failures = new ArrayList<>();
		Map<GlObjectCounts.Kind, Integer> baseline = s.counts().snapshot();
		int errorsBefore = GlDebug.errors();
		drainGlErrors();

		// Two quads: left half (vertices 0-3) and right half (4-7) of clip space; one shared index list.
		ByteBuffer vertices = MemoryUtil.memAlloc(8 * 2 * 4).order(ByteOrder.nativeOrder());
		float[][] quads = {{-1f, 0f}, {0f, 1f}};
		for (float[] q : quads) {
			vertices.putFloat(q[0]).putFloat(-1f).putFloat(q[1]).putFloat(-1f).putFloat(q[1]).putFloat(1f)
					.putFloat(q[0]).putFloat(1f);
		}
		vertices.flip();
		ByteBuffer indices = MemoryUtil.memAlloc(6 * 2).order(ByteOrder.nativeOrder());
		for (int i : new int[] {0, 1, 2, 2, 3, 0}) {
			indices.putShort((short) i);
		}
		indices.flip();
		ByteBuffer colours = MemoryUtil.memAlloc(2 * 4).order(ByteOrder.nativeOrder());
		colours.putInt(RED).putInt(GREEN).flip();
		ByteBuffer tint = MemoryUtil.memAlloc(16).order(ByteOrder.nativeOrder());
		tint.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(1f).flip();
		ByteBuffer whitePixel = MemoryUtil.memAlloc(4);
		whitePixel.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();

		int vbo = s.buffer().create(vertices, BufferUsage.VERTEX);
		int ibo = s.buffer().create(indices, BufferUsage.INDEX);
		int texel = s.buffer().create(colours, BufferUsage.UNIFORM_TEXEL_BUFFER);
		int ubo = s.buffer().create(tint, BufferUsage.UNIFORM);
		int white = s.texture().create(TexFormat.RGBA8, 1, 1, 1, false);
		s.texture().upload(white, TexFormat.RGBA8, false, 0, 0, 0, 0, 1, 1, PixelLayout.RGBA,
				MemoryUtil.memAddress(whitePixel), 1, 0, 0);
		int sampler = s.texture().createSampler(new SamplerState(false, false, false, false, 1, 0.0));

		ProgramStrategy.Result vs = s.program().compile(ShaderStage.VERTEX, VERTEX);
		ProgramStrategy.Result fs = s.program().compile(ShaderStage.FRAGMENT, FRAGMENT);
		ProgramStrategy.Result program = vs.ok() && fs.ok()
				? s.program().link(vs.name(), fs.name(), List.of("position"), List.of("colour"))
				: new ProgramStrategy.Result(0, false, "compile: " + vs.log() + " " + fs.log());
		if (!program.ok()) {
			failures.add("program: " + program.log().trim());
		}

		// Offscreen target: plain GL objects, deleted below (no framebuffer strategy until AltShaders needs one).
		int target = GL11C.glGenTextures();
		s.state().bindTexture(0, com.coredex.altrender.strategy.GlState.TARGET_2D, target);
		GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, SIZE, SIZE, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE,
				(ByteBuffer) null);
		int fbo = GL30C.glGenFramebuffers();
		s.state().bindDrawFramebuffer(fbo);
		GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, target, 0);
		if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
			failures.add("offscreen target incomplete");
		}

		VertexLayout layout = new VertexLayout(8, List.of(
				new VertexLayout.Attribute(0, "position", 2, VertexLayout.AttribType.FLOAT, false, 0)));
		int fence = 0;
		long fenceHandle = 0;
		int timer = 0;
		if (program.ok() && failures.isEmpty()) {
			s.state().viewport(0, 0, SIZE, SIZE);
			s.state().apply(new RasterState(false, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE, BlendFactor.ZERO,
					0xF, false, Compare.ALWAYS, false, 0f, 0f, false, false, null));
			s.state().useProgram(program.name());
			s.program().setUniform2f(s.program().uniformLocation(program.name(), "offset"), 0f, 0f);
			s.program().setSamplerUnit(program.name(), "colours", 0);
			s.program().setSamplerUnit(program.name(), "white", 1);
			List<String> blocks = s.uniform().configureProgram(program.name());
			if (!blocks.equals(List.of("Tint"))) {
				failures.add("uniform blocks " + blocks);
			}
			s.uniform().bindBlock(0, ubo, 0, 16);
			s.uniform().bindTexelBuffer(0, texel, TexFormat.R32I);
			s.texture().bindForSampling(1, white, false, 0, 0, sampler);
			s.vertexInput().bind(layout, vbo, ibo);

			timer = s.sync().beginTimer();
			draw(s, false);
			check(failures, "multiDrawElements", fbo, s);
			if (s.tier() == Tier.GL45) {
				draw(s, true);
				check(failures, "multiDrawElementsIndirect", fbo, s);
			}
			s.sync().endTimer();
			fenceHandle = s.sync().fence();
			if (!s.sync().await(fenceHandle, 1_000_000_000L)) {
				failures.add("fence not signalled within 1 s");
			}
			long gpuNs = s.sync().timerResult(timer);
			Log.info("resource self-test: draws took " + (gpuNs >= 0 ? gpuNs / 1000 + " µs" : "(timer not ready)")
					+ " on the GPU");
		}

		// Everything goes again: strategies first, then the plain target.
		if (fenceHandle != 0) {
			s.sync().delete(fenceHandle);
		}
		if (timer != 0) {
			s.sync().deleteTimer(timer);
		}
		s.vertexInput().clear();
		if (program.ok()) {
			s.program().deleteProgram(program.name());
		}
		if (vs.name() != 0) {
			s.program().deleteShader(vs.name());
		}
		if (fs.name() != 0) {
			s.program().deleteShader(fs.name());
		}
		s.texture().deleteSampler(sampler);
		s.texture().delete(white);
		s.buffer().delete(vbo);
		s.buffer().delete(ibo);
		s.buffer().delete(texel);
		s.buffer().delete(ubo);
		GL30C.glDeleteFramebuffers(fbo);
		s.state().forgetFramebuffer(fbo);
		GL11C.glDeleteTextures(target);
		s.state().forgetTexture(target);
		MemoryUtil.memFree(vertices);
		MemoryUtil.memFree(indices);
		MemoryUtil.memFree(colours);
		MemoryUtil.memFree(tint);
		MemoryUtil.memFree(whitePixel);

		Map<GlObjectCounts.Kind, Integer> after = s.counts().snapshot();
		if (!after.equals(baseline)) {
			failures.add("object counts " + GlObjectCounts.describe(after) + " vs baseline "
					+ GlObjectCounts.describe(baseline));
		}
		int glErrors = drainGlErrors();
		if (glErrors != 0) {
			failures.add(glErrors + " glGetError error(s)");
		}
		if (GlDebug.errors() != errorsBefore) {
			failures.add((GlDebug.errors() - errorsBefore) + " KHR_debug error message(s)");
		}
		if (failures.isEmpty()) {
			Log.info("resource self-test: passed (" + s.tier() + ", objects back to baseline, 0 GL errors)");
			return true;
		}
		Log.warn("resource self-test: FAILED: " + failures);
		return false;
	}

	/** Two quads, counts 6 and 6, base vertices 0 and 4: through multi-draw, or indirect commands (gl45). */
	private static void draw(Strategies s, boolean indirect) {
		float[] clear = new float[4];
		GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, clear);
		GL11C.glClearColor(0f, 0f, 0f, 0f);
		GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
		GL11C.glClearColor(clear[0], clear[1], clear[2], clear[3]);
		if (!indirect) {
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				PointerBuffer offsets = stack.mallocPointer(2).put(0, 0L).put(1, 0L);
				IntBuffer counts = stack.ints(6, 6);
				IntBuffer baseVertices = stack.ints(0, 4);
				s.vertexInput().multiDrawElements(Primitive.TRIANGLES, IndexType.U16, offsets, counts, baseVertices, 2);
			}
			return;
		}
		ByteBuffer commands = MemoryUtil.memAlloc(2 * 5 * 4).order(ByteOrder.nativeOrder());
		// count, instances, first index, base vertex, first instance
		commands.putInt(6).putInt(1).putInt(0).putInt(0).putInt(0).putInt(6).putInt(1).putInt(0).putInt(4).putInt(0).flip();
		int buffer = s.buffer().create(commands, BufferUsage.VERTEX);
		MemoryUtil.memFree(commands);
		s.vertexInput().multiDrawElementsIndirect(Primitive.TRIANGLES, IndexType.U16, buffer, 0, 2);
		s.buffer().delete(buffer);
	}

	/** Left half red, right half green. */
	private static void check(List<String> failures, String what, int fbo, Strategies s) {
		s.state().bindReadFramebuffer(fbo);
		ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 4);
		GL11C.glReadPixels(0, 0, SIZE, SIZE, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
		GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, GlStateTracker.pixelStoreValue(GL11C.GL_PACK_ALIGNMENT));
		int wrong = 0;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int i = (y * SIZE + x) * 4;
				int rgb = (pixels.get(i) & 0xFF) | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF) << 16;
				if (rgb != (x < SIZE / 2 ? RED : GREEN)) {
					wrong++;
				}
			}
		}
		MemoryUtil.memFree(pixels);
		if (wrong != 0) {
			failures.add(what + ": " + wrong + " of " + SIZE * SIZE + " pixels wrong");
		}
	}

	private static int drainGlErrors() {
		int n = 0;
		while (GL11C.glGetError() != GL11C.GL_NO_ERROR && n < 64) {
			n++;
		}
		return n;
	}
}
