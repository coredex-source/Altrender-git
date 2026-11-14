/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/texture/TextureDefinition.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.gl;

public class TextureDefinition {
	protected String name;

	public String getName() {
		return name;
	}

	public static class PNGDefinition extends TextureDefinition {
		public PNGDefinition(String name) {
			this.name = name;
		}
	}

	public static class RawDefinition extends TextureDefinition {
		private final TextureType target;
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final InternalTextureFormat internalFormat;
		private final PixelFormat format;
		private final PixelType pixelType;

		public RawDefinition(String path, TextureType target, InternalTextureFormat internalFormat, int sizeX, int sizeY, int sizeZ, PixelFormat format, PixelType pixelType) {
			this.name = path;
			this.target = target;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
			this.internalFormat = internalFormat;
			this.format = format;
			this.pixelType = pixelType;
		}

		public TextureType getTarget() {
			return target;
		}

		public int getSizeX() {
			return sizeX;
		}

		public int getSizeY() {
			return sizeY;
		}

		public int getSizeZ() {
			return sizeZ;
		}

		public PixelFormat getFormat() {
			return format;
		}

		public InternalTextureFormat getInternalFormat() {
			return internalFormat;
		}

		public PixelType getPixelType() {
			return pixelType;
		}
	}
}
