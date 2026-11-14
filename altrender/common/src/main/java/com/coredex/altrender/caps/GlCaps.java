/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.caps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * What the current context offers, probed once at context creation (development.md 0.4). Immutable. Presence flags
 * come from LWJGL's capabilities, which also require the entry points to load (a driver that claims 4.6 but lacks a
 * 4.6 function reports 4.5 here).
 */
public final class GlCaps {

	private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY = 0x84FF; // EXT/ARB_texture_filter_anisotropic, GL 4.6

	public final String vendor;
	public final String renderer;
	/** {@code GL_VERSION} as the driver reports it. */
	public final String version;
	/** The version LWJGL could load completely (its {@code OpenGLxy} flags). */
	public final int major;
	public final int minor;
	/** {@code compat}, or {@code core} (Altrender never asks for a core context). */
	public final String profile;
	public final String glsl;
	public final Set<String> extensions;

	public final int maxTextureSize;
	public final int max3dTextureSize;
	public final int maxTextureUnits;
	public final int maxVertexAttribs;
	public final int maxDrawBuffers;
	public final int maxUniformBlockSize;
	public final int uniformBufferAlignment;
	public final int maxUniformBindings;
	public final long maxSsboSize;
	public final int ssboAlignment;
	public final float maxAnisotropy;
	public final int[] computeGroupCount;
	public final int[] computeGroupSize;
	public final int computeInvocations;

	/** gl45 requirements: GL 4.5, or 3.3 with each of these. */
	public final boolean dsa;
	public final boolean bufferStorage;
	public final boolean multiDrawIndirect;
	public final boolean vertexAttribBinding;
	public final boolean textureStorage;
	public final boolean baseInstance;
	/** GL 4.3 features (AltShaders, Photon-class packs). */
	public final boolean compute;
	public final boolean ssbo;
	public final boolean imageLoadStore;
	/** The GL 4.6 extras gl45 uses where present. */
	public final boolean indirectCount;
	public final boolean shaderDrawParameters;
	public final boolean anisotropic;
	public final boolean polygonOffsetClamp;
	/** Debug output, object labels, debug groups (KHR_debug, GL 4.3). */
	public final boolean khrDebug;
	/** Timestamp queries (ARB_timer_query, GL 3.3). */
	public final boolean timerQuery;
	/** Shaders compile on driver threads, polled with {@code GL_COMPLETION_STATUS} (KHR/ARB_parallel_shader_compile). */
	public final boolean parallelShaderCompile;

	private GlCaps() {
		GLCapabilities c = GL.getCapabilities();
		vendor = GL11C.glGetString(GL11C.GL_VENDOR);
		renderer = GL11C.glGetString(GL11C.GL_RENDERER);
		version = GL11C.glGetString(GL11C.GL_VERSION);
		int[] v = loadedVersion(c);
		major = v[0];
		minor = v[1];
		profile = c.OpenGL32 && (GL11C.glGetInteger(GL32C.GL_CONTEXT_PROFILE_MASK) & GL32C.GL_CONTEXT_CORE_PROFILE_BIT) != 0
				? "core" : "compat";
		glsl = c.OpenGL20 ? GL11C.glGetString(GL20C.GL_SHADING_LANGUAGE_VERSION) : "none";
		extensions = readExtensions(c);

		maxTextureSize = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
		max3dTextureSize = c.OpenGL12 ? GL11C.glGetInteger(0x8073) : 0; // GL_MAX_3D_TEXTURE_SIZE
		maxTextureUnits = c.OpenGL20 ? GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) : 0;
		maxVertexAttribs = c.OpenGL20 ? GL11C.glGetInteger(GL20C.GL_MAX_VERTEX_ATTRIBS) : 0;
		maxDrawBuffers = c.OpenGL20 ? GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS) : 1;
		boolean ubo = c.OpenGL31 || c.GL_ARB_uniform_buffer_object;
		maxUniformBlockSize = ubo ? GL11C.glGetInteger(GL31C.GL_MAX_UNIFORM_BLOCK_SIZE) : 0;
		uniformBufferAlignment = ubo ? GL11C.glGetInteger(GL31C.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT) : 0;
		maxUniformBindings = ubo ? GL11C.glGetInteger(GL31C.GL_MAX_UNIFORM_BUFFER_BINDINGS) : 0;

		dsa = c.OpenGL45 || c.GL_ARB_direct_state_access;
		bufferStorage = c.OpenGL44 || c.GL_ARB_buffer_storage;
		multiDrawIndirect = c.OpenGL43 || c.GL_ARB_multi_draw_indirect;
		vertexAttribBinding = c.OpenGL43 || c.GL_ARB_vertex_attrib_binding;
		textureStorage = c.OpenGL42 || c.GL_ARB_texture_storage;
		baseInstance = c.OpenGL42 || c.GL_ARB_base_instance;
		compute = c.OpenGL43 || c.GL_ARB_compute_shader;
		ssbo = c.OpenGL43 || c.GL_ARB_shader_storage_buffer_object;
		imageLoadStore = c.OpenGL42 || c.GL_ARB_shader_image_load_store;
		indirectCount = c.OpenGL46 || c.GL_ARB_indirect_parameters;
		shaderDrawParameters = c.OpenGL46 || c.GL_ARB_shader_draw_parameters;
		anisotropic = c.OpenGL46 || c.GL_ARB_texture_filter_anisotropic || c.GL_EXT_texture_filter_anisotropic;
		polygonOffsetClamp = c.OpenGL46 || c.GL_ARB_polygon_offset_clamp || c.GL_EXT_polygon_offset_clamp;
		khrDebug = c.OpenGL43 || c.GL_KHR_debug;
		timerQuery = c.OpenGL33 || c.GL_ARB_timer_query;
		parallelShaderCompile = c.GL_KHR_parallel_shader_compile || c.GL_ARB_parallel_shader_compile;

		maxSsboSize = ssbo ? GL32C.glGetInteger64(GL43C.GL_MAX_SHADER_STORAGE_BLOCK_SIZE) : 0;
		ssboAlignment = ssbo ? GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT) : 0;
		maxAnisotropy = anisotropic ? GL11C.glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY) : 1f;
		computeGroupCount = new int[3];
		computeGroupSize = new int[3];
		if (compute) {
			for (int i = 0; i < 3; i++) {
				computeGroupCount[i] = GL30C.glGetIntegeri(GL43C.GL_MAX_COMPUTE_WORK_GROUP_COUNT, i);
				computeGroupSize[i] = GL30C.glGetIntegeri(GL43C.GL_MAX_COMPUTE_WORK_GROUP_SIZE, i);
			}
			computeInvocations = GL11C.glGetInteger(GL43C.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
		} else {
			computeInvocations = 0;
		}
		// The probe must leave no error behind for the game's own glGetError checks.
		for (int i = 0; i < 16 && GL11C.glGetError() != GL11C.GL_NO_ERROR; i++) {
		}
	}

	/** Probes the context current on this thread. */
	public static GlCaps probe() {
		return new GlCaps();
	}

	public boolean atLeast(int major, int minor) {
		return this.major > major || this.major == major && this.minor >= minor;
	}

	/** The gl45 tier's requirements hold: GL 4.5, or 3.3 with the six extensions. */
	public boolean gl45() {
		return atLeast(4, 5) || atLeast(3, 3) && gl45Missing().isEmpty();
	}

	/** What gl45 lacks, for the log. Empty when the tier is available. */
	public List<String> gl45Missing() {
		if (atLeast(4, 5)) {
			return List.of();
		}
		List<String> missing = new ArrayList<>();
		if (!atLeast(3, 3)) {
			missing.add("GL 3.3");
		}
		add(missing, dsa, "ARB_direct_state_access");
		add(missing, bufferStorage, "ARB_buffer_storage");
		add(missing, multiDrawIndirect, "ARB_multi_draw_indirect");
		add(missing, vertexAttribBinding, "ARB_vertex_attrib_binding");
		add(missing, textureStorage, "ARB_texture_storage");
		add(missing, baseInstance, "ARB_base_instance");
		return missing;
	}

	/** One line per group, for the log and caps.txt. */
	public List<String> table() {
		List<String> t = new ArrayList<>();
		t.add("context: GL " + major + "." + minor + " " + profile + " (GL_VERSION " + version + "), GLSL " + glsl);
		t.add("device: " + renderer + " / " + vendor);
		t.add("limits: texture " + maxTextureSize + ", 3D " + max3dTextureSize + ", units " + maxTextureUnits
				+ ", vertex attribs " + maxVertexAttribs + ", draw buffers " + maxDrawBuffers + ", UBO "
				+ maxUniformBlockSize + " (align " + uniformBufferAlignment + ", " + maxUniformBindings + " bindings), SSBO " + maxSsboSize + " (align "
				+ ssboAlignment + "), anisotropy " + maxAnisotropy);
		t.add("compute: " + (compute ? "groups " + computeGroupCount[0] + "x" + computeGroupCount[1] + "x"
				+ computeGroupCount[2] + ", size " + computeGroupSize[0] + "x" + computeGroupSize[1] + "x"
				+ computeGroupSize[2] + ", invocations " + computeInvocations : "no")
				+ "; ssbo " + yes(ssbo) + ", image load/store " + yes(imageLoadStore) + "; KHR_debug " + yes(khrDebug)
				+ ", timer query " + yes(timerQuery) + ", parallel shader compile " + yes(parallelShaderCompile));
		t.add("gl45: " + (gl45() ? "yes" : "no, missing " + String.join(", ", gl45Missing()))
				+ "; 4.6 extras: indirect count " + yes(indirectCount) + ", shader draw parameters "
				+ yes(shaderDrawParameters) + ", anisotropic " + yes(anisotropic) + ", polygon offset clamp "
				+ yes(polygonOffsetClamp));
		t.add("extensions: " + extensions.size());
		return t;
	}

	private static int[] loadedVersion(GLCapabilities c) {
		boolean[] flags = {c.OpenGL11, c.OpenGL12, c.OpenGL13, c.OpenGL14, c.OpenGL15, c.OpenGL20, c.OpenGL21, c.OpenGL30,
				c.OpenGL31, c.OpenGL32, c.OpenGL33, c.OpenGL40, c.OpenGL41, c.OpenGL42, c.OpenGL43, c.OpenGL44, c.OpenGL45,
				c.OpenGL46};
		int[][] versions = {{1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {3, 2}, {3, 3},
				{4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}, {4, 5}, {4, 6}};
		int[] best = {1, 0};
		for (int i = 0; i < flags.length && flags[i]; i++) {
			best = versions[i];
		}
		return best;
	}

	private static Set<String> readExtensions(GLCapabilities c) {
		Set<String> set = new TreeSet<>();
		if (c.OpenGL30) {
			int n = GL11C.glGetInteger(GL30C.GL_NUM_EXTENSIONS);
			for (int i = 0; i < n; i++) {
				set.add(GL30C.glGetStringi(GL11C.GL_EXTENSIONS, i));
			}
		} else {
			String all = GL11C.glGetString(GL11C.GL_EXTENSIONS);
			if (all != null) {
				for (String e : all.trim().split("\\s+")) {
					set.add(e);
				}
			}
		}
		return Collections.unmodifiableSet(set);
	}

	private static void add(List<String> missing, boolean has, String name) {
		if (!has) {
			missing.add(name);
		}
	}

	private static String yes(boolean b) {
		return b ? "yes" : "no";
	}
}
