/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/error/RusticError.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.error;


public record RusticError(String severity, String message, String detailMessage, String file, int lineNumber,
						  String badLine) {


	@Override
	public String toString() {
		return severity + ": " + message + "\n"
			+ " --> " + file + ":" + lineNumber + "\n"
			+ "  |\n"
			+ "  | " + badLine + "\n"
			+ "  | " + "^".repeat(badLine.length()) + " " + detailMessage + "\n"
			+ "  |";
	}
}
