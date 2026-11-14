/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.TexFormat;
import com.coredex.altrender.strategy.UniformStrategy;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31C;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GL 3.3 uniforms: each program's uniform blocks are pointed at binding points 0..n-1 in block-index order, and the
 * game's buffer ranges are bound there per draw (through the state cache). Texel buffers are buffer textures, one per
 * (buffer, format), created on first use and deleted with the buffer.
 */
public final class Gl33UniformStrategy implements UniformStrategy {
	private record TexelKey(int buffer, TexFormat format) {}

	private final GlState state;
	private final GlObjectCounts counts;
	private final int maxBindings;
	private final Map<TexelKey, Integer> texelTextures = new HashMap<>();

	public Gl33UniformStrategy(GlState state, GlObjectCounts counts, int maxBindings) {
		this.state = state;
		this.counts = counts;
		this.maxBindings = maxBindings;
	}

	@Override
	public String id() {
		return "gl33-ubo";
	}

	@Override
	public List<String> configureProgram(int program) {
		int count = GL31C.glGetProgrami(program, GL31C.GL_ACTIVE_UNIFORM_BLOCKS);
		List<String> blocks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			blocks.add(GL31C.glGetActiveUniformBlockName(program, i));
			if (i < maxBindings) {
				GL31C.glUniformBlockBinding(program, i, i);
			}
		}
		return blocks;
	}

	@Override
	public int maxBlocks() {
		return maxBindings;
	}

	@Override
	public void bindBlock(int binding, int buffer, long offset, long size) {
		state.bindUniformRange(binding, buffer, offset, size);
	}

	@Override
	public void bindTexelBuffer(int unit, int buffer, TexFormat format) {
		TexelKey key = new TexelKey(buffer, format);
		Integer texture = texelTextures.get(key);
		if (texture == null) {
			texture = GL11C.glGenTextures();
			state.bindTexture(unit, GlState.TARGET_BUFFER, texture);
			GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, Gl33Formats.internalFormat(format), buffer);
			counts.created(GlObjectCounts.Kind.TEXTURE);
			texelTextures.put(key, texture);
		}
		state.bindTexture(unit, GlState.TARGET_BUFFER, texture);
	}

	@Override
	public void forgetBuffer(int buffer) {
		Iterator<Map.Entry<TexelKey, Integer>> it = texelTextures.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<TexelKey, Integer> e = it.next();
			if (e.getKey().buffer() == buffer) {
				GL11C.glDeleteTextures(e.getValue());
				state.forgetTexture(e.getValue());
				counts.deleted(GlObjectCounts.Kind.TEXTURE);
				it.remove();
			}
		}
	}
}
