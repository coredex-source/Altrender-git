/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.IndexType;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.strategy.ShaderStage;
import com.coredex.altrender.strategy.Strategies;
import com.coredex.altrender.strategy.StreamBuffer;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

/**
 * Layer 0's draw commands written on the GPU (development.md 4.3; gl45 with {@code ARB_indirect_parameters}): a
 * table of every section (position, index count, base vertex) lives on the GPU; each pass a compute shader appends
 * a command and its per-draw data for each section the pass's set selects, and the draw takes its count from the
 * GPU. The set is a bitset over table slots: for the camera, the visibility walk's result (which already includes the
 * frustum, so the GPU draws exactly what the CPU path would); another camera (a shader pack's shadow pass, Phase 6/7)
 * passes its own. Commands come out in slot order (deterministic, but not the CPU path's region order: only for
 * depth-tested opaque geometry).
 * <p>
 * Per-draw data is computed exactly as the CPU path computes it (float region base minus the camera's float
 * position), so the picture is the same bit for bit. Render thread only.
 */
final class GpuCuller {

	/** Ints per table slot: position (x, y, z, 0), draw (index count, base vertex, solid index count, 0). */
	private static final int SLOT_INTS = 8;
	/** Output sets used in turn, so a pass never overwrites commands an earlier pass of the frame still reads. */
	private static final int SETS = 4;
	private static final int COMMAND_BYTES = 20;
	private static final int CULL_BLOCK_BYTES = 32;
	private static final int ALIGN = 256;

	static final String COMPUTE = """
			#version 430 core
			// One workgroup compacts the selected slots in slot order (a prefix sum, no atomics): the same commands in
			// the same order every frame.
			layout(local_size_x = 1024) in;
			// per slot: position (x, y, z, 0), draw (index count, base vertex, solid index count, 0)
			layout(std430, binding = 0) readonly buffer Sections { ivec4 sections[]; };
			layout(std430, binding = 1) readonly buffer Selected { uint selected[]; };
			// DrawElementsIndirectCommand: count, instances, first index, base vertex, base instance
			layout(std430, binding = 2) writeonly buffer Commands { uint commands[]; };
			// a_RegionOffset, a_RegionInner
			layout(std430, binding = 3) writeonly buffer PerDraw { float perDraw[]; };
			layout(std430, binding = 4) writeonly buffer Count { uint drawCount[]; };
			layout(std140, binding = 1) uniform Cull {
				// the camera as the game's floats: (float) x, (float) y, (float) z
				vec4 u_Camera;
				// slots, output set, part (0 all, 1 solid quads, 2 cut-out quads)
				ivec4 u_Params;
			};

			shared uint sums[1024];

			// The part's index count and base vertex (the solid quads come first; 6 indices, 4 vertices a quad).
			ivec2 part(ivec4 d) {
				if (u_Params.z == 1) {
					return ivec2(d.z, d.y);
				}
				if (u_Params.z == 2) {
					return ivec2(d.x - d.z, d.y + d.z / 6 * 4);
				}
				return d.xy;
			}

			bool wanted(uint slot) {
				return (selected[slot >> 5u] & (1u << (slot & 31u))) != 0u && part(sections[slot * 2u + 1u]).x != 0;
			}

			void main() {
				uint t = gl_LocalInvocationID.x;
				uint slots = uint(u_Params.x);
				uint per = (slots + 1023u) / 1024u;
				uint begin = min(t * per, slots);
				uint end = min(begin + per, slots);
				uint n = 0u;
				for (uint s = begin; s < end; s++) {
					if (wanted(s)) {
						n++;
					}
				}
				sums[t] = n;
				memoryBarrierShared();
				barrier();
				for (uint off = 1u; off < 1024u; off <<= 1u) {
					uint v = t >= off ? sums[t - off] : 0u;
					memoryBarrierShared();
					barrier();
					sums[t] += v;
					memoryBarrierShared();
					barrier();
				}
				uint i = sums[t] - n;
				for (uint s = begin; s < end; s++) {
					if (!wanted(s)) {
						continue;
					}
					ivec2 d = part(sections[s * 2u + 1u]);
					ivec4 p = sections[s * 2u];
					commands[i * 5u] = uint(d.x);
					commands[i * 5u + 1u] = 1u;
					commands[i * 5u + 2u] = 0u;
					commands[i * 5u + 3u] = uint(d.y);
					commands[i * 5u + 4u] = i;
					// regions of 8 x 4 x 8 sections; offsets per 1024-block base, as TerrainRenderer#regionData
					int ox = (p.x >> 3) * 128;
					int oy = (p.y >> 2) * 64;
					int oz = (p.z >> 3) * 128;
					perDraw[i * 6u] = float(ox - (ox & 1023)) - u_Camera.x;
					perDraw[i * 6u + 1u] = 0.0 - u_Camera.y;
					perDraw[i * 6u + 2u] = float(oz - (oz & 1023)) - u_Camera.z;
					perDraw[i * 6u + 3u] = float(ox & 1023);
					perDraw[i * 6u + 4u] = float(oy);
					perDraw[i * 6u + 5u] = float(oz & 1023);
					i++;
				}
				if (t == 1023u) {
					drawCount[u_Params.y] = sums[1023];
				}
			}
			""";

	private final Strategies gl;
	private final int program;
	/** CPU copy of the table; {@code dirtyFrom..dirtyTo} slots differ from the GPU's. */
	private int[] table = new int[0];
	private int tableBuffer;
	private int tableCapacity;
	private int dirtyFrom = Integer.MAX_VALUE;
	private int dirtyTo = -1;
	/** Slots in use: [0, slots); freed ones are reused. */
	private int slots;
	private int[] freeSlots = new int[64];
	private int freeCount;
	private final int[] commandBuffers = new int[SETS];
	private final int[] perDrawBuffers = new int[SETS];
	private int countBuffer;
	private int outputCapacity;
	private int nextSet;
	private int[] selected = new int[0];

	GpuCuller(Strategies gl) {
		this.gl = gl;
		ProgramStrategy p = gl.program();
		ProgramStrategy.Result cs = p.compile(ShaderStage.COMPUTE, COMPUTE);
		if (!cs.ok()) {
			throw new IllegalStateException("terrain cull shader: " + cs.log());
		}
		ProgramStrategy.Result linked = p.linkCompute(cs.name());
		p.deleteShader(cs.name());
		if (!linked.ok()) {
			throw new IllegalStateException("terrain cull program: " + linked.log());
		}
		program = linked.name();
		countBuffer = gl.buffer().create(SETS * 4L, BufferUsage.COPY_DST);
		GlDebug.label(GL43C.GL_BUFFER, countBuffer, "terrain-draw-count");
	}

	/** A table slot for a new section. */
	int allocSlot() {
		int slot = freeCount > 0 ? freeSlots[--freeCount] : slots++;
		if (slot * SLOT_INTS >= table.length) {
			table = Arrays.copyOf(table, Math.max(1024 * SLOT_INTS, table.length * 2));
		}
		set(slot, 0, 0, 0, 0, 0);
		return slot;
	}

	void freeSlot(int slot) {
		set(slot, 0, 0, 0, 0, 0);
		if (freeCount == freeSlots.length) {
			freeSlots = Arrays.copyOf(freeSlots, freeCount * 2);
		}
		freeSlots[freeCount++] = slot;
	}

	/** The section's layer 0 draw ({@code indexCount} 0: nothing to draw). */
	void set(int slot, int x, int y, int z, int indexCount, int baseVertex) {
		set(slot, x, y, z, indexCount, baseVertex, 0);
	}

	/** As above, with the index count of its solid quads (they come first). */
	void set(int slot, int x, int y, int z, int indexCount, int baseVertex, int solidIndexCount) {
		int i = slot * SLOT_INTS;
		table[i] = x;
		table[i + 1] = y;
		table[i + 2] = z;
		table[i + 4] = indexCount;
		table[i + 5] = baseVertex;
		table[i + 6] = solidIndexCount;
		dirtyFrom = Math.min(dirtyFrom, slot);
		dirtyTo = Math.max(dirtyTo, slot);
	}

	/** Marks {@code slot} as selected for the next {@link #draw}. */
	void select(int slot) {
		selected[slot >> 5] |= 1 << (slot & 31);
	}

	/** Starts a selection: no slot selected. */
	void clearSelection() {
		int words = (slots + 31) >> 5;
		if (selected.length < words) {
			selected = new int[Math.max(words, selected.length * 2)];
		}
		Arrays.fill(selected, 0, words, 0);
	}

	/**
	 * Writes the selected sections' commands on the GPU and draws them: the terrain program, its frame block, the
	 * texture and the {@code LAYOUT_INDIRECT} vertex array with {@code vertexBuffer} and {@code indexBuffer} are set
	 * by the caller (the vertex array's instance binding is set here). Camera: the game's float position.
	 */
	void draw(int vertexBuffer, int indexBuffer, float camX, float camY, float camZ,
			com.coredex.altrender.strategy.VertexLayout layoutIndirect) {
		draw(vertexBuffer, indexBuffer, camX, camY, camZ, layoutIndirect, 0);
	}

	/** As above, for a part of each section's quads: 0 all, 1 the solid ones, 2 the cut-out ones. */
	void draw(int vertexBuffer, int indexBuffer, float camX, float camY, float camZ,
			com.coredex.altrender.strategy.VertexLayout layoutIndirect, int part) {
		if (slots == 0) {
			return;
		}
		flushTable();
		ensureOutputs(slots);
		StreamBuffer stream = gl.stream();
		int words = (slots + 31) >> 5;
		long selectedAt = stream.alloc(words * 4L, ALIGN);
		long blockAt = stream.alloc(CULL_BLOCK_BYTES, ALIGN);
		ByteBuffer m = stream.memory();
		for (int i = 0; i < words; i++) {
			m.putInt((int) selectedAt + i * 4, selected[i]);
		}
		stream.commit(selectedAt, words * 4L);
		int set = nextSet;
		nextSet = (nextSet + 1) % SETS;
		m.putFloat((int) blockAt, camX).putFloat((int) blockAt + 4, camY).putFloat((int) blockAt + 8, camZ)
				.putFloat((int) blockAt + 12, 0f).putInt((int) blockAt + 16, slots).putInt((int) blockAt + 20, set)
				.putInt((int) blockAt + 24, part).putInt((int) blockAt + 28, 0);
		stream.commit(blockAt, CULL_BLOCK_BYTES);

		int previousProgram = gl.state().program();
		gl.state().useProgram(program);
		GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, 0, tableBuffer, 0, (long) slots * SLOT_INTS * 4);
		GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, 1, stream.buffer(), selectedAt, words * 4L);
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, commandBuffers[set]);
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 3, perDrawBuffers[set]);
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 4, countBuffer);
		gl.uniform().bindBlock(1, stream.buffer(), blockAt, CULL_BLOCK_BYTES);
		GL43C.glDispatchCompute(1, 1, 1);
		GL42C.glMemoryBarrier(GL43C.GL_COMMAND_BARRIER_BIT | GL42C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
		gl.state().useProgram(previousProgram);

		gl.vertexInput().bind(layoutIndirect, vertexBuffer, indexBuffer);
		gl.vertexInput().bindInstances(layoutIndirect, perDrawBuffers[set], 0);
		gl.vertexInput().multiDrawElementsIndirectCount(Primitive.TRIANGLES, IndexType.U32, commandBuffers[set], 0,
				countBuffer, set * 4L, slots);
	}

	/** Every section is gone (world change): all slots free. */
	void reset() {
		slots = 0;
		freeCount = 0;
		dirtyFrom = Integer.MAX_VALUE;
		dirtyTo = -1;
	}

	void delete() {
		gl.program().deleteProgram(program);
		if (tableBuffer != 0) {
			gl.buffer().delete(tableBuffer);
		}
		for (int i = 0; i < SETS; i++) {
			if (commandBuffers[i] != 0) {
				gl.buffer().delete(commandBuffers[i]);
				gl.buffer().delete(perDrawBuffers[i]);
			}
		}
		gl.buffer().delete(countBuffer);
	}

	private void flushTable() {
		if (tableCapacity < slots) {
			int capacity = Math.max(slots, Math.max(1024, tableCapacity * 2));
			if (tableBuffer != 0) {
				gl.buffer().delete(tableBuffer);
			}
			tableBuffer = gl.buffer().create((long) capacity * SLOT_INTS * 4, BufferUsage.COPY_DST);
			GlDebug.label(GL43C.GL_BUFFER, tableBuffer, "terrain-sections");
			tableCapacity = capacity;
			dirtyFrom = 0;
			dirtyTo = slots - 1;
		}
		if (dirtyTo < dirtyFrom) {
			return;
		}
		int from = dirtyFrom * SLOT_INTS;
		int ints = (Math.min(dirtyTo, slots - 1) - dirtyFrom + 1) * SLOT_INTS;
		ByteBuffer data = MemoryUtil.memAlloc(ints * 4);
		data.asIntBuffer().put(table, from, ints);
		gl.buffer().write(tableBuffer, from * 4L, data);
		MemoryUtil.memFree(data);
		dirtyFrom = Integer.MAX_VALUE;
		dirtyTo = -1;
	}

	private void ensureOutputs(int draws) {
		if (outputCapacity >= draws) {
			return;
		}
		int capacity = Math.max(draws, Math.max(1024, outputCapacity * 2));
		for (int i = 0; i < SETS; i++) {
			if (commandBuffers[i] != 0) {
				gl.buffer().delete(commandBuffers[i]);
				gl.buffer().delete(perDrawBuffers[i]);
			}
			commandBuffers[i] = gl.buffer().create((long) capacity * COMMAND_BYTES, BufferUsage.VERTEX);
			perDrawBuffers[i] = gl.buffer().create((long) capacity * TerrainVertex.DRAW_STRIDE, BufferUsage.VERTEX);
			GlDebug.label(GL43C.GL_BUFFER, commandBuffers[i], "terrain-commands-" + i);
			GlDebug.label(GL43C.GL_BUFFER, perDrawBuffers[i], "terrain-per-draw-" + i);
		}
		outputCapacity = capacity;
	}
}
