/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.OutOfGpuMemoryException;
import com.coredex.altrender.strategy.PixelLayout;
import com.coredex.altrender.strategy.SamplerState;
import com.coredex.altrender.strategy.TexFormat;
import com.coredex.altrender.strategy.TextureStrategy;
import com.coredex.altrender.gl.GlStateTracker;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * GL 3.3 textures: mutable storage via {@code glTexImage2D} per level (and per face for cube maps),
 * {@code glTexSubImage2D} uploads from client memory, and sampler objects.
 */
public final class Gl33TextureStrategy implements TextureStrategy {
	private static final int GL_TEXTURE_MAX_ANISOTROPY = 0x84FE;

	private final GlObjectCounts counts;
	private final GlState state;
	private final boolean anisotropy;
	/** Current {base, max} level per texture, so binding a view only touches them when its range differs. */
	private final Map<Integer, int[]> levels = new HashMap<>();

	public Gl33TextureStrategy(GlCaps caps, GlObjectCounts counts, GlState state) {
		this.counts = counts;
		this.state = state;
		this.anisotropy = caps.anisotropic;
	}

	@Override
	public String id() {
		return "gl33-teximage";
	}

	@Override
	public int create(TexFormat format, int width, int height, int mipLevels, boolean cubemap) {
		int internal = Gl33Formats.internalFormat(format);
		int external = Gl33Formats.externalFormat(format);
		int type = Gl33Formats.externalType(format);
		int target = target(cubemap);
		int texture = GL11C.glGenTextures();
		state.bindTexture(0, cubemap ? GlState.TARGET_CUBE : GlState.TARGET_2D, texture);
		GL11C.glTexParameteri(target, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
		GL11C.glTexParameteri(target, GL12C.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);
		for (int level = 0; level < mipLevels; level++) {
			int w = Math.max(1, width >> level);
			int h = Math.max(1, height >> level);
			if (cubemap) {
				for (int face = 0; face < 6; face++) {
					GL11C.glTexImage2D(GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, level, internal, w, h, 0, external, type,
							(ByteBuffer) null);
				}
			} else {
				GL11C.glTexImage2D(target, level, internal, w, h, 0, external, type, (ByteBuffer) null);
			}
		}
		if (GL11C.glGetError() == GL11C.GL_OUT_OF_MEMORY) {
			GL11C.glDeleteTextures(texture);
			state.forgetTexture(texture);
			throw new OutOfGpuMemoryException("texture " + format + " " + width + "x" + height + " x" + mipLevels
					+ " levels ran out of memory");
		}
		counts.created(GlObjectCounts.Kind.TEXTURE);
		levels.put(texture, new int[] {0, mipLevels - 1});
		return texture;
	}

	@Override
	public void delete(int texture) {
		GL11C.glDeleteTextures(texture);
		state.forgetTexture(texture);
		levels.remove(texture);
		counts.deleted(GlObjectCounts.Kind.TEXTURE);
	}

	@Override
	public void upload(int texture, TexFormat format, boolean cubemap, int face, int level, int x, int y, int width,
					   int height, PixelLayout layout, long address, int rowLength, int skipPixels, int skipRows) {
		int imageTarget = cubemap ? GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face : GL11C.GL_TEXTURE_2D;
		state.bindTexture(0, cubemap ? GlState.TARGET_CUBE : GlState.TARGET_2D, texture);
		Unpack.set(rowLength, skipPixels, skipRows);
		GL11C.nglTexSubImage2D(imageTarget, level, x, y, width, height,
				Gl33Formats.uploadFormat(layout, format.isInteger()),
				format.isInteger() ? GL11C.GL_BYTE : GL11C.GL_UNSIGNED_BYTE, address);
		Unpack.reset();
	}

	@Override
	public void bindForSampling(int unit, int texture, boolean cubemap, int baseLevel, int maxLevel, int sampler) {
		state.bindTexture(unit, cubemap ? GlState.TARGET_CUBE : GlState.TARGET_2D, texture);
		int[] current = levels.get(texture);
		if (current == null || current[0] != baseLevel || current[1] != maxLevel) {
			int target = target(cubemap);
			GL11C.glTexParameteri(target, GL12C.GL_TEXTURE_BASE_LEVEL, baseLevel);
			GL11C.glTexParameteri(target, GL12C.GL_TEXTURE_MAX_LEVEL, maxLevel);
			levels.put(texture, new int[] {baseLevel, maxLevel});
		}
		state.bindSampler(unit, sampler);
	}

	@Override
	public int createSampler(SamplerState state) {
		int sampler = GL33C.glGenSamplers();
		GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_S, state.repeatU() ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);
		GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_T, state.repeatV() ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);
		// Mip levels always blend linearly; the view's mip range limits what is sampled.
		GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MIN_FILTER,
				state.linearMin() ? GL11C.GL_LINEAR_MIPMAP_LINEAR : GL11C.GL_NEAREST_MIPMAP_LINEAR);
		GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MAG_FILTER, state.linearMag() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		if (anisotropy && state.maxAnisotropy() > 1) {
			GL33C.glSamplerParameterf(sampler, GL_TEXTURE_MAX_ANISOTROPY, state.maxAnisotropy());
		}
		if (!Double.isNaN(state.maxLod())) {
			GL33C.glSamplerParameterf(sampler, GL12C.GL_TEXTURE_MAX_LOD, (float) state.maxLod());
		}
		counts.created(GlObjectCounts.Kind.SAMPLER);
		return sampler;
	}

	@Override
	public void deleteSampler(int sampler) {
		GL33C.glDeleteSamplers(sampler);
		state.forgetSampler(sampler);
		counts.deleted(GlObjectCounts.Kind.SAMPLER);
	}

	private static int target(boolean cubemap) {
		return cubemap ? GL13C.GL_TEXTURE_CUBE_MAP : GL11C.GL_TEXTURE_2D;
	}

	/** Unpack state for client-memory uploads; reset to GL defaults afterwards so other GL users see clean state. */
	private static final class Unpack {
		static void set(int rowLength, int skipPixels, int skipRows) {
			GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
			GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, rowLength);
			GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, skipPixels);
			GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, skipRows);
			GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
		}

		static void reset() {
			// Back to the game's values (development.md 1.5), not GL's defaults: its screenshot code leaves alignment 1.
			GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_ROW_LENGTH));
			GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_SKIP_PIXELS));
			GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_SKIP_ROWS));
			GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_ALIGNMENT));
		}
	}
}
