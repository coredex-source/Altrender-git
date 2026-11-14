/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl45;

import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.OutOfGpuMemoryException;
import com.coredex.altrender.strategy.PixelLayout;
import com.coredex.altrender.strategy.SamplerState;
import com.coredex.altrender.strategy.TexFormat;
import com.coredex.altrender.strategy.TextureStrategy;
import com.coredex.altrender.strategy.gl33.Gl33Formats;
import com.coredex.altrender.gl.GlStateTracker;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL45C;

import java.util.HashMap;
import java.util.Map;

/**
 * GL 4.5 textures: immutable storage made and edited by name ({@code glCreateTextures},
 * {@code glTextureStorage2D}, {@code glTextureSubImage*}, {@code glTextureParameteri}), so uploads and mip-range changes
 * don't disturb any texture unit. Sampling binds by unit ({@code glBindTextureUnit}, cached in {@link GlState}), so it
 * never switches the active unit. Sampler objects as on gl33.
 */
public final class Gl45TextureStrategy implements TextureStrategy {
	private static final int GL_TEXTURE_MAX_ANISOTROPY = 0x84FE;

	private final GlObjectCounts counts;
	private final GlState state;
	private final boolean anisotropy;
	private final Map<Integer, int[]> levels = new HashMap<>();

	public Gl45TextureStrategy(GlCaps caps, GlObjectCounts counts, GlState state) {
		this.counts = counts;
		this.state = state;
		this.anisotropy = caps.anisotropic;
	}

	@Override
	public String id() {
		return "gl45-texstorage";
	}

	@Override
	public int create(TexFormat format, int width, int height, int mipLevels, boolean cubemap) {
		int texture = GL45C.glCreateTextures(cubemap ? GL13C.GL_TEXTURE_CUBE_MAP : GL11C.GL_TEXTURE_2D);
		GL45C.glTextureStorage2D(texture, mipLevels, Gl33Formats.internalFormat(format), width, height);
		if (GL11C.glGetError() == GL11C.GL_OUT_OF_MEMORY) {
			GL11C.glDeleteTextures(texture);
			throw new OutOfGpuMemoryException("texture " + format + " " + width + "x" + height + " x" + mipLevels
					+ " levels ran out of memory");
		}
		GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
		GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);
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
		int uploadFormat = Gl33Formats.uploadFormat(layout, format.isInteger());
		int type = format.isInteger() ? GL11C.GL_BYTE : GL11C.GL_UNSIGNED_BYTE;
		GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
		GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, rowLength);
		GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, skipPixels);
		GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, skipRows);
		GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
		if (cubemap) {
			// A cube map is a 6-layer image to DSA: the face is the z offset.
			GL45C.nglTextureSubImage3D(texture, level, x, y, face, width, height, 1, uploadFormat, type, address);
		} else {
			GL45C.nglTextureSubImage2D(texture, level, x, y, width, height, uploadFormat, type, address);
		}
		// Back to the game's values (development.md 1.5), not GL's defaults: its screenshot code leaves alignment 1.
		GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_ROW_LENGTH));
		GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_SKIP_PIXELS));
		GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_SKIP_ROWS));
		GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, GlStateTracker.pixelStoreValue(GL11C.GL_UNPACK_ALIGNMENT));
	}

	@Override
	public void bindForSampling(int unit, int texture, boolean cubemap, int baseLevel, int maxLevel, int sampler) {
		int[] current = levels.get(texture);
		if (current == null || current[0] != baseLevel || current[1] != maxLevel) {
			GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_BASE_LEVEL, baseLevel);
			GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_MAX_LEVEL, maxLevel);
			levels.put(texture, new int[] {baseLevel, maxLevel});
		}
		state.bindTextureUnit(unit, cubemap ? GlState.TARGET_CUBE : GlState.TARGET_2D, texture);
		state.bindSampler(unit, sampler);
	}

	@Override
	public int createSampler(SamplerState s) {
		int sampler = GL45C.glCreateSamplers();
		org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_S, s.repeatU() ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);
		org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_T, s.repeatV() ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);
		// Mip levels always blend linearly; the view's mip range limits what is sampled.
		org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MIN_FILTER,
				s.linearMin() ? GL11C.GL_LINEAR_MIPMAP_LINEAR : GL11C.GL_NEAREST_MIPMAP_LINEAR);
		org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MAG_FILTER, s.linearMag() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		if (anisotropy && s.maxAnisotropy() > 1) {
			org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, GL_TEXTURE_MAX_ANISOTROPY, s.maxAnisotropy());
		}
		if (!Double.isNaN(s.maxLod())) {
			org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, GL12C.GL_TEXTURE_MAX_LOD, (float) s.maxLod());
		}
		counts.created(GlObjectCounts.Kind.SAMPLER);
		return sampler;
	}

	@Override
	public void deleteSampler(int sampler) {
		org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
		state.forgetSampler(sampler);
		counts.deleted(GlObjectCounts.Kind.SAMPLER);
	}
}
