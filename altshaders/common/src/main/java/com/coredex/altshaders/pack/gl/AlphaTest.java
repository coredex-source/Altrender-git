/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/gl/blending/AlphaTest.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.gl;

public record AlphaTest(AlphaTestFunction function, float reference) {
	public static final AlphaTest ALWAYS = new AlphaTest(AlphaTestFunction.ALWAYS, 0.0f);

	// WARNING: adding new fields requires updating hashCode and equals methods!

	public String toExpression(String indentation) {
		return toExpression("gl_FragData[0].a", "iris_currentAlphaTest", indentation);
	}

	public String toExpression(String alphaAccessor, String alphaThreshold, String indentation) {
		String expr = function.getExpression();

		if (function == AlphaTestFunction.ALWAYS) {
			return "// alpha test disabled\n";
		} else if (this.reference == Float.MAX_VALUE) {
			return indentation + "if (!(" + alphaAccessor + " > iris_vertexColorAlpha)) {\n" +
				indentation + "    discard;\n" +
				indentation + "}\n";
		} else if (function == AlphaTestFunction.NEVER) {
			return "discard;\n";
		}

		return indentation + "if (!(" + alphaAccessor + " " + expr + " " + alphaThreshold + ")) {\n" +
			indentation + "    discard;\n" +
			indentation + "}\n";
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AlphaTest other = (AlphaTest) obj;
		if (function != other.function)
			return false;
		return Float.floatToIntBits(reference) == Float.floatToIntBits(other.reference);
	}
}
