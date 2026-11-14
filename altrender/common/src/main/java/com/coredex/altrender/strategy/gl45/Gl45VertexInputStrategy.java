/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl45;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.IndexType;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.VertexInputStrategy;
import com.coredex.altrender.strategy.VertexLayout;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL46C;

import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * GL 4.5 vertex input: one VAO per vertex layout, its formats set once by name
 * ({@code vertex_attrib_binding}: binding 0 per vertex, binding 1 per instance), so a draw with another vertex buffer
 * only swaps a binding ({@code glVertexArrayVertexBuffer}) instead of re-specifying every attribute pointer. Draws use
 * base vertex and base instance; multi-draws and indirect draws go to the driver in one call each.
 */
public final class Gl45VertexInputStrategy implements VertexInputStrategy {
	private static final class Vao {
		final int name;
		int vertexBuffer = -1;
		int indexBuffer = -1;
		int instanceBuffer = -1;
		long instanceOffset = -1;

		Vao(int name) {
			this.name = name;
		}
	}

	private final GlState state;
	private final GlObjectCounts counts;
	private final BiConsumer<Integer, VertexLayout> labeler;
	private final Map<VertexLayout, Vao> vaos = new IdentityHashMap<>();
	private int indirectBuffer = -1;
	private int parameterBuffer = -1;

	public Gl45VertexInputStrategy(GlState state, GlObjectCounts counts, BiConsumer<Integer, VertexLayout> labeler) {
		this.state = state;
		this.counts = counts;
		this.labeler = labeler;
	}

	@Override
	public String id() {
		return "gl45-attribbinding";
	}

	private Vao vao(VertexLayout layout) {
		Vao vao = vaos.get(layout);
		if (vao != null) {
			return vao;
		}
		vao = new Vao(GL45C.glCreateVertexArrays());
		counts.created(GlObjectCounts.Kind.VERTEX_ARRAY);
		for (VertexLayout.Attribute a : layout.attributes()) {
			format(vao.name, a, 0);
		}
		if (layout.hasInstanceBinding()) {
			for (VertexLayout.Attribute a : layout.instanceAttributes()) {
				format(vao.name, a, 1);
			}
			GL45C.glVertexArrayBindingDivisor(vao.name, 1, 1);
		}
		labeler.accept(vao.name, layout);
		vaos.put(layout, vao);
		return vao;
	}

	private static void format(int vao, VertexLayout.Attribute a, int binding) {
		GL45C.glEnableVertexArrayAttrib(vao, a.location());
		if (a.integer()) {
			GL45C.glVertexArrayAttribIFormat(vao, a.location(), a.components(), glType(a.type()), a.offset());
		} else {
			GL45C.glVertexArrayAttribFormat(vao, a.location(), a.components(), glType(a.type()), a.normalized(), a.offset());
		}
		GL45C.glVertexArrayAttribBinding(vao, a.location(), binding);
	}

	@Override
	public void bind(VertexLayout layout, int vertexBuffer, int indexBuffer) {
		Vao vao = vao(layout);
		if (vao.vertexBuffer != vertexBuffer) {
			GL45C.glVertexArrayVertexBuffer(vao.name, 0, vertexBuffer, 0, layout.stride());
			vao.vertexBuffer = vertexBuffer;
		}
		if (vao.indexBuffer != indexBuffer) {
			GL45C.glVertexArrayElementBuffer(vao.name, indexBuffer);
			vao.indexBuffer = indexBuffer;
		}
		state.bindVertexArray(vao.name);
	}

	@Override
	public void bindInstances(VertexLayout layout, int buffer, long byteOffset) {
		Vao vao = vao(layout);
		if (vao.instanceBuffer != buffer || vao.instanceOffset != byteOffset) {
			GL45C.glVertexArrayVertexBuffer(vao.name, 1, buffer, byteOffset, layout.instanceStride());
			vao.instanceBuffer = buffer;
			vao.instanceOffset = byteOffset;
		}
	}

	@Override
	public void drawArrays(Primitive primitive, int first, int count) {
		GL11C.glDrawArrays(glPrimitive(primitive), first, count);
	}

	@Override
	public void drawArrays(Primitive primitive, int first, int count, int instances, int firstInstance) {
		GL42C.glDrawArraysInstancedBaseInstance(glPrimitive(primitive), first, count, instances, firstInstance);
	}

	@Override
	public void drawElements(Primitive primitive, int count, IndexType type, long byteOffset, int baseVertex, int instances) {
		int mode = glPrimitive(primitive);
		if (instances > 1) {
			GL32C.glDrawElementsInstancedBaseVertex(mode, count, glIndex(type), byteOffset, instances, baseVertex);
		} else {
			GL32C.glDrawElementsBaseVertex(mode, count, glIndex(type), byteOffset, baseVertex);
		}
	}

	@Override
	public void drawElements(Primitive primitive, int count, IndexType type, long byteOffset, int baseVertex, int instances,
							 int firstInstance) {
		if (firstInstance == 0) {
			drawElements(primitive, count, type, byteOffset, baseVertex, instances);
			return;
		}
		GL42C.glDrawElementsInstancedBaseVertexBaseInstance(glPrimitive(primitive), count, glIndex(type), byteOffset,
				instances, baseVertex, firstInstance);
	}

	@Override
	public void multiDrawElements(Primitive primitive, IndexType type, PointerBuffer byteOffsets, IntBuffer counts,
								  IntBuffer baseVertices, int drawCount) {
		// LWJGL takes the draw count from the buffers' remaining(); the caller's views are exactly drawCount long.
		GL32C.glMultiDrawElementsBaseVertex(glPrimitive(primitive), limit(counts, drawCount), glIndex(type),
				limit(byteOffsets, drawCount), limit(baseVertices, drawCount));
	}

	@Override
	public void multiDrawArrays(Primitive primitive, IntBuffer firsts, IntBuffer counts, int drawCount) {
		GL14C.glMultiDrawArrays(glPrimitive(primitive), limit(firsts, drawCount), limit(counts, drawCount));
	}

	@Override
	public void multiDrawElementsIndirect(Primitive primitive, IndexType type, int buffer, long byteOffset, int drawCount) {
		bindIndirect(buffer);
		GL43C.glMultiDrawElementsIndirect(glPrimitive(primitive), glIndex(type), byteOffset, drawCount, 20);
	}

	@Override
	public void multiDrawArraysIndirect(Primitive primitive, int buffer, long byteOffset, int drawCount) {
		bindIndirect(buffer);
		GL43C.glMultiDrawArraysIndirect(glPrimitive(primitive), byteOffset, drawCount, 16);
	}

	@Override
	public void multiDrawElementsIndirectCount(Primitive primitive, IndexType type, int buffer, long byteOffset,
			int countBuffer, long countOffset, int maxDraws) {
		bindIndirect(buffer);
		if (parameterBuffer != countBuffer) {
			GL15C.glBindBuffer(GL46C.GL_PARAMETER_BUFFER, countBuffer);
			parameterBuffer = countBuffer;
		}
		GL46C.glMultiDrawElementsIndirectCount(glPrimitive(primitive), glIndex(type), byteOffset, countOffset, maxDraws, 20);
	}

	private void bindIndirect(int buffer) {
		if (indirectBuffer != buffer) {
			GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, buffer);
			indirectBuffer = buffer;
		}
	}

	private static IntBuffer limit(IntBuffer b, int count) {
		return org.lwjgl.system.MemoryUtil.memSlice(b, 0, count);
	}

	private static PointerBuffer limit(PointerBuffer b, int count) {
		return PointerBuffer.create(b.address() + (long) b.position() * PointerBuffer.POINTER_SIZE, count);
	}

	@Override
	public void forgetBuffer(int buffer) {
		for (Vao vao : vaos.values()) {
			if (vao.vertexBuffer == buffer) {
				vao.vertexBuffer = -1;
			}
			if (vao.indexBuffer == buffer) {
				vao.indexBuffer = -1;
			}
			if (vao.instanceBuffer == buffer) {
				vao.instanceBuffer = -1;
			}
		}
		if (indirectBuffer == buffer) {
			indirectBuffer = -1;
		}
		if (parameterBuffer == buffer) {
			parameterBuffer = -1;
		}
	}

	@Override
	public void invalidate() {
		indirectBuffer = -1;
		parameterBuffer = -1;
	}

	@Override
	public void clear() {
		for (Vao vao : vaos.values()) {
			org.lwjgl.opengl.GL30C.glDeleteVertexArrays(vao.name);
			state.forgetVertexArray(vao.name);
			counts.deleted(GlObjectCounts.Kind.VERTEX_ARRAY);
		}
		vaos.clear();
	}

	private static int glIndex(IndexType type) {
		return type == IndexType.U16 ? GL11C.GL_UNSIGNED_SHORT : GL11C.GL_UNSIGNED_INT;
	}

	private static int glType(VertexLayout.AttribType type) {
		return switch (type) {
			case FLOAT -> GL11C.GL_FLOAT;
			case UBYTE -> GL11C.GL_UNSIGNED_BYTE;
			case BYTE -> GL11C.GL_BYTE;
			case USHORT -> GL11C.GL_UNSIGNED_SHORT;
			case SHORT -> GL11C.GL_SHORT;
			case UINT -> GL11C.GL_UNSIGNED_INT;
			case INT, INT_AS_FLOAT -> GL11C.GL_INT;
			case BYTE_AS_FLOAT -> GL11C.GL_BYTE;
			case SHORT_AS_FLOAT -> GL11C.GL_SHORT;
		};
	}

	private static int glPrimitive(Primitive p) {
		return switch (p) {
			case POINTS -> GL11C.GL_POINTS;
			case LINES -> GL11C.GL_LINES;
			case LINE_STRIP -> GL11C.GL_LINE_STRIP;
			case TRIANGLES -> GL11C.GL_TRIANGLES;
			case TRIANGLE_STRIP -> GL11C.GL_TRIANGLE_STRIP;
			case TRIANGLE_FAN -> GL11C.GL_TRIANGLE_FAN;
		};
	}
}
