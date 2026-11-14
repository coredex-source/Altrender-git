/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/loading/ProgramArrayId.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.loading;

public enum ProgramArrayId {
	Setup(ProgramGroup.Setup, 100),
	Begin(ProgramGroup.Begin, 100),
	ShadowComposite(ProgramGroup.ShadowComposite, 100),
	Prepare(ProgramGroup.Prepare, 100),
	Deferred(ProgramGroup.Deferred, 100),
	Composite(ProgramGroup.Composite, 100),
	;

	private final ProgramGroup group;
	private final int numPrograms;

	ProgramArrayId(ProgramGroup group, int numPrograms) {
		this.group = group;
		this.numPrograms = numPrograms;
	}

	public ProgramGroup getGroup() {
		return group;
	}

	public String getSourcePrefix() {
		return group.getBaseName();
	}

	public int getNumPrograms() {
		return numPrograms;
	}
}
