/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.IndexType;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.VertexInputStrategy;
import com.coredex.altrender.strategy.VertexLayout;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * GL 3.3 vertex input: one VAO per vertex layout. Attribute pointers capture the vertex buffer, so they're re-specified
 * whenever a layout's VAO is used with a different vertex buffer; the index buffer binding lives in the VAO too.
 * Draws use base-vertex entry points (core since 3.2).
 */
public final class Gl33VertexInputStrategy implements VertexInputStrategy {
	private static final class Vao {
		final int name;
		int vertexBuffer = -1;
		int indexBuffer = -1;

		Vao(int name) {
			this.name = name;
		}
	}

	private final GlState state;
	private final GlObjectCounts counts;
	private final BiConsumer<Integer, VertexLayout> labeler;
	private final Map<VertexLayout, Vao> vaos = new IdentityHashMap<>();

	/** @param labeler called once per new VAO (debug labels) */
	public Gl33VertexInputStrategy(GlState state, GlObjectCounts counts, BiConsumer<Integer, VertexLayout> labeler) {
		this.state = state;
		this.counts = counts;
		this.labeler = labeler;
	}

	@Override
	public String id() {
		return "gl33-vao";
	}

	@Override
	public void bind(VertexLayout layout, int vertexBuffer, int indexBuffer) {
		Vao vao = vaos.get(layout);
		if (vao == null) {
			vao = new Vao(GL30C.glGenVertexArrays());
			counts.created(GlObjectCounts.Kind.VERTEX_ARRAY);
			state.bindVertexArray(vao.name);
			for (VertexLayout.Attribute a : layout.attributes()) {
				GL20C.glEnableVertexAttribArray(a.location());
			}
			labeler.accept(vao.name, layout);
			vaos.put(layout, vao);
		}
		state.bindVertexArray(vao.name);
		if (vao.vertexBuffer != vertexBuffer) {
			// Through the state cache: GL_ARRAY_BUFFER is global, and the game's client arrays need 0 back after the
			// pass (a buffer left bound turns their pointers into offsets into it).
			state.bindArrayBuffer(vertexBuffer);
			for (VertexLayout.Attribute a : layout.attributes()) {
				if (a.integer()) {
					GL30C.glVertexAttribIPointer(a.location(), a.components(), glType(a.type()), layout.stride(), a.offset());
				} else {
					GL20C.glVertexAttribPointer(a.location(), a.components(), glType(a.type()), a.normalized(),
							layout.stride(), a.offset());
				}
			}
			vao.vertexBuffer = vertexBuffer;
		}
		if (vao.indexBuffer != indexBuffer) {
			GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
			vao.indexBuffer = indexBuffer;
		}
	}

	@Override
	public void drawArrays(Primitive primitive, int first, int count) {
		GL11C.glDrawArrays(glPrimitive(primitive), first, count);
	}

	@Override
	public void drawElements(Primitive primitive, int count, IndexType type, long byteOffset, int baseVertex, int instances) {
		int mode = glPrimitive(primitive);
		int glType = type == IndexType.U16 ? GL11C.GL_UNSIGNED_SHORT : GL11C.GL_UNSIGNED_INT;
		if (instances > 1) {
			GL32C.glDrawElementsInstancedBaseVertex(mode, count, glType, byteOffset, instances, baseVertex);
		} else {
			GL32C.glDrawElementsBaseVertex(mode, count, glType, byteOffset, baseVertex);
		}
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
		}
	}

	@Override
	public void clear() {
		for (Vao vao : vaos.values()) {
			GL30C.glDeleteVertexArrays(vao.name);
			state.forgetVertexArray(vao.name);
			counts.deleted(GlObjectCounts.Kind.VERTEX_ARRAY);
		}
		vaos.clear();
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
