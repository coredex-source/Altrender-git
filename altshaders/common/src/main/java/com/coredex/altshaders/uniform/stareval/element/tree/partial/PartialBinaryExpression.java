/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/element/tree/partial/PartialBinaryExpression.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.element.tree.partial;

import com.coredex.altshaders.uniform.stareval.element.ExpressionElement;
import com.coredex.altshaders.uniform.stareval.element.PriorityOperatorElement;
import com.coredex.altshaders.uniform.stareval.element.tree.BinaryExpressionElement;
import com.coredex.altshaders.uniform.stareval.parser.BinaryOp;

public class PartialBinaryExpression extends PartialExpression implements PriorityOperatorElement {
	private final ExpressionElement left;
	private final BinaryOp op;

	public PartialBinaryExpression(ExpressionElement left, BinaryOp op) {
		this.left = left;
		this.op = op;
	}

	@Override
	public String toString() {
		return "PartialBinaryExpression{ {" + this.left + "} " + this.op + "}";
	}

	@Override
	public int getPriority() {
		return this.op.priority();
	}

	@Override
	public BinaryExpressionElement resolveWith(ExpressionElement right) {
		return new BinaryExpressionElement(this.op, this.left, right);
	}
}
