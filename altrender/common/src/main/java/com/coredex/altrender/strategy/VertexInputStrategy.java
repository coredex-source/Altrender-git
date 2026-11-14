/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * Vertex input and draw submission per tier: a VAO per vertex layout (gl33),
 * {@code vertex_attrib_binding} with per-instance buffers, base instance, multi-draw and indirect draws (gl45). The
 * defaults loop over single draws or refuse; only gl45 implements multi-draw and indirect draws. Render thread
 * only.
 */
public interface VertexInputStrategy {
	String id();

	/** Makes {@code layout} read from {@code vertexBuffer} (0 = none) with indices from {@code indexBuffer} (0 = none). */
	void bind(VertexLayout layout, int vertexBuffer, int indexBuffer);

	void drawArrays(Primitive primitive, int first, int count);

	/**
	 * @param byteOffset offset of the first index in the index buffer
	 * @param baseVertex added to every index
	 */
	void drawElements(Primitive primitive, int count, IndexType type, long byteOffset, int baseVertex, int instances);

	/**
	 * The per-instance binding of {@code layout} ({@link VertexLayout#hasInstanceBinding()}, multi-draw terrain)
	 * reads from {@code buffer} at {@code byteOffset}. Call after {@link #bind}. Only tiers that declare multi-draw.
	 */
	default void bindInstances(VertexLayout layout, int buffer, long byteOffset) {
		throw new UnsupportedOperationException(id() + " has no per-instance vertex buffers");
	}

	/** {@link #drawElements} starting at instance {@code firstInstance} (base instance, gl45). */
	default void drawElements(Primitive primitive, int count, IndexType type, long byteOffset, int baseVertex, int instances,
							  int firstInstance) {
		if (firstInstance != 0) {
			throw new UnsupportedOperationException(id() + " can't draw from a first instance (" + firstInstance + ")");
		}
		drawElements(primitive, count, type, byteOffset, baseVertex, instances);
	}

	/** Non-indexed draw with instances and a first instance (gl45). */
	default void drawArrays(Primitive primitive, int first, int count, int instances, int firstInstance) {
		if (instances != 1 || firstInstance != 0) {
			throw new UnsupportedOperationException(id() + " can't draw " + instances + " instances from " + firstInstance);
		}
		drawArrays(primitive, first, count);
	}

	/** Several indexed draws: byte offsets into the index buffer, counts and base vertices per draw. */
	default void multiDrawElements(Primitive primitive, IndexType type, org.lwjgl.PointerBuffer byteOffsets,
								   java.nio.IntBuffer counts, java.nio.IntBuffer baseVertices, int drawCount) {
		for (int i = 0; i < drawCount; i++) {
			drawElements(primitive, counts.get(counts.position() + i), type, byteOffsets.get(byteOffsets.position() + i),
					baseVertices.get(baseVertices.position() + i), 1);
		}
	}

	/** Several non-indexed draws: first vertex and count per draw. */
	default void multiDrawArrays(Primitive primitive, java.nio.IntBuffer firsts, java.nio.IntBuffer counts, int drawCount) {
		for (int i = 0; i < drawCount; i++) {
			drawArrays(primitive, firsts.get(firsts.position() + i), counts.get(counts.position() + i));
		}
	}

	/**
	 * {@code drawCount} indexed draws whose parameters the GPU reads from {@code buffer} at {@code byteOffset}, five
	 * 32-bit values each (count, instances, first index, base vertex, first instance), tightly packed (gl45).
	 */
	default void multiDrawElementsIndirect(Primitive primitive, IndexType type, int buffer, long byteOffset, int drawCount) {
		throw new UnsupportedOperationException(id() + " has no indirect draws");
	}

	/** As {@link #multiDrawElementsIndirect} without indices: four values each (count, instances, first, first instance). */
	default void multiDrawArraysIndirect(Primitive primitive, int buffer, long byteOffset, int drawCount) {
		throw new UnsupportedOperationException(id() + " has no indirect draws");
	}

	/**
	 * As {@link #multiDrawElementsIndirect}, with the draw count read by the GPU from {@code countBuffer} at
	 * {@code countOffset} (one 32-bit value), at most {@code maxDraws} (gl45 with {@code ARB_indirect_parameters}).
	 */
	default void multiDrawElementsIndirectCount(Primitive primitive, IndexType type, int buffer, long byteOffset,
			int countBuffer, long countOffset, int maxDraws) {
		throw new UnsupportedOperationException(id() + " has no indirect count draws");
	}

	/**
	 * The value a vertex shader reads from attribute {@code location} while the bound vertex array has no array
	 * enabled there (per-draw constants between draws). Keep to locations no driver aliases to fixed-function state.
	 */
	default void constantAttribute(int location, float x, float y, float z) {
		org.lwjgl.opengl.GL20C.glVertexAttrib3f(location, x, y, z);
	}

	/** A buffer was deleted: forget it wherever a vertex array still references it. */
	void forgetBuffer(int buffer);

	/** Deletes every vertex array (device shutdown). */
	void clear();

	/** Programs were deleted (resource reload): drop what was derived per program, e.g. CPU-pulled attributes. */
	default void forgetPrograms() {}

	/** Forget cached global vertex state (something else may have changed it); called with {@code GlState.invalidate}. */
	default void invalidate() {}
}
