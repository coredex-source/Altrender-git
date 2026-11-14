/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.gpu;

/**
 * GPU objects and drawing for a {@link com.coredex.altrender.api.render.WorldPipeline} (since 0.3.0, development.md
 * 6.1), on Altrender's GL context and state cache: an addon makes no GL calls of its own, so Altrender always knows
 * the GL state and gives the game back its own after each pass. Render thread only; GL enums are plain ints.
 * <p>
 * The pipeline's own draws (clears, full-screen passes, compute) go between {@link #begin} and {@link #end}; outside
 * them only {@link #gameDraws} changes state.
 */
public interface Gpu {

	GpuTexture createTexture(TextureSpec spec, String label);

	GpuFramebuffer createFramebuffer(String label);

	/** A zero-filled buffer of {@code size} bytes. */
	GpuBuffer createBuffer(long size, String label);

	/**
	 * Where the game's own world draws go from now on, and with which program ({@code 0}: the fixed-function
	 * pipeline, as vanilla); {@code target null} is the screen. Bound at once and kept across Altrender's own passes
	 * (they restore it, not the screen). {@code gameDraws(null, 0)} undoes it.
	 */
	void gameDraws(GpuFramebuffer target, int program);

	/** Like {@link #gameDraws} for the program alone, the target unchanged. */
	void gameProgram(int program);

	/**
	 * A texture the game's draws find on {@code unit} (1 and up: the game binds its own on unit 0), kept across
	 * Altrender's passes like {@link #gameDraws}; {@code null} gives the unit back to the game.
	 */
	void gameTexture(int unit, GpuTexture texture);

	/**
	 * Light coordinates for the game's own draws, which have none on old versions: {@code gl_MultiTexCoord1} becomes
	 * (block, sky) in modern lightmap units (0–240) and {@code gl_TextureMatrix[1]} the modern lightmap matrix
	 * ({@code (uv + 8) / 256}); NaN puts the game's own (unit 1 unused) back.
	 */
	void gameLightCoords(float block, float sky);

	/** Start of the pipeline's own GL work ({@code label}: a debug group). */
	void begin(String label);

	/** End of it: the game's state is back, with {@link #gameDraws}' target and program. */
	void end();

	/**
	 * Whether the game's own alpha test reaches the driver ({@code true}, the default) or the pipeline tests alpha in
	 * its programs ({@code false}, as Iris; the game's state is still tracked and comes back with {@code true} or at
	 * the frame's end).
	 */
	default void gameAlphaTest(boolean gameOwn) {
	}

	/**
	 * The blending of the game's draws (and Altrender's terrain) from now: {@code blend}, or {@link Blend#NONE} for
	 * off, instead of the game's own (a pack's {@code blend.<program>}); null gives the game's own back. The game's
	 * blend calls are still tracked meanwhile.
	 */
	default void gameBlend(Blend blend) {
	}

	/**
	 * Blending per draw buffer of the game's draws, over {@link #gameBlend} and the game's own (a pack's
	 * {@code blend.<program>.<buffer>}): attachment {@code i} of the framebuffer given to {@link #gameDraws} takes
	 * {@code blends[i]} ({@link Blend#NONE}: off; null: as the rest); null or empty puts none. Blend functions per
	 * buffer need {@code Capabilities.drawBuffersBlend}; on/off works everywhere. Since 0.3.0.
	 */
	default void gameBufferBlends(Blend[] blends) {
	}

	/** Starts timing {@code pass} on the GPU (when F3's GPU breakdown is on); not nested. */
	default void beginTimer(TimedPass pass) {
	}

	/** Stops timing {@code pass}. */
	default void endTimer(TimedPass pass) {
	}

	/** {@code null}: the screen (the game's default framebuffer). */
	void bindFramebuffer(GpuFramebuffer target);

	void viewport(int x, int y, int width, int height);

	void useProgram(int program);

	/** Location of a uniform of {@code program}, or -1. */
	int uniformLocation(int program, String name);

	/**
	 * The program's active uniforms: name (an array's without {@code [0]}) to its GL type ({@code GL_FLOAT},
	 * {@code GL_INT_VEC2}, {@code GL_SAMPLER_2D}, …).
	 */
	java.util.Map<String, Integer> activeUniforms(int program);

	// Uniforms of the program in use.

	void uniform1i(int location, int x);

	void uniform2i(int location, int x, int y);

	void uniform3i(int location, int x, int y, int z);

	void uniform4i(int location, int x, int y, int z, int w);

	void uniform1f(int location, float x);

	void uniform2f(int location, float x, float y);

	void uniform3f(int location, float x, float y, float z);

	void uniform4f(int location, float x, float y, float z, float w);

	/** A column-major 4×4 matrix (16 floats). */
	void uniformMatrix4(int location, float[] m);

	/** A column-major 3×3 matrix (9 floats). */
	void uniformMatrix3(int location, float[] m);

	/** {@code count} vec4s from {@code v} (4 floats each). */
	void uniform4fv(int location, float[] v);

	void bindTexture(int unit, GpuTexture texture);

	/** One of the game's own textures (the block atlas, a lightmap) by GL name, as a {@code GL_TEXTURE_2D}. */
	void bindGameTexture(int unit, int name);

	/** Image unit binding (needs {@code imageLoadStore}): access {@code GL_READ_WRITE} etc., format e.g. {@code GL_RGBA16F}. */
	void bindImage(int unit, GpuTexture texture, int level, int access, int format);

	/** Shader storage binding (needs {@code storageBuffers}). */
	void bindStorageBuffer(int index, GpuBuffer buffer);

	void bindUniformBuffer(int index, GpuBuffer buffer);

	/** For every draw buffer; {@code null} switches blending off. */
	void blend(Blend blend);

	/** For one draw buffer (needs {@code drawBuffersBlend}); {@code null} switches it off there. */
	void blend(int drawBuffer, Blend blend);

	/** Depth test with {@code func} ({@code GL_LEQUAL}; 0 for no test) and depth writes. */
	void depth(int func, boolean write);

	void colorMask(boolean r, boolean g, boolean b, boolean a);

	/** Clears draw buffer {@code drawBuffer} of the bound framebuffer to a colour (float formats and normalised ones). */
	void clearColor(int drawBuffer, float r, float g, float b, float a);

	/** Clears draw buffer {@code drawBuffer} of the bound framebuffer, an integer format. */
	void clearColorInt(int drawBuffer, int r, int g, int b, int a);

	/** Sets only the alpha of draw buffer {@code drawBuffer} of the bound framebuffer (red, green and blue kept). */
	void clearAlpha(int drawBuffer, float a);

	void clearDepth(float depth);

	/**
	 * Draws a full-screen quad as OptiFine and Iris do for composite passes: {@code gl_Vertex} and
	 * {@code gl_MultiTexCoord0} from (0,0) to (1,1), white {@code gl_Color}, the projection an orthographic 0..1 box
	 * and the modelview identity (both put back after).
	 */
	void drawFullscreen();

	/** Zeroes every level-0 texel of {@code texture} (a pack's images; needs GL 4.4, the gl45 tier). */
	void clearTexture(GpuTexture texture);

	/** A compute program's {@code local_size} (x, y, z). */
	int[] computeWorkGroupSize(int program);

	/**
	 * One of the game's own textures by its modern resource path ({@code textures/environment/clouds.png}), as GL
	 * name, or 0 where the version has none.
	 */
	int gameTextureByPath(String path);

	/** {@code glDispatchCompute} with the program in use (needs {@code compute}). */
	void dispatch(int x, int y, int z);

	/** {@code glMemoryBarrier(bits)}. */
	void memoryBarrier(int bits);

	/** Copies the bound read framebuffer's depth into {@code texture}'s level 0 (e.g. Iris's {@code depthtex1}). */
	void copyDepth(GpuFramebuffer from, GpuTexture texture);

	/** Copies colour attachment {@code attachment} of {@code from} into {@code texture}'s level 0. */
	void copyColor(GpuFramebuffer from, int attachment, GpuTexture texture);
}
