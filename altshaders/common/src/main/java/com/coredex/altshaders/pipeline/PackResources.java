/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;
import com.coredex.altshaders.Log;
import com.coredex.altshaders.pack.ImageInformation;
import com.coredex.altshaders.pack.ShaderPack;
import com.coredex.altshaders.pack.texture.CustomTextureData;
import com.coredex.altshaders.pack.texture.TextureFilteringData;
import com.coredex.altshaders.pack.texture.TextureStage;

/**
 * A pack's own textures and images on the GPU (development.md 6.4): custom textures ({@code texture.<stage>.<name>},
 * {@code customTexture.<name>}, {@code texture.noise}: PNG, raw 1D/2D/3D data, the game's own by path, the lightmap)
 * and custom images ({@code image.<name>}, written by compute and fragment programs).
 */
final class PackResources {

	private static final int GL_TEXTURE_1D = 0x0DE0, GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_3D = 0x806F;
	private static final int GL_TEXTURE_RECTANGLE = 0x84F5;
	private static final int GL_RGBA8 = 0x8058, GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
	private static final int GL_LINEAR = 0x2601, GL_NEAREST = 0x2600, GL_REPEAT = 0x2901, GL_CLAMP_TO_EDGE = 0x812F;

	/** A texture a sampler can read: one made here, the game's own (by GL name), or the lightmap. */
	record TexRef(GpuTexture texture, int gameName, boolean lightmap) {
	}

	/** A pack image: its texture, the GL format image units take, and whether it's cleared each frame. */
	record Image(ImageInformation info, GpuTexture texture) {
	}

	private final Gpu gpu;
	private final List<GpuTexture> owned = new ArrayList<>();
	private final Map<String, TexRef> named = new HashMap<>();
	private final EnumMap<TextureStage, Map<String, TexRef>> staged = new EnumMap<>(TextureStage.class);
	private TexRef noise;
	private final Map<String, Image> imagesByName = new HashMap<>();
	private final Map<String, Image> imagesBySampler = new HashMap<>();
	private final List<Image> images = new ArrayList<>();

	PackResources(Gpu gpu, ShaderPack pack, int width, int height) {
		this.gpu = gpu;
		pack.getIrisCustomTextureDataMap().forEach((name, data) -> put(named, name, data));
		pack.getCustomTextureDataMap().forEach((stage, map) -> {
			Map<String, TexRef> m = new HashMap<>();
			map.forEach((name, data) -> put(m, name, data));
			staged.put(stage, m);
		});
		if (pack.getCustomNoiseTexture() != null) {
			noise = upload("noisetex", pack.getCustomNoiseTexture());
		}
		for (ImageInformation info : pack.getIrisCustomImages()) {
			int w = info.isRelative() ? Math.max(1, (int) (width * info.relativeWidth())) : info.width();
			int h = info.isRelative() ? Math.max(1, (int) (height * info.relativeHeight())) : info.height();
			int target = switch (info.target()) {
				case TEXTURE_1D -> GL_TEXTURE_1D;
				case TEXTURE_3D -> GL_TEXTURE_3D;
				case TEXTURE_RECTANGLE -> GL_TEXTURE_RECTANGLE;
				default -> GL_TEXTURE_2D;
			};
			GpuTexture t = gpu.createTexture(new TextureSpec(target, info.internalTextureFormat().getGlFormat(),
					info.format().getGlFormat(), info.type().getGlFormat(), w, Math.max(1, h),
					Math.max(1, info.depth()), 1), "altshaders image " + info.name());
			t.sampling(GL_NEAREST, GL_NEAREST, GL_CLAMP_TO_EDGE);
			gpu.clearTexture(t);
			owned.add(t);
			Image image = new Image(info, t);
			images.add(image);
			imagesByName.put(info.name(), image);
			if (info.samplerName() != null && !info.samplerName().isEmpty()) {
				imagesBySampler.put(info.samplerName(), image);
			}
		}
	}

	private void put(Map<String, TexRef> into, String name, CustomTextureData data) {
		TexRef t = upload(name, data);
		if (t != null) {
			into.put(name, t);
		}
	}

	private TexRef upload(String name, CustomTextureData data) {
		try {
			if (data instanceof CustomTextureData.LightmapMarker) {
				return new TexRef(null, 0, true);
			}
			if (data instanceof CustomTextureData.ResourceData r) {
				int game = gpu.gameTextureByPath(r.getNamespace() + ":" + r.getLocation());
				if (game == 0) {
					Log.warn("shader pack texture " + name + ": the game has no " + r.getLocation());
					return null;
				}
				return new TexRef(null, game, false);
			}
			if (data instanceof CustomTextureData.PngData png) {
				BufferedImage img = ImageIO.read(new ByteArrayInputStream(png.getContent()));
				if (img == null) {
					Log.warn("shader pack texture " + name + ": not a readable image");
					return null;
				}
				int w = img.getWidth();
				int h = img.getHeight();
				ByteBuffer b = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int argb = img.getRGB(x, y);
						b.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
					}
				}
				b.flip();
				GpuTexture t = gpu.createTexture(TextureSpec.of2d(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, w, h),
						"altshaders texture " + name);
				t.upload(0, 0, 0, 0, w, h, 1, b);
				filtering(t, png.getFilteringData());
				owned.add(t);
				return new TexRef(t, 0, false);
			}
			if (data instanceof CustomTextureData.RawData raw) {
				int target;
				int w;
				int h = 1;
				int d = 1;
				if (raw instanceof CustomTextureData.RawData1D r1) {
					target = GL_TEXTURE_1D;
					w = r1.getSizeX();
				} else if (raw instanceof CustomTextureData.RawData3D r3) {
					target = GL_TEXTURE_3D;
					w = r3.getSizeX();
					h = r3.getSizeY();
					d = r3.getSizeZ();
				} else if (raw instanceof CustomTextureData.RawDataRect rr) {
					target = GL_TEXTURE_RECTANGLE;
					w = rr.getSizeX();
					h = rr.getSizeY();
				} else {
					CustomTextureData.RawData2D r2 = (CustomTextureData.RawData2D) raw;
					target = GL_TEXTURE_2D;
					w = r2.getSizeX();
					h = r2.getSizeY();
				}
				GpuTexture t = gpu.createTexture(new TextureSpec(target, raw.getInternalFormat().getGlFormat(),
						raw.getPixelFormat().getGlFormat(), raw.getPixelType().getGlFormat(), w, h, d, 1),
						"altshaders texture " + name);
				byte[] content = raw.getContent();
				ByteBuffer b = ByteBuffer.allocateDirect(content.length);
				b.put(content).flip();
				t.upload(0, 0, 0, 0, w, h, d, b);
				filtering(t, raw.getFilteringData());
				owned.add(t);
				return new TexRef(t, 0, false);
			}
		} catch (IOException | RuntimeException e) {
			Log.warn("shader pack texture " + name + " couldn't be loaded: " + e);
		}
		return null;
	}

	private static void filtering(GpuTexture t, TextureFilteringData f) {
		boolean blur = f != null && f.shouldBlur();
		boolean clamp = f != null && f.shouldClamp();
		t.sampling(blur ? GL_LINEAR : GL_NEAREST, blur ? GL_LINEAR : GL_NEAREST, clamp ? GL_CLAMP_TO_EDGE : GL_REPEAT);
	}

	/** The texture a sampler named {@code name} reads in {@code stage}, if the pack gives one. */
	TexRef sampler(TextureStage stage, String name) {
		Map<String, TexRef> m = staged.get(stage);
		TexRef t = m != null ? m.get(name) : null;
		if (t == null) {
			t = named.get(name);
		}
		if (t == null) {
			Image image = imagesBySampler.get(name);
			if (image != null) {
				t = new TexRef(image.texture(), 0, false);
			}
		}
		return t;
	}

	TexRef noise() {
		return noise;
	}

	Image image(String name) {
		return imagesByName.get(name);
	}

	/** The images the pack clears every frame (Iris: at the frame's start). */
	void clearImages() {
		for (Image i : images) {
			if (i.info().clear()) {
				gpu.clearTexture(i.texture());
			}
		}
	}

	void delete() {
		for (GpuTexture t : owned) {
			t.delete();
		}
		owned.clear();
	}
}
