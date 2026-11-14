/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;
import com.coredex.altshaders.pack.gl.InternalTextureFormat;
import com.coredex.altshaders.pack.gl.ShaderDataType;
import com.coredex.altshaders.pack.helpers.Vector2i;
import com.coredex.altshaders.pack.properties.PackDirectives;
import com.coredex.altshaders.pack.properties.PackRenderTargetDirectives;

/**
 * A pack's {@code colortex0–31} and {@code depthtex0–2} (development.md 6.4). Each colour target has two textures, so
 * a composite pass can read a buffer while writing it: passes read the current side and write the other, which then
 * becomes current ("flipping", as OptiFine and Iris). Targets are made when first asked for, in the pack's format
 * and size ({@code size.buffer.*}).
 */
final class RenderTargets {

	static final int GL_TEXTURE_2D = 0x0DE1;
	private static final int GL_DEPTH_COMPONENT = 0x1902;
	private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
	private static final int GL_FLOAT = 0x1406;
	private static final int GL_INT = 0x1404;
	private static final int GL_UNSIGNED_INT = 0x1405;
	private static final int GL_LINEAR = 0x2601;
	private static final int GL_NEAREST = 0x2600;
	private static final int GL_CLAMP_TO_EDGE = 0x812F;

	/** One colour buffer: its two sides and which one is current. */
	static final class Target {
		final int index;
		final InternalTextureFormat format;
		final GpuTexture[] sides = new GpuTexture[2];
		int width;
		int height;
		/** 0 or 1: the side passes read. */
		int current;
		/**
		 * Cleared at the next frame's start whatever the pack's {@code colortexNClear} (Iris's full clear: once
		 * after the buffer is made or resized).
		 */
		boolean fullClear = true;
		/**
		 * Per side: a pass made its mipmaps this frame, so passes read it with mipmap filtering until the frame ends
		 * (Iris's, as OptiFine's).
		 */
		final boolean[] mipsOn = new boolean[2];
		/** Per side: written since its mipmaps were last made (they're made again only then). */
		final boolean[] mipsStale = {true, true};

		Target(int index, InternalTextureFormat format) {
			this.index = index;
			this.format = format;
		}

		GpuTexture read() {
			return sides[current];
		}

		GpuTexture write() {
			return sides[current ^ 1];
		}
	}

	private final Gpu gpu;
	private final PackDirectives directives;
	private final Target[] targets = new Target[PackRenderTargetDirectives.MAX_COLOR_BUFFERS];
	/** Buffers some pass reads with mipmaps ({@code colortexNMipmapEnabled}): made with their mip chain. */
	private final java.util.Set<Integer> mipmapped = new java.util.HashSet<>();
	private int width;
	private int height;
	GpuTexture depth0;
	GpuTexture depth1;
	GpuTexture depth2;

	RenderTargets(Gpu gpu, PackDirectives directives, int width, int height) {
		this.gpu = gpu;
		this.directives = directives;
		this.width = width;
		this.height = height;
		depth0 = depthTexture("depthtex0");
		depth1 = depthTexture("depthtex1");
		depth2 = depthTexture("depthtex2");
	}

	private GpuTexture depthTexture(String name) {
		// 24 bits, as the game's own framebuffer: the same depth test results as without a pack.
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_FLOAT, width,
				height), "altshaders " + name);
		t.sampling(GL_NEAREST, GL_NEAREST, GL_CLAMP_TO_EDGE);
		return t;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	void mipmapped(java.util.Set<Integer> buffers) {
		mipmapped.addAll(buffers);
	}

	/** colortex{@code index}, made on first use. */
	Target get(int index) {
		Target t = targets[index];
		if (t == null) {
			PackRenderTargetDirectives.RenderTargetSettings settings = directives.getRenderTargetDirectives()
					.getRenderTargetSettings().get(index);
			InternalTextureFormat format = settings != null ? settings.getInternalFormat() : InternalTextureFormat.RGBA;
			t = new Target(index, format);
			allocate(t);
			targets[index] = t;
		}
		return t;
	}

	/** colortex{@code index} if it exists, else null (nothing made). */
	Target peek(int index) {
		return targets[index];
	}

	private void allocate(Target t) {
		Vector2i size = directives.getTextureScaleOverride(t.index, width, height);
		t.width = Math.max(1, size.x);
		t.height = Math.max(1, size.y);
		int pixelType = switch (t.format.getShaderDataType()) {
			case FLOAT -> GL_FLOAT;
			case INT -> GL_INT;
			case UINT -> GL_UNSIGNED_INT;
		};
		int filter = t.format.getShaderDataType() == ShaderDataType.FLOAT ? GL_LINEAR : GL_NEAREST;
		int levels = mipmapped.contains(t.index)
				? 32 - Integer.numberOfLeadingZeros(Math.max(t.width, t.height)) : 1;
		for (int side = 0; side < 2; side++) {
			if (t.sides[side] != null) {
				t.sides[side].delete();
			}
			GpuTexture tex = gpu.createTexture(new TextureSpec(GL_TEXTURE_2D, t.format.getGlFormat(),
					t.format.getPixelFormat().getGlFormat(), pixelType, t.width, t.height, 1, levels),
					"altshaders colortex" + t.index + (side == 0 ? "" : " alt"));
			tex.sampling(filter, filter, GL_CLAMP_TO_EDGE);
			t.sides[side] = tex;
		}
		t.fullClear = true;
		t.mipsOn[0] = t.mipsOn[1] = false;
		t.mipsStale[0] = t.mipsStale[1] = true;
	}

	/**
	 * Mipmap filtering on the side {@code t} is read from now, with its mipmaps made if it was written since they
	 * last were (Iris makes them for every pass that asks; unchanged texels give the same mipmaps).
	 */
	void mipmaps(Target t) {
		int side = t.current;
		if (t.mipsStale[side] || PackProgram.NO_74) {
			t.sides[side].generateMipmaps();
			t.mipsStale[side] = false;
		}
		if (!t.mipsOn[side]) {
			t.mipsOn[side] = true;
			boolean linear = t.format.getShaderDataType() == ShaderDataType.FLOAT;
			// Iris's MIPPED_LINEAR / MIPPED_NEAREST samplers.
			t.sides[side].sampling(linear ? 0x2703 : 0x2702, linear ? GL_LINEAR : GL_NEAREST, GL_CLAMP_TO_EDGE);
		}
	}

	/** Every buffer's sides may have been written (the game's draws between stages): mipmaps made again on use. */
	void allWritten() {
		for (Target t : targets) {
			if (t != null) {
				t.mipsStale[0] = t.mipsStale[1] = true;
			}
		}
	}

	/** Every buffer back to its first side (each frame starts unflipped), mipmap filtering off (Iris's frame end). */
	void resetFlips() {
		for (Target t : targets) {
			if (t != null) {
				t.current = 0;
				for (int side = 0; side < 2; side++) {
					if (t.mipsOn[side]) {
						t.mipsOn[side] = false;
						int filter = t.format.getShaderDataType() == ShaderDataType.FLOAT ? GL_LINEAR : GL_NEAREST;
						t.sides[side].sampling(filter, filter, GL_CLAMP_TO_EDGE);
					}
					t.mipsStale[side] = true;
				}
			}
		}
	}

	/** The game's framebuffer size changed: everything again at the new size (contents lost). */
	void resize(int w, int h) {
		if (w == width && h == height) {
			return;
		}
		width = w;
		height = h;
		depth0.resize(w, h);
		depth1.resize(w, h);
		depth2.resize(w, h);
		for (Target t : targets) {
			if (t != null) {
				allocate(t);
			}
		}
	}

	void delete() {
		for (int i = 0; i < targets.length; i++) {
			Target t = targets[i];
			if (t != null) {
				t.sides[0].delete();
				t.sides[1].delete();
				targets[i] = null;
			}
		}
		depth0.delete();
		depth1.delete();
		depth2.delete();
	}
}
