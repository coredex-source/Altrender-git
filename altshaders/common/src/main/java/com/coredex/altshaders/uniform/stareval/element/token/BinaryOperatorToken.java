/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/element/token/BinaryOperatorToken.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.element.token;

import com.coredex.altshaders.uniform.stareval.parser.BinaryOp;

public class BinaryOperatorToken extends Token {
	public final BinaryOp op;

	public BinaryOperatorToken(BinaryOp op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return "BinaryOp{" + this.op + "}";
	}
}
