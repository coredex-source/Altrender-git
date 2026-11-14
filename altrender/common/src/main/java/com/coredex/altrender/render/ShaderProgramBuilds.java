/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.util.List;

import com.coredex.altrender.Log;
import com.coredex.altrender.api.PendingProgram;
import com.coredex.altrender.api.ShaderPrograms;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.terrain.TerrainShaders;
import com.coredex.altrender.terrain.TerrainVertex;

/** The API's {@link ShaderPrograms} on the tier's {@link ProgramStrategy} (development.md 4.5). Render thread only. */
final class ShaderProgramBuilds implements ShaderPrograms {

	private static final int GL_PROGRAM = 0x82E2;

	private final ProgramStrategy programs;

	ShaderProgramBuilds(ProgramStrategy programs) {
		this.programs = programs;
	}

	@Override
	public PendingProgram build(String name, String vertexSource, String fragmentSource, List<String> attributes,
			List<String> fragmentOutputs) {
		// Labelled once built: touching a program while it links can make the driver finish the build first.
		return new Pending(name, programs.build(vertexSource, fragmentSource, attributes, fragmentOutputs));
	}

	@Override
	public PendingProgram build(String name, java.util.Map<Stage, String> sources, List<String> attributes,
			List<String> fragmentOutputs) {
		java.util.Map<com.coredex.altrender.strategy.ShaderStage, String> s = new java.util.LinkedHashMap<>();
		for (Stage stage : Stage.values()) { // the pipeline's order, whatever the map's
			String src = sources.get(stage);
			if (src != null) {
				s.put(switch (stage) {
					case VERTEX -> com.coredex.altrender.strategy.ShaderStage.VERTEX;
					case TESS_CONTROL -> com.coredex.altrender.strategy.ShaderStage.TESS_CONTROL;
					case TESS_EVALUATION -> com.coredex.altrender.strategy.ShaderStage.TESS_EVALUATION;
					case GEOMETRY -> com.coredex.altrender.strategy.ShaderStage.GEOMETRY;
					case FRAGMENT -> com.coredex.altrender.strategy.ShaderStage.FRAGMENT;
					case COMPUTE -> com.coredex.altrender.strategy.ShaderStage.COMPUTE;
				}, src);
			}
		}
		return new Pending(name, programs.build(s, attributes, fragmentOutputs));
	}

	@Override
	public boolean parallel() {
		return programs.parallel();
	}

	private final class Pending implements PendingProgram {
		private final String name;
		private int program;
		private ProgramStrategy.Result result;

		Pending(String name, int program) {
			this.name = name;
			this.program = program;
			if (program == 0) {
				result = new ProgramStrategy.Result(0, false, "glCreateShader/glCreateProgram failed");
			}
		}

		@Override
		public boolean done() {
			return result != null || programs.built(program);
		}

		private ProgramStrategy.Result result() {
			if (result == null) {
				result = programs.buildResult(program);
				if (result.ok()) {
					GlDebug.label(GL_PROGRAM, program, name);
				} else {
					Log.warn("program " + name + " failed to build: " + result.log());
					programs.deleteProgram(program);
					program = 0;
				}
			}
			return result;
		}

		@Override
		public boolean ok() {
			return result().ok();
		}

		@Override
		public int program() {
			return result().ok() ? program : 0;
		}

		@Override
		public String log() {
			return result().log();
		}

		@Override
		public void delete() {
			if (program != 0) {
				if (result == null) {
					programs.buildResult(program); // detaches and frees its shaders
				}
				programs.deleteProgram(program);
				program = 0;
			}
		}
	}

	/**
	 * {@code -Daltrender.debug.programTest=N} (development.md 4.5): builds N distinct variants of the terrain program
	 * (salted, so no shader cache helps) as a shader pack would, polling once a millisecond like a loading screen,
	 * and logs how long it took and how much of it the render thread spent inside GL calls.
	 */
	void selfTest(int n) {
		String salt = "// " + System.nanoTime() + " variant ";
		long start = System.nanoTime();
		PendingProgram[] pending = new PendingProgram[n];
		for (int i = 0; i < n; i++) {
			pending[i] = build("ar:test-" + i, variant(TerrainShaders.VERTEX, salt + i),
					variant(TerrainShaders.FRAGMENT, salt + i), TerrainVertex.ATTRIBUTES, List.of("fragColor"));
		}
		long submitted = System.nanoTime();
		long inGl = submitted - start;
		int done = 0;
		boolean[] finished = new boolean[n];
		int failed = 0;
		while (done < n) {
			for (int i = 0; i < n; i++) {
				if (finished[i]) {
					continue;
				}
				long t = System.nanoTime();
				if (pending[i].done()) {
					finished[i] = true;
					done++;
					if (!pending[i].ok()) {
						failed++;
					}
				}
				inGl += System.nanoTime() - t;
			}
			if (done < n) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		long total = System.nanoTime() - start;
		for (PendingProgram p : pending) {
			p.delete();
		}
		Log.info(String.format(java.util.Locale.ROOT,
				"program test: %d programs, parallel=%s: all built in %.1f ms (issuing them %.1f ms), render thread "
						+ "inside GL %.1f ms, %d failed", n, parallel(), total / 1e6, (submitted - start) / 1e6, inGl / 1e6,
				failed));
	}

	private static String variant(String source, String comment) {
		int line = source.indexOf('\n') + 1;
		return source.substring(0, line) + comment + "\n" + source.substring(line);
	}
}
