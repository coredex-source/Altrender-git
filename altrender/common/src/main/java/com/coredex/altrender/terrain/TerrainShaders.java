/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

/**
 * GLSL 3.30 terrain shaders (development.md 2.4) reproducing the game's fixed-function terrain draw:
 * <ul>
 * <li>position: the game's chain per section display list (camera modelview, then the 1024-block region minus the
 * camera, the section's offset in that region, and a 1.000001 scale about the section centre that hides cracks);</li>
 * <li>colour: texture × vertex colour (GL_MODULATE; the light is in the vertex colour);</li>
 * <li>fog: as the fixed-function fog the tracker reports (linear/exp/exp2; eye-plane or eye-radial distance, the latter
 * with {@code NV_fog_distance}), from a per-vertex distance as the driver computes it;</li>
 * <li>alpha test: not here; a compatibility context still applies the game's fixed-function alpha test after the
 * fragment shader.</li>
 * </ul>
 */
public final class TerrainShaders {

	/** The per-pass block both stages read (std140, {@link #FRAME_BYTES} bytes; development.md 4.4). */
	private static final String FRAME_BLOCK = """
			layout(std140) uniform TerrainFrame {
				mat4 u_ModelView;
				mat4 u_Projection;
				vec4 u_FogColor;
				// start, end, density, unused
				vec4 u_Fog;
				// mode (0 off, 1 linear, 2 exp, 3 exp2), radial distance, unused, unused
				ivec4 u_FogFlags;
			};
			""";

	/** Bytes of {@code TerrainFrame}. */
	public static final int FRAME_BYTES = 176;

	public static final String VERTEX = """
			#version 330 core
			in uvec4 a_Pos;
			in vec4 a_Color;
			in vec2 a_TexCoord;
			// per draw: (1024-block base of the region) - camera, as the game computes it in float
			in vec3 a_RegionOffset;
			// per draw: the region's origin within its 1024-block base
			in vec3 a_RegionInner;
			""" + FRAME_BLOCK + """

			out vec4 v_Color;
			out vec2 v_TexCoord;
			out float v_FogDistance;

			void main() {
				vec3 local = vec3(a_Pos.xyz) * (1.0 / 2048.0) - 8.0;
				local = (local - 8.0) * 1.000001 + 8.0;
				uint section = a_Pos.w;
				vec3 sectionOffset = vec3(float((section >> 5u) & 7u), float((section >> 3u) & 3u), float(section & 7u)) * 16.0;
				vec3 position = a_RegionOffset + (a_RegionInner + sectionOffset + local);
				vec4 eye = u_ModelView * vec4(position, 1.0);
				gl_Position = u_Projection * eye;
				v_FogDistance = u_FogFlags.y != 0 ? length(eye.xyz) : abs(eye.z);
				v_Color = a_Color;
				v_TexCoord = a_TexCoord;
			}
			""";

	public static final String FRAGMENT = """
			#version 330 core
			in vec4 v_Color;
			in vec2 v_TexCoord;
			in float v_FogDistance;

			uniform sampler2D u_Texture;
			""" + FRAME_BLOCK + """

			out vec4 fragColor;

			void main() {
				vec4 color = texture(u_Texture, v_TexCoord) * v_Color;
				int fogMode = u_FogFlags.x;
				if (fogMode != 0) {
					float f;
					if (fogMode == 1) {
						f = (u_Fog.y - v_FogDistance) / (u_Fog.y - u_Fog.x);
					} else if (fogMode == 2) {
						f = exp(-u_Fog.z * v_FogDistance);
					} else {
						float d = u_Fog.z * v_FogDistance;
						f = exp(-d * d);
					}
					color.rgb = mix(u_FogColor.rgb, color.rgb, clamp(f, 0.0, 1.0));
				}
				fragColor = color;
			}
			""";

	private TerrainShaders() {
	}
}
