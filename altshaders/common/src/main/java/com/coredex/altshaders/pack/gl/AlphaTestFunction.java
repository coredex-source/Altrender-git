/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/blending/AlphaTestFunction.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.gl;

import org.lwjgl.opengl.GL11;

import java.util.Optional;

public enum AlphaTestFunction {
	NEVER(GL11.GL_NEVER, null),
	LESS(GL11.GL_LESS, "<"),
	EQUAL(GL11.GL_EQUAL, "=="),
	LEQUAL(GL11.GL_LEQUAL, "<="),
	GREATER(GL11.GL_GREATER, ">"),
	NOTEQUAL(GL11.GL_NOTEQUAL, "!="),
	GEQUAL(GL11.GL_GEQUAL, ">="),
	ALWAYS(GL11.GL_ALWAYS, null);

	private final int glId;
	private final String expression;

	AlphaTestFunction(int glFormat, String expression) {
		this.glId = glFormat;
		this.expression = expression;
	}

	public static Optional<AlphaTestFunction> fromGlId(int glId) {
		return switch (glId) {
			case GL11.GL_NEVER -> Optional.of(NEVER);
			case GL11.GL_LESS -> Optional.of(LESS);
			case GL11.GL_EQUAL -> Optional.of(EQUAL);
			case GL11.GL_LEQUAL -> Optional.of(LEQUAL);
			case GL11.GL_GREATER -> Optional.of(GREATER);
			case GL11.GL_NOTEQUAL -> Optional.of(NOTEQUAL);
			case GL11.GL_GEQUAL -> Optional.of(GEQUAL);
			case GL11.GL_ALWAYS -> Optional.of(ALWAYS);
			default -> Optional.empty();
		};
	}

	public static Optional<AlphaTestFunction> fromString(String name) {
		if ("GL_ALWAYS".equals(name)) {
			// shaders.properties states that GL_ALWAYS is the name to use, but I haven't verified that this actually
			// matches the implementation... All of the other names do not have the GL_ prefix.
			//
			// We'll support it here just to be safe, even though just a plain ALWAYS seems more likely to be what it
			// parses.
			return Optional.of(AlphaTestFunction.ALWAYS);
		}

		try {
			return Optional.of(AlphaTestFunction.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlId() {
		return glId;
	}

	public String getExpression() {
		return expression;
	}
}
