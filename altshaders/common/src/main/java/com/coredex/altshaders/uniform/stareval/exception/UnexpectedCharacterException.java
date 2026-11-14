/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/exception/UnexpectedCharacterException.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.exception;

public class UnexpectedCharacterException extends ParseException {
	public UnexpectedCharacterException(char expected, char actual, int index) {
		this("Expected to read '" + expected + "' but got '" + actual + "' at index " + index);
	}

	public UnexpectedCharacterException(char actual, int index) {
		this("Read an unexpected character '" + actual + "' at index " + index);
	}

	public UnexpectedCharacterException(String expected, char actual, int index) {
		this("Expected to read " + expected + " but got '" + actual + "' at index " + index);
	}

	private UnexpectedCharacterException(String message) {
		super(message);
	}
}
