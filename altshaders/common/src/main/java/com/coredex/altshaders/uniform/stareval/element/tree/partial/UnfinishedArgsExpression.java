/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/element/tree/partial/UnfinishedArgsExpression.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.element.tree.partial;

import com.coredex.altshaders.uniform.stareval.element.ExpressionElement;

import java.util.ArrayList;
import java.util.List;

public class UnfinishedArgsExpression extends PartialExpression {
	public final List<ExpressionElement> tokens = new ArrayList<>();

	@Override
	public String toString() {
		return "UnfinishedArgs{" + this.tokens + "}";
	}
}
