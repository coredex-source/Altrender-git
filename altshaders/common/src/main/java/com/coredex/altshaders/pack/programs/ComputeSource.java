/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/programs/ComputeSource.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.programs;

import com.coredex.altshaders.pack.properties.IndirectPointer;
import com.coredex.altshaders.pack.properties.ShaderProperties;
import com.coredex.altshaders.pack.helpers.Vector2f;
import com.coredex.altshaders.pack.helpers.Vector3i;

import java.util.Optional;

public class ComputeSource {
	private final String name;
	private final String source;
	private final ProgramSet parent;
	private final IndirectPointer indirectPointer;
	private Vector3i workGroups;
	private Vector2f workGroupRelative;

	public ComputeSource(String name, String source, ProgramSet parent, ShaderProperties properties) {
		this.name = name;
		this.source = source;
		this.parent = parent;
		this.indirectPointer = properties.getIndirectPointers().get(name);
	}

	public String getName() {
		return name;
	}

	public Optional<String> getSource() {
		return Optional.ofNullable(source);
	}

	public ProgramSet getParent() {
		return parent;
	}

	public boolean isValid() {
		return source != null;
	}

	public Vector2f getWorkGroupRelative() {
		return workGroupRelative;
	}

	public void setWorkGroupRelative(Vector2f workGroupRelative) {
		this.workGroupRelative = workGroupRelative;
	}

	public Vector3i getWorkGroups() {
		return workGroups;
	}

	public void setWorkGroups(Vector3i workGroups) {
		this.workGroups = workGroups;
	}

	public IndirectPointer getIndirectPointer() {
		return indirectPointer;
	}

	public Optional<ComputeSource> requireValid() {
		if (this.isValid()) {
			return Optional.of(this);
		} else {
			return Optional.empty();
		}
	}
}
