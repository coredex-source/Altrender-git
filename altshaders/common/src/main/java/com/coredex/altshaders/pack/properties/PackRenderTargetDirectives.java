/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/properties/PackRenderTargetDirectives.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.properties;

import com.coredex.altshaders.pack.PackEnv;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.coredex.altshaders.pack.helpers.ListBuilder;
import java.util.Set;
import com.coredex.altshaders.pack.gl.InternalTextureFormat;
import com.coredex.altshaders.pack.parsing.DirectiveHolder;
import com.coredex.altshaders.pack.helpers.Vector4f;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PackRenderTargetDirectives {
	/** colortex0..colortex31, as Iris (its IrisLimits.MAX_COLOR_BUFFERS). */
	public static final int MAX_COLOR_BUFFERS = 32;

	public static final List<String> LEGACY_RENDER_TARGETS = List.of(
		"gcolor",
		"gdepth",
		"gnormal",
		"composite",
		"gaux1",
		"gaux2",
		"gaux3",
		"gaux4"
	);

	// TODO: Instead of just passing this, the shader pack loader should try to figure out what color buffers are in
	//       use.
	public static final Set<Integer> BASELINE_SUPPORTED_RENDER_TARGETS;

	static {
		Set<Integer> targets = new java.util.LinkedHashSet<>();

		for (int i = 0; i < MAX_COLOR_BUFFERS; i++) {
			targets.add(i);
		}

		BASELINE_SUPPORTED_RENDER_TARGETS = java.util.Collections.unmodifiableSet(targets);
	}

	private final Map<Integer, RenderTargetSettings> renderTargetSettings;

	PackRenderTargetDirectives(Set<Integer> supportedRenderTargets) {
		this.renderTargetSettings = new HashMap<>();

		supportedRenderTargets.forEach(
			(index) -> renderTargetSettings.put(index.intValue(), new RenderTargetSettings()));
	}

	public List<Integer> getBuffersToBeCleared() {
		List<Integer> buffersToBeCleared = new ArrayList<Integer>();

		renderTargetSettings.forEach((index, settings) -> {
			if (settings.shouldClear()) {
				buffersToBeCleared.add(index);
			}
		});

		return buffersToBeCleared;
	}

	public Map<Integer, RenderTargetSettings> getRenderTargetSettings() {
		return Collections.unmodifiableMap(renderTargetSettings);
	}

	public void acceptDirectives(DirectiveHolder directives) {
		Optional.ofNullable(renderTargetSettings.get(7)).ifPresent(colortex7 -> {
			// Handle legacy GAUX4FORMAT directives

			directives.acceptCommentStringDirective("GAUX4FORMAT", format -> {
				switch (format) {
					case "RGBA32F" -> colortex7.requestedFormat = InternalTextureFormat.RGBA32F;
					case "RGB32F" -> colortex7.requestedFormat = InternalTextureFormat.RGB32F;
					case "RGB16" -> colortex7.requestedFormat = InternalTextureFormat.RGB16;
					case null, default ->
						PackEnv.LOG.warn("Ignoring GAUX4FORMAT directive /* GAUX4FORMAT:" + format + "*/ because " + format
							+ " must be RGBA32F, RGB32F, or RGB16. Use `const int colortex7Format = " + format + ";` + instead.");
				}
			});
		});

		// If a shaderpack declares a gdepth uniform (even if it is not actually sampled or even of the correct type),
		// we upgrade the format of gdepth / colortex1 to RGBA32F if it is currently RGBA.
		Optional.ofNullable(renderTargetSettings.get(1)).ifPresent(gdepth -> directives.acceptUniformDirective("gdepth", () -> {
			if (gdepth.requestedFormat == InternalTextureFormat.RGBA) {
				gdepth.requestedFormat = InternalTextureFormat.RGBA32F;
			}
		}));

		renderTargetSettings.forEach((index, settings) -> {
			acceptBufferDirectives(directives, settings, "colortex" + index);

			if (index < LEGACY_RENDER_TARGETS.size()) {
				acceptBufferDirectives(directives, settings, LEGACY_RENDER_TARGETS.get(index));
			}
		});
	}

	private void acceptBufferDirectives(DirectiveHolder directives, RenderTargetSettings settings, String bufferName) {
		directives.acceptConstStringDirective(bufferName + "Format", format -> {
			Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);

			if (internalFormat.isPresent()) {
				settings.requestedFormat = internalFormat.get();
			} else {
				PackEnv.LOG.warn("Unrecognized internal texture format " + format + " specified for " + bufferName + "Format, ignoring.");
			}
		});

		// TODO: Only for composite and deferred
		directives.acceptConstBooleanDirective(bufferName + "Clear",
			shouldClear -> settings.clear = shouldClear);

		// TODO: Only for composite, deferred, and final

		// Note: This is still relevant even if shouldClear is false,
		// since this will be the initial color of the buffer.
		directives.acceptConstVec4Directive(bufferName + "ClearColor",
			clearColor -> settings.clearColor = clearColor);
	}

	public static final class RenderTargetSettings {
		private InternalTextureFormat requestedFormat;
		private boolean clear;
		private Vector4f clearColor;

		public RenderTargetSettings() {
			this.requestedFormat = InternalTextureFormat.RGBA;
			this.clear = true;
			this.clearColor = null;
		}

		public InternalTextureFormat getInternalFormat() {
			return requestedFormat;
		}

		public boolean shouldClear() {
			return clear;
		}

		public Optional<Vector4f> getClearColor() {
			return Optional.ofNullable(clearColor);
		}

		@Override
		public String toString() {
			return "RenderTargetSettings{" +
				"requestedFormat=" + requestedFormat +
				", clear=" + clear +
				", clearColor=" + clearColor +
				'}';
		}
	}
}
