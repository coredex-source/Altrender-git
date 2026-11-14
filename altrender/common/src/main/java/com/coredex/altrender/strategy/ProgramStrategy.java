/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

import com.coredex.altrender.strategy.ShaderStage;

import java.util.List;

/**
 * GL shader and program objects per tier: compile, link (attribute locations bound by name first), sampler units.
 * Uniform blocks belong to {@link UniformStrategy}. Render thread only.
 */
public interface ProgramStrategy {
	String id();

	/** @param shader 0 if creation failed outright */
	record Result(int name, boolean ok, String log) {}

	Result compile(ShaderStage stage, String source);

	void deleteShader(int shader);

	/**
	 * Links a program; attribute {@code i} of {@code attributes} gets location {@code i}, and on strategies that bind
	 * them (GLSL 1.30–1.50, which can't declare locations), fragment output {@code i} of {@code fragmentOutputs} goes to
	 * draw buffer {@code i}.
	 */
	Result link(int vertexShader, int fragmentShader, List<String> attributes, List<String> fragmentOutputs);

	/** Links a compute program from one compute shader (GL 4.3). */
	Result linkCompute(int computeShader);

	/**
	 * Starts building a program from sources without waiting for the driver (development.md 4.5): compile and link
	 * are issued at once, and with {@code KHR_parallel_shader_compile} they run on driver threads. Poll {@link #built},
	 * then take {@link #buildResult}. Attributes and outputs as {@link #link}.
	 *
	 * @return the program name (0 if creation failed outright)
	 */
	int build(String vertexSource, String fragmentSource, List<String> attributes, List<String> fragmentOutputs);

	/**
	 * {@link #build} with any set of stages (a shader pack's vertex, tessellation, geometry and fragment shaders, or
	 * one compute shader). A failed build's log names each shader's stage.
	 */
	int build(java.util.Map<ShaderStage, String> sources, List<String> attributes, List<String> fragmentOutputs);

	/** Whether a {@link #build} has finished; never waits with parallel compilation (else always true). */
	boolean built(int program);

	/** The finished build: link status and the compile and link logs. Waits if it hasn't finished. */
	Result buildResult(int program);

	/** Whether builds run on driver threads ({@code KHR_parallel_shader_compile} in use). */
	boolean parallel();

	void deleteProgram(int program);

	/** Location of a plain uniform, or -1 if the program doesn't have it (or the driver dropped it). */
	int uniformLocation(int program, String name);

	// Plain uniforms of the current program. Per-draw data on gl33 goes this way, set before each draw (GL has no
	// push constants); gl45 indexes a buffer by gl_DrawID instead (Phase 4).

	void setUniform1i(int location, int x);

	void setUniform1f(int location, float x);

	void setUniform2f(int location, float x, float y);

	void setUniform3f(int location, float x, float y, float z);

	void setUniform4f(int location, float x, float y, float z, float w);

	void setUniformMatrix4f(int location, java.nio.FloatBuffer columnMajor);

	/** Points the sampler uniform {@code name} at texture unit {@code unit}. False if the program has no such uniform. */
	boolean setSamplerUnit(int program, String name, int unit);
}
