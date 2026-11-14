/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy.gl33;

import com.coredex.altrender.debug.GlObjectCounts;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.strategy.ShaderStage;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.KHRParallelShaderCompile;

import java.util.List;

/**
 * Shaders and programs for every tier (core 2.0 entry points, plus {@code glBindFragDataLocation} on GL 3.x for GLSL
 * 1.40, whose fragment outputs can't carry a location; nothing tier-specific beyond the GLSL itself).
 */
public final class Gl33ProgramStrategy implements ProgramStrategy {
	private final GlState state;
	private final GlObjectCounts counts;
	private final boolean bindFragmentOutputs;
	private final boolean parallel;
	/** Builds in flight: program → its shaders, deleted once the build is taken. */
	private final java.util.Map<Integer, int[]> building = new java.util.HashMap<>();
	/** The stage of each shader of a build in flight, for its log. */
	private final java.util.Map<Integer, ShaderStage> stages = new java.util.HashMap<>();

	public Gl33ProgramStrategy(GlState state, GlObjectCounts counts) {
		this(state, counts, false, false);
	}

	/**
	 * @param bindFragmentOutputs bind fragment outputs by name before linking (GLSL 1.30–1.50 on a GL 3.x context)
	 * @param parallel            use {@code KHR_parallel_shader_compile} (present and not switched off)
	 */
	public Gl33ProgramStrategy(GlState state, GlObjectCounts counts, boolean bindFragmentOutputs, boolean parallel) {
		this.state = state;
		this.counts = counts;
		this.bindFragmentOutputs = bindFragmentOutputs;
		this.parallel = parallel;
		if (parallel) {
			// As many driver threads as it likes (the extension's default may be fewer). KHR and ARB share the tokens.
			if (org.lwjgl.opengl.GL.getCapabilities().GL_KHR_parallel_shader_compile) {
				KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(0xFFFFFFFF);
			} else {
				org.lwjgl.opengl.ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(0xFFFFFFFF);
			}
		}
	}

	@Override
	public String id() {
		return bindFragmentOutputs ? "gl31-program" : "gl33-program";
	}

	@Override
	public Result compile(ShaderStage stage, String source) {
		int shader = GL20C.glCreateShader(glStage(stage));
		if (shader == 0) {
			return new Result(0, false, "glCreateShader failed");
		}
		counts.created(GlObjectCounts.Kind.SHADER);
		GL20C.glShaderSource(shader, source);
		GL20C.glCompileShader(shader);
		boolean ok = GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_TRUE;
		return new Result(shader, ok, GL20C.glGetShaderInfoLog(shader));
	}

	@Override
	public void deleteShader(int shader) {
		GL20C.glDeleteShader(shader);
		counts.deleted(GlObjectCounts.Kind.SHADER);
	}

	@Override
	public Result link(int vertexShader, int fragmentShader, List<String> attributes, List<String> fragmentOutputs) {
		int program = GL20C.glCreateProgram();
		if (program == 0) {
			return new Result(0, false, "glCreateProgram failed");
		}
		counts.created(GlObjectCounts.Kind.PROGRAM);
		GL20C.glAttachShader(program, vertexShader);
		GL20C.glAttachShader(program, fragmentShader);
		for (int i = 0; i < attributes.size(); i++) {
			GL20C.glBindAttribLocation(program, i, attributes.get(i));
		}
		if (bindFragmentOutputs) {
			for (int i = 0; i < fragmentOutputs.size(); i++) {
				org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, i, fragmentOutputs.get(i));
			}
		}
		GL20C.glLinkProgram(program);
		boolean ok = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
		String log = GL20C.glGetProgramInfoLog(program);
		// Shader objects stay cached for other programs; the program no longer needs them attached.
		GL20C.glDetachShader(program, vertexShader);
		GL20C.glDetachShader(program, fragmentShader);
		return new Result(program, ok, log);
	}

	@Override
	public Result linkCompute(int computeShader) {
		int program = GL20C.glCreateProgram();
		if (program == 0) {
			return new Result(0, false, "glCreateProgram failed");
		}
		counts.created(GlObjectCounts.Kind.PROGRAM);
		GL20C.glAttachShader(program, computeShader);
		GL20C.glLinkProgram(program);
		boolean ok = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
		String log = GL20C.glGetProgramInfoLog(program);
		GL20C.glDetachShader(program, computeShader);
		return new Result(program, ok, log);
	}

	@Override
	public int build(String vertexSource, String fragmentSource, List<String> attributes, List<String> fragmentOutputs) {
		int vs = GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);
		int fs = GL20C.glCreateShader(GL20C.GL_FRAGMENT_SHADER);
		int program = GL20C.glCreateProgram();
		if (vs == 0 || fs == 0 || program == 0) {
			GL20C.glDeleteShader(vs);
			GL20C.glDeleteShader(fs);
			GL20C.glDeleteProgram(program);
			return 0;
		}
		counts.created(GlObjectCounts.Kind.SHADER);
		counts.created(GlObjectCounts.Kind.SHADER);
		counts.created(GlObjectCounts.Kind.PROGRAM);
		GL20C.glShaderSource(vs, vertexSource);
		GL20C.glShaderSource(fs, fragmentSource);
		GL20C.glCompileShader(vs);
		GL20C.glCompileShader(fs);
		// No status query in between: with parallel compilation the link queues behind the compiles.
		GL20C.glAttachShader(program, vs);
		GL20C.glAttachShader(program, fs);
		for (int i = 0; i < attributes.size(); i++) {
			GL20C.glBindAttribLocation(program, i, attributes.get(i));
		}
		if (bindFragmentOutputs) {
			for (int i = 0; i < fragmentOutputs.size(); i++) {
				org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, i, fragmentOutputs.get(i));
			}
		}
		GL20C.glLinkProgram(program);
		building.put(program, new int[] {vs, fs});
		return program;
	}

	@Override
	public int build(java.util.Map<ShaderStage, String> sources, List<String> attributes, List<String> fragmentOutputs) {
		int program = GL20C.glCreateProgram();
		if (program == 0) {
			return 0;
		}
		int[] shaders = new int[sources.size()];
		int n = 0;
		for (java.util.Map.Entry<ShaderStage, String> e : sources.entrySet()) {
			int shader = GL20C.glCreateShader(glStage(e.getKey()));
			if (shader == 0) {
				for (int i = 0; i < n; i++) {
					stages.remove(shaders[i]);
					deleteShader(shaders[i]);
				}
				GL20C.glDeleteProgram(program);
				return 0;
			}
			counts.created(GlObjectCounts.Kind.SHADER);
			GL20C.glShaderSource(shader, e.getValue());
			GL20C.glCompileShader(shader);
			GL20C.glAttachShader(program, shader);
			stages.put(shader, e.getKey());
			shaders[n++] = shader;
		}
		counts.created(GlObjectCounts.Kind.PROGRAM);
		for (int i = 0; i < attributes.size(); i++) {
			GL20C.glBindAttribLocation(program, i, attributes.get(i));
		}
		if (bindFragmentOutputs) {
			for (int i = 0; i < fragmentOutputs.size(); i++) {
				org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, i, fragmentOutputs.get(i));
			}
		}
		GL20C.glLinkProgram(program);
		building.put(program, shaders);
		return program;
	}

	@Override
	public boolean built(int program) {
		return !parallel || !building.containsKey(program)
				|| GL20C.glGetProgrami(program, KHRParallelShaderCompile.GL_COMPLETION_STATUS_KHR) == GL11C.GL_TRUE;
	}

	@Override
	public Result buildResult(int program) {
		int[] shaders = building.remove(program);
		boolean ok = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
		StringBuilder log = new StringBuilder();
		if (shaders != null) {
			for (int shader : shaders) {
				ShaderStage stage = stages.remove(shader);
				if (!ok) {
					String shaderLog = GL20C.glGetShaderInfoLog(shader).trim();
					if (!shaderLog.isEmpty()) {
						log.append(stage != null ? stage.name().toLowerCase(java.util.Locale.ROOT) + ": " : "")
								.append(shaderLog).append('\n');
					}
				}
				GL20C.glDetachShader(program, shader);
				deleteShader(shader);
			}
		}
		log.append(GL20C.glGetProgramInfoLog(program).trim());
		return new Result(program, ok, log.toString().trim());
	}

	@Override
	public boolean parallel() {
		return parallel;
	}

	private static int glStage(ShaderStage stage) {
		return switch (stage) {
			case VERTEX -> GL20C.GL_VERTEX_SHADER;
			case FRAGMENT -> GL20C.GL_FRAGMENT_SHADER;
			case COMPUTE -> org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
			case GEOMETRY -> org.lwjgl.opengl.GL32C.GL_GEOMETRY_SHADER;
			case TESS_CONTROL -> org.lwjgl.opengl.GL40C.GL_TESS_CONTROL_SHADER;
			case TESS_EVALUATION -> org.lwjgl.opengl.GL40C.GL_TESS_EVALUATION_SHADER;
		};
	}

	@Override
	public void deleteProgram(int program) {
		GL20C.glDeleteProgram(program);
		state.forgetProgram(program);
		counts.deleted(GlObjectCounts.Kind.PROGRAM);
	}

	@Override
	public int uniformLocation(int program, String name) {
		return GL20C.glGetUniformLocation(program, name);
	}

	@Override
	public void setUniform1i(int location, int x) {
		GL20C.glUniform1i(location, x);
	}

	@Override
	public void setUniform1f(int location, float x) {
		GL20C.glUniform1f(location, x);
	}

	@Override
	public void setUniform2f(int location, float x, float y) {
		GL20C.glUniform2f(location, x, y);
	}

	@Override
	public void setUniform3f(int location, float x, float y, float z) {
		GL20C.glUniform3f(location, x, y, z);
	}

	@Override
	public void setUniform4f(int location, float x, float y, float z, float w) {
		GL20C.glUniform4f(location, x, y, z, w);
	}

	@Override
	public void setUniformMatrix4f(int location, java.nio.FloatBuffer columnMajor) {
		GL20C.glUniformMatrix4fv(location, false, columnMajor);
	}


	@Override
	public boolean setSamplerUnit(int program, String name, int unit) {
		int location = GL20C.glGetUniformLocation(program, name);
		if (location < 0) {
			return false;
		}
		state.useProgram(program);
		GL20C.glUniform1i(location, unit);
		return true;
	}
}
