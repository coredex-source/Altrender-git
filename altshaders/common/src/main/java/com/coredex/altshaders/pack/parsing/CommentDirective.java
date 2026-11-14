/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/parsing/CommentDirective.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.parsing;

public class CommentDirective {
	private final Type type;
	private final String directive;
	private final int location;

	CommentDirective(Type type, String directive, int location) {
		this.type = type;
		this.directive = directive;
		this.location = location;
	}

	public Type getType() {
		return type;
	}

	/**
	 * @return The directive without {@literal /}* or *{@literal /}
	 */
	public String getDirective() {
		return directive;
	}

	/**
	 * @return The starting position of the directive in a multi-line string. <br>
	 * This is necessary to check if either the drawbuffer or the rendertarget directive should be applied
	 * when there are multiple in the same shader file, based on which one is defined last.
	 */
	public int getLocation() {
		return location;
	}

	public enum Type {
		DRAWBUFFERS,
		RENDERTARGETS
	}
}
