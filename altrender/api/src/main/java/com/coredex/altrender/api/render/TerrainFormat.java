/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.render;

import java.util.List;

/**
 * Altrender's terrain vertices for a pipeline's own terrain programs (since 0.3.0, development.md 6.1/6.6). A program
 * that draws Altrender's terrain is built with {@link #ATTRIBUTES} (entry {@code i} at location {@code i}) and gets
 * {@link #VERTEX_PROLOGUE} in its vertex shader, after the {@code #version} and {@code #extension} lines: it declares
 * the inputs and defines {@code void ar_terrainInit()}, which fills
 * <ul>
 * <li>{@code vec4 ar_Vertex}: the position relative to the camera (with the game's modelview, which is the camera's
 * during the terrain passes, it gives the eye position, as the game's own {@code gl_ModelViewMatrix * gl_Vertex});</li>
 * <li>{@code vec4 ar_Color}: the vertex colour (biome tint and face shade; the light is not in it);</li>
 * <li>{@code vec4 ar_MultiTexCoord0}: the atlas coordinates;</li>
 * <li>{@code vec4 ar_MultiTexCoord1}: the light as modern lightmap coordinates (block, sky; 0–240);</li>
 * <li>{@code vec3 ar_Normal}: the face normal (model space).</li>
 * </ul>
 * The shader packs' own attributes are filled by name: {@code mc_Entity} (x: the pipeline's block id, from
 * {@link TerrainPipeline#blockId}; y: -1), {@code mc_midTexCoord} (the sprite's centre), {@code at_tangent} and
 * {@code at_midBlock} (the vertex to the block's centre in 1/64 blocks; w: the block's light emission).
 */
public final class TerrainFormat {

	/** Attribute names by location. */
	public static final List<String> ATTRIBUTES = List.of("ar_Pos", "ar_ColorIn", "ar_TexCoord", "ar_Light",
			"ar_NormalIn", "mc_Entity", "ar_RegionOffset", "ar_RegionInner", "mc_midTexCoord", "at_tangent",
			"at_midBlock");

	public static final String VERTEX_PROLOGUE = """
			in uvec4 ar_Pos;
			in vec4 ar_ColorIn;
			in vec2 ar_TexCoord;
			in vec2 ar_Light;
			in vec4 ar_NormalIn;
			in vec3 ar_RegionOffset;
			in vec3 ar_RegionInner;
			vec4 ar_Vertex;
			vec4 ar_Color;
			vec4 ar_MultiTexCoord0;
			vec4 ar_MultiTexCoord1;
			vec3 ar_Normal;
			void ar_terrainInit() {
				vec3 local = vec3(ar_Pos.xyz) * (1.0 / 2048.0) - 8.0;
				local = (local - 8.0) * 1.000001 + 8.0;
				uint section = ar_Pos.w;
				vec3 sectionOffset = vec3(float((section >> 5u) & 7u), float((section >> 3u) & 3u), float(section & 7u)) * 16.0;
				ar_Vertex = vec4(ar_RegionOffset + (ar_RegionInner + sectionOffset + local), 1.0);
				ar_Color = ar_ColorIn;
				ar_MultiTexCoord0 = vec4(ar_TexCoord, 0.0, 1.0);
				ar_MultiTexCoord1 = vec4(ar_Light * 255.0, 0.0, 1.0); // light × 16, stored as normalised bytes
				ar_Normal = ar_NormalIn.xyz;
			}
			""";

	private TerrainFormat() {
	}
}
