/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.coredex.altrender.api.render.TerrainFormat;

/**
 * A pack's terrain vertex shader on Altrender's terrain vertices (development.md 6.6): the built-in inputs it reads
 * become the values Altrender's prologue decodes ({@link TerrainFormat}; {@code ftransform()} transforms that position),
 * and its {@code main} runs after the decode.
 * The camera's matrices stay the game's built-ins: during the terrain passes they are the camera's. The lightmap
 * texture matrix ({@code gl_TextureMatrix[1]}) is the modern one, since the light coordinates are.
 */
final class TerrainSource {

	private static final Map<String, String> NAMES = Map.of(
			"gl_Vertex", "ar_Vertex",
			"gl_Color", "ar_Color",
			"gl_MultiTexCoord0", "ar_MultiTexCoord0",
			"gl_MultiTexCoord1", "ar_MultiTexCoord1",
			"gl_MultiTexCoord2", "ar_MultiTexCoord1",
			"gl_Normal", "ar_Normal",
			"gl_TextureMatrix", "ar_TextureMatrix",
			"ftransform", "ar_ftransform",
			"main", "ar_packMain");

	private static final Pattern HEADER = Pattern.compile("\\A(?:[ \\t]*#[ \\t]*(?:version|extension)[^\\n]*\\n)+");

	private TerrainSource() {
	}

	/** {@code prepared}: a vertex shader already through {@link SourcePrep}. */
	static String vertex(String prepared) {
		return vertex(prepared, false);
	}

	/**
	 * As {@link #vertex(String)}; {@code depthOutputs} also passes the atlas coordinates and the vertex alpha on
	 * ({@code ar_DepthUv}, {@code ar_DepthAlpha}) for a depth-only fragment shader's alpha test.
	 */
	static String vertex(String prepared, boolean depthOutputs) {
		String body = GlslRename.rename(prepared, NAMES);
		Matcher m = HEADER.matcher(body);
		int at = m.find() ? m.end() : 0;
		return body.substring(0, at) + TerrainFormat.VERTEX_PROLOGUE + """
				mat4 ar_TextureMatrix[8];
				vec4 ar_ftransform() {
					return gl_ModelViewProjectionMatrix * ar_Vertex;
				}
				""" + (depthOutputs ? "out vec2 ar_DepthUv;\nout float ar_DepthAlpha;\n" : "") + body.substring(at) + """

				void main() {
					ar_terrainInit();
					for (int i = 0; i < 8; i++) {
						ar_TextureMatrix[i] = gl_TextureMatrix[i];
					}
					// The modern lightmap matrix: (uv + 8) / 256, for light coordinates 0..240.
					ar_TextureMatrix[1] = mat4(1.0 / 256.0, 0.0, 0.0, 0.0, 0.0, 1.0 / 256.0, 0.0, 0.0,
							0.0, 0.0, 1.0 / 256.0, 0.0, 8.0 / 256.0, 8.0 / 256.0, 8.0 / 256.0, 1.0);
					ar_packMain();
				""" + (depthOutputs ? """
					ar_DepthUv = ar_TexCoord;
					ar_DepthAlpha = ar_ColorIn.a;
				""" : "") + """
				}
				""";
	}
}
