/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramSource.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.programs;

import com.coredex.altshaders.pack.gl.BlendModeOverride;
import com.coredex.altshaders.pack.properties.PackRenderTargetDirectives;
import com.coredex.altshaders.pack.properties.ProgramDirectives;
import com.coredex.altshaders.pack.properties.ShaderProperties;

import java.util.Optional;

public class ProgramSource {
	private final String name;
	private final String vertexSource;
	private final String geometrySource;
	private final String tessControlSource;
	private final String tessEvalSource;
	private final String fragmentSource;
	private final ProgramDirectives directives;
	private final ProgramSet parent;

	private ProgramSource(String name, String vertexSource, String geometrySource, String tessControlSource, String tessEvalSource, String fragmentSource,
						  ProgramDirectives directives, ProgramSet parent) {
		this.name = name;
		this.vertexSource = vertexSource;
		this.geometrySource = geometrySource;
		this.tessControlSource = tessControlSource;
		this.tessEvalSource = tessEvalSource;
		this.fragmentSource = fragmentSource;
		this.directives = directives;
		this.parent = parent;
	}

	public ProgramSource(String name, String vertexSource, String geometrySource, String tessControlSource, String tessEvalSource, String fragmentSource,
						 ProgramSet parent, ShaderProperties properties, BlendModeOverride defaultBlendModeOverride) {
		this.name = name;
		this.vertexSource = vertexSource;
		this.geometrySource = geometrySource;
		this.tessControlSource = tessControlSource;
		this.tessEvalSource = tessEvalSource;
		this.fragmentSource = fragmentSource;
		this.parent = parent;
		this.directives = new ProgramDirectives(this, properties,
			PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, defaultBlendModeOverride);
	}

	public ProgramSource withDirectiveOverride(ProgramDirectives overrideDirectives) {
		return new ProgramSource(name, vertexSource, geometrySource, tessControlSource, tessEvalSource, fragmentSource, overrideDirectives, parent);
	}

	public String getName() {
		return name;
	}

	public Optional<String> getVertexSource() {
		return Optional.ofNullable(vertexSource);
	}

	public Optional<String> getGeometrySource() {
		return Optional.ofNullable(geometrySource);
	}

	public Optional<String> getTessControlSource() {
		return Optional.ofNullable(tessControlSource);
	}

	public Optional<String> getTessEvalSource() {
		return Optional.ofNullable(tessEvalSource);
	}

	public Optional<String> getFragmentSource() {
		return Optional.ofNullable(fragmentSource);
	}

	public ProgramDirectives getDirectives() {
		return this.directives;
	}

	public ProgramSet getParent() {
		return parent;
	}

	public boolean isValid() {
		return vertexSource != null && fragmentSource != null;
	}

	public Optional<ProgramSource> requireValid() {
		if (this.isValid()) {
			return Optional.of(this);
		} else {
			return Optional.empty();
		}
	}
}
