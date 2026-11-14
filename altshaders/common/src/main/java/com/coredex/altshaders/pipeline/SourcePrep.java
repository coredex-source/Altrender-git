/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A preprocessed pack source made ready for the compatibility context (development.md 6.4), doing what Iris's
 * transformer does that packs rely on, without parsing the GLSL:
 * <ul>
 * <li>the version becomes at least {@code 330 compatibility} (Iris raises every non-compute shader to 330; packs
 * written for {@code #version 120} use 1.30 features under it), and the compatibility profile keeps every
 * {@code gl_*} built-in;</li>
 * <li>{@code attribute} and {@code varying} become {@code in}/{@code out} (Mesa refuses {@code flat varying});</li>
 * <li>Iris's core-mode inputs ({@code projectionMatrix}, {@code vaPosition}, …) become the built-ins they stand for,
 * where the program uses them;</li>
 * <li>{@code mc_chunkFade} (Iris's chunk fade-in, the FADE_VARIABLE feature) is 1: chunks are fully there;</li>
 * <li>{@code uniform float centerDepthSmooth} becomes a read of the smoothed depth's 1×1 texture ({@link CenterDepth},
 * as Iris).</li>
 * </ul>
 */
final class SourcePrep {

	enum Stage {
		VERTEX, TESS_CONTROL, TESS_EVALUATION, GEOMETRY, FRAGMENT, COMPUTE
	}

	private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version[ \\t]+(\\d+)[ \\t]*(\\w+)?[^\\n]*$");
	private static final Pattern HEADER = Pattern.compile("\\A(?:[ \\t]*#[ \\t]*(?:version|extension)[^\\n]*\\n)+");

	/** Iris's core-mode inputs and the compatibility built-ins they are. */
	private static final Map<String, String> CORE = new LinkedHashMap<>();

	static {
		CORE.put("projectionMatrix", "gl_ProjectionMatrix");
		CORE.put("modelViewMatrix", "gl_ModelViewMatrix");
		CORE.put("normalMatrix", "gl_NormalMatrix");
		CORE.put("vaPosition", "gl_Vertex.xyz");
		CORE.put("vaColor", "gl_Color");
		CORE.put("vaUV0", "gl_MultiTexCoord0.xy");
		CORE.put("vaUV2", "ivec2(gl_MultiTexCoord1.xy)");
		CORE.put("vaNormal", "gl_Normal");
		CORE.put("chunkOffset", "vec3(0.0)");
	}

	private SourcePrep() {
	}

	static String prepare(String source, boolean compute) {
		return prepare(source, compute ? Stage.COMPUTE : Stage.VERTEX);
	}

	static String prepare(String source, Stage stage) {
		boolean compute = stage == Stage.COMPUTE;
		String s;
		Matcher m = VERSION.matcher(source);
		if (!m.find()) {
			// Iris refuses a source without #version; drivers would read it as 1.10. Assume the oldest packs' 1.20.
			s = (compute ? "#version 430\n" : "#version 330 compatibility\n") + source;
		} else {
			int version = Integer.parseInt(m.group(1));
			String line = compute ? "#version " + Math.max(version, 430)
					: "#version " + Math.max(version, 330) + " compatibility";
			s = source.substring(0, m.start()) + line + source.substring(m.end());
		}
		s = centerDepth(s);
		if (compute) {
			return s;
		}
		Map<String, String> names = new LinkedHashMap<>();
		if (stage == Stage.VERTEX) {
			names.put("attribute", "in");
			names.put("varying", "out");
		} else if (stage == Stage.FRAGMENT) {
			names.put("varying", "in");
		}
		for (Map.Entry<String, String> e : CORE.entrySet()) {
			String name = e.getKey();
			if (!Pattern.compile("\\b" + name + "\\b").matcher(s).find()) {
				continue;
			}
			// Declared as an input (uniform/in/attribute): the declaration goes, the uses become the built-in.
			Pattern input = Pattern.compile("(?m)^[ \\t]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:uniform|in|attribute)\\s+\\w+\\s+"
					+ name + "\\s*;[ \\t]*$");
			Matcher d = input.matcher(s);
			if (d.find()) {
				s = d.replaceAll("");
				names.put(name, e.getValue());
			} else if (!Pattern.compile("\\b\\w+\\s+" + name + "\\s*[;=,)\\[]").matcher(s).find()) {
				names.put(name, e.getValue()); // used but never declared: Iris supplies it
			}
		}
		if (!names.isEmpty()) {
			s = GlslRename.rename(s, names);
		}
		if (stage == Stage.VERTEX && s.contains("mc_chunkFade")
				&& !Pattern.compile("\\b\\w+\\s+mc_chunkFade\\s*[;=]").matcher(s).find()) {
			Matcher h = HEADER.matcher(s);
			int at = h.find() ? h.end() : 0;
			s = s.substring(0, at) + "float mc_chunkFade = 1.0;\n" + s.substring(at);
		}
		return s;
	}

	private static final Pattern CENTER_DEPTH = Pattern.compile(
			"(?m)^[ \\t]*uniform\\s+(?:(?:highp|mediump|lowp)\\s+)?float\\s+centerDepthSmooth\\s*;");

	private static final Pattern FLOAT_CENTER_DEPTH = Pattern.compile("\\bfloat\\s+centerDepthSmooth\\b");

	/**
	 * {@code centerDepthSmooth} declared as a uniform: the GPU-smoothed value's texture instead (not where the source
	 * also declares its own variable of that name: the uniform's constant stays there).
	 */
	private static String centerDepth(String s) {
		Matcher d = CENTER_DEPTH.matcher(s);
		if (!d.find()) {
			return s;
		}
		long uniforms = CENTER_DEPTH.matcher(s).results().count();
		if (FLOAT_CENTER_DEPTH.matcher(s).results().count() > uniforms) {
			return s;
		}
		s = d.replaceAll("uniform sampler2D " + CenterDepth.SAMPLER + ";");
		return GlslRename.rename(s, Map.of("centerDepthSmooth", "texture(" + CenterDepth.SAMPLER + ", vec2(0.5)).r"));
	}

	private static final Pattern FRAG_DATA0 = Pattern.compile("\\bgl_FragData\\s*\\[\\s*0\\s*\\]");
	private static final Pattern FRAG_COLOR = Pattern.compile("\\bgl_FragColor\\b");

	/**
	 * Iris's alpha test for a gbuffers fragment shader that writes {@code gl_FragData[0]} or {@code gl_FragColor}
	 * (packs with their own declared outputs test alpha themselves): after the pack's {@code main}, the first
	 * output's alpha must be greater than {@code altshaders_alphaTestRef} (the pack's {@code alphaTest.*} or the
	 * game's own alpha test for the draw; -1: off).
	 */
	static String alphaTest(String prepared) {
		String out = FRAG_DATA0.matcher(prepared).find() ? "gl_FragData[0]"
				: FRAG_COLOR.matcher(prepared).find() ? "gl_FragColor" : null;
		if (out == null) {
			return prepared;
		}
		String body = GlslRename.rename(prepared, Map.of("main", "ar_packMain"));
		Matcher h = HEADER.matcher(body);
		int at = h.find() ? h.end() : 0;
		return body.substring(0, at) + "uniform float altshaders_alphaTestRef;\n" + body.substring(at) + """

				void main() {
					ar_packMain();
					if (!(%s.a > altshaders_alphaTestRef)) {
						discard;
					}
				}
				""".formatted(out);
	}
}
