/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertex format in our own terms. Attribute {@code location} is the element's position in the format, which is also
 * the location the program binds the attribute's name to before linking.
 *
 * <p>A layout may have a second, per-instance binding (step rate 1: the multi-draw terrain path's chunk position
 * and visibility). Its attributes continue the location numbering after the per-vertex ones; their {@code offset} is
 * within the instance record of {@code instanceStride} bytes. Only tiers that declare multi-draw (gl45) see such
 * pipelines in use.</p>
 */
public record VertexLayout(int stride, List<Attribute> attributes, int instanceStride, List<Attribute> instanceAttributes) {
	public VertexLayout(int stride, List<Attribute> attributes) {
		this(stride, attributes, 0, List.of());
	}

	public enum AttribType {
		FLOAT, UBYTE, BYTE, USHORT, SHORT, UINT, INT,
		/** Integer data the shader reads as float values, not normalised (a shader pack's {@code vec4} attributes). */
		BYTE_AS_FLOAT, SHORT_AS_FLOAT, INT_AS_FLOAT
	}

	/**
	 * @param normalized integer data mapped to [0,1] / [-1,1] floats
	 */
	public record Attribute(int location, String name, int components, AttribType type, boolean normalized, int offset) {
		/** Non-normalized integer data reaches the shader as an integer attribute ({@code ivec}/{@code uvec}). */
		public boolean integer() {
			return type != AttribType.FLOAT && type != AttribType.BYTE_AS_FLOAT && type != AttribType.SHORT_AS_FLOAT
					&& type != AttribType.INT_AS_FLOAT && !normalized;
		}
	}

	public boolean hasInstanceBinding() {
		return !instanceAttributes.isEmpty();
	}

	/** Every attribute name in location order: per-vertex, then per-instance. */
	public List<String> names() {
		List<String> out = new ArrayList<>(attributes.size() + instanceAttributes.size());
		for (Attribute a : attributes) {
			out.add(a.name());
		}
		for (Attribute a : instanceAttributes) {
			out.add(a.name());
		}
		return out;
	}
}
