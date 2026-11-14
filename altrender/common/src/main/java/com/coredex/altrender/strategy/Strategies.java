/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import java.util.Set;

import com.coredex.altrender.Log;
import com.coredex.altrender.caps.Feature;
import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.caps.Tier;
import com.coredex.altrender.caps.TierChoice;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.gl33.Gl33BufferStrategy;
import com.coredex.altrender.strategy.gl33.Gl33ProgramStrategy;
import com.coredex.altrender.strategy.gl33.Gl33StreamBuffer;
import com.coredex.altrender.strategy.gl33.Gl33SyncStrategy;
import com.coredex.altrender.strategy.gl33.Gl33TextureStrategy;
import com.coredex.altrender.strategy.gl33.Gl33UniformStrategy;
import com.coredex.altrender.strategy.gl33.Gl33VertexInputStrategy;
import com.coredex.altrender.strategy.gl45.Gl45BufferStrategy;
import com.coredex.altrender.strategy.gl45.Gl45StreamRing;
import com.coredex.altrender.strategy.gl45.Gl45TextureStrategy;
import com.coredex.altrender.strategy.gl45.Gl45VertexInputStrategy;

/**
 * The strategy set for the chosen tier (development.md 0.4), made once when the renderer installs. Wiring:
 * gl45 has DSA buffers (persistent maps), immutable DSA textures and
 * {@code vertex_attrib_binding} with multi-draw and indirect draws; uniforms, sync and programs are gl33's on both.
 * Every Altrender pass runs between {@link #beginPass} and {@link #endPass}, which puts the game's state back.
 */
public record Strategies(Tier tier, GlState state, BufferStrategy buffer, TextureStrategy texture,
		UniformStrategy uniform, VertexInputStrategy vertexInput, SyncStrategy sync, ProgramStrategy program,
		GlObjectCounts counts, StreamBuffer stream, Set<Feature> features) {

	/** Stream buffer sizes: gl45's ring also stages uploads (up to the terrain's 16 MB per frame). */
	private static final long RING_BYTES = 64L << 20;
	private static final long GL33_STREAM_BYTES = 4L << 20;

	/** Whether the tier uses {@code f} (present, and not switched off by a driver rule or dev switch). */
	public boolean has(Feature f) {
		return features.contains(f);
	}

	public static Strategies create(TierChoice choice, GlCaps caps) {
		Tier tier = choice.tier();
		Set<Feature> features = choice.features();
		GlObjectCounts counts = new GlObjectCounts();
		boolean unsynchronizedMaps = !Boolean.getBoolean("altrender.debug.syncMaps");
		GlState state = new GlState(caps.maxTextureUnits, Math.max(1, caps.maxUniformBindings), true);
		Gl33UniformStrategy uniform = new Gl33UniformStrategy(state, counts, caps.maxUniformBindings);
		Strategies s;
		if (tier == Tier.GL45) {
			Gl45VertexInputStrategy vertexInput = new Gl45VertexInputStrategy(state, counts, Strategies::labelVao);
			Gl33SyncStrategy sync = new Gl33SyncStrategy(counts);
			Gl45BufferStrategy buffer = new Gl45BufferStrategy(counts, unsynchronizedMaps, state, vertexInput, uniform);
			Gl45StreamRing ring = new Gl45StreamRing(RING_BYTES, sync, counts);
			buffer.setStaging(ring);
			s = new Strategies(tier, state, buffer, new Gl45TextureStrategy(caps, counts, state), uniform, vertexInput,
					sync, new Gl33ProgramStrategy(state, counts, false, features.contains(Feature.PARALLEL_SHADER_COMPILE)), counts, ring, features);
		} else if (tier == Tier.GL33) {
			Gl33VertexInputStrategy vertexInput = new Gl33VertexInputStrategy(state, counts, Strategies::labelVao);
			Gl33BufferStrategy buffer = new Gl33BufferStrategy(counts, unsynchronizedMaps, state, vertexInput, uniform);
			s = new Strategies(tier, state, buffer, new Gl33TextureStrategy(caps, counts, state), uniform, vertexInput,
					new Gl33SyncStrategy(counts), new Gl33ProgramStrategy(state, counts, false, features.contains(Feature.PARALLEL_SHADER_COMPILE)), counts,
					new Gl33StreamBuffer(buffer, GL33_STREAM_BYTES), features);
		} else {
			throw new IllegalArgumentException("no strategies for tier " + tier);
		}
		Log.info("strategies: buffer=" + s.buffer.id() + " texture=" + s.texture.id() + " uniform=" + s.uniform.id()
				+ " vertexInput=" + s.vertexInput.id() + " sync=" + s.sync.id() + " program=" + s.program.id()
				+ " stream=" + s.stream.id());
		return s;
	}

	/** End of a frame: the stream's ranges of this frame are fenced (development.md 4.2). */
	public void frameEnd() {
		stream.frameEnd();
	}

	/** Frees what the strategies own themselves (the stream). Objects made through them are their owners' to free. */
	public void delete() {
		stream.delete();
	}

	/** Altrender passes running (render thread): state changes in them aren't the game's. */
	private static int passes;

	/** Whether an Altrender pass (its own or a pipeline's GL work) is running. */
	public static boolean inPass() {
		return passes > 0;
	}

	/** Start of an Altrender pass: the game may have changed anything since the last one. */
	public void beginPass(String name) {
		passes++;
		GlDebug.push(name);
		state.invalidate();
	}

	/** End of an Altrender pass: the game's bindings and raster state are back (development.md 1.5). */
	public void endPass() {
		state.restoreGameState();
		GlDebug.pop();
		passes--;
	}

	private static void labelVao(Integer vao, VertexLayout layout) {
		GlDebug.label(0x8074, vao, "vao stride " + layout.stride() + " " + layout.names()); // GL_VERTEX_ARRAY
	}
}
