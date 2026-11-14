/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

import java.util.List;

/**
 * Builds GL programs without stalling the render thread (development.md 4.5): every call returns at once; where the
 * driver compiles on its own threads ({@code KHR_parallel_shader_compile}) hundreds of programs build while the game
 * keeps drawing (a shader pack's loading screen). Render thread only, on Altrender's GL context.
 */
public interface ShaderPrograms {

	/**
	 * Starts building a program from GLSL sources.
	 *
	 * @param name            for logs and the debug label
	 * @param attributes      vertex attributes: entry {@code i} gets location {@code i}
	 * @param fragmentOutputs fragment outputs: entry {@code i} goes to draw buffer {@code i} where the GLSL version
	 *                        can't say so itself
	 */
	PendingProgram build(String name, String vertexSource, String fragmentSource, List<String> attributes,
			List<String> fragmentOutputs);

	/** Shader stages (since 0.3.0). */
	enum Stage {
		VERTEX,
		/** Needs {@link Capabilities#tessellation()}. */
		TESS_CONTROL,
		TESS_EVALUATION,
		GEOMETRY,
		FRAGMENT,
		/** Alone in its program; needs {@link Capabilities#compute()}. */
		COMPUTE
	}

	/**
	 * Starts building a program from any set of stages (since 0.3.0): a shader pack's vertex, tessellation, geometry
	 * and fragment shaders, or one compute shader. A failed build's {@link PendingProgram#log()} names each failing
	 * shader's stage. Attributes and outputs as {@link #build(String, String, String, List, List)}.
	 */
	PendingProgram build(String name, java.util.Map<Stage, String> sources, List<String> attributes,
			List<String> fragmentOutputs);

	/** Whether builds run on driver threads; without, a build's work happens when its result is first asked for. */
	boolean parallel();
}
