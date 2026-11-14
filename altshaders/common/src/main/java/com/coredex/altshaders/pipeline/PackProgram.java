/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.api.PendingProgram;
import com.coredex.altrender.api.ShaderPrograms;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuFramebuffer;
import com.coredex.altshaders.Log;
import com.coredex.altshaders.pack.programs.ComputeSource;
import com.coredex.altshaders.pack.programs.ProgramSource;
import com.coredex.altshaders.pack.properties.PackRenderTargetDirectives;
import com.coredex.altshaders.pack.properties.ProgramDirectives;

/**
 * One of a pack's programs, built on Altrender's context (development.md 6.4): its directives, its samplers (each
 * given a texture unit once linked) and, per flip state of the buffers it writes, a framebuffer.
 */
final class PackProgram {

	/** What a sampler reads. */
	enum Source {
		/** The game's own texture on unit 0 (gbuffers). */
		GAME,
		LIGHTMAP, NORMALS, SPECULAR, NOISE, COLOR, DEPTH,
		/** One of the pack's textures or images ({@link PackResources}). */
		CUSTOM,
		/** shadowtex0/1: {@code index} = which × 2 + 1 for a comparing ({@code sampler2DShadow}) sampler. */
		SHADOW_DEPTH,
		/** shadowcolor{@code index}. */
		SHADOW_COLOR,
		/** The smoothed centre depth ({@link CenterDepth}). */
		CENTER_DEPTH,
		/** Nothing the pack gives: a black texture, so it never reads another program's leftovers. */
		NONE
	}

	/** A sampler uniform and what goes on its unit. */
	record Sampler(String name, int unit, Source source, int index, PackResources.TexRef custom) {
	}

	/** An image uniform and the pack image on its unit. */
	/**
	 * An image the program declares: one of the pack's ({@code image}), or a colour buffer as Iris binds them
	 * ({@code colorimgN}: {@code color} = N, {@code shadowcolorimgN}: {@code shadowColor} = N; the side read now).
	 */
	record ImageUnit(String name, int unit, PackResources.Image image, int color, int shadowColor) {

		ImageUnit(String name, int unit, PackResources.Image image) {
			this(name, unit, image, -1, -1);
		}
	}

	/** The texture stage of the program (which of the pack's stage textures it reads). */
	com.coredex.altshaders.pack.texture.TextureStage stage = com.coredex.altshaders.pack.texture.TextureStage.COMPOSITE_AND_FINAL;
	final List<ImageUnit> images = new ArrayList<>();

	final String name;
	final ProgramDirectives directives;
	final boolean gbuffers;
	final boolean compute;
	private final PendingProgram pending;
	private boolean linked;
	private int program;
	final List<Sampler> samplers = new ArrayList<>();
	/** Uniform locations by name, looked up once (-1: the program doesn't use it). */
	private final Map<String, Integer> locations = new HashMap<>();
	private final Map<Integer, GpuFramebuffer> framebuffers = new HashMap<>();

	private PackProgram(String name, ProgramDirectives directives, boolean gbuffers, boolean compute,
			PendingProgram pending) {
		this.name = name;
		this.directives = directives;
		this.gbuffers = gbuffers;
		this.compute = compute;
		this.pending = pending;
	}

	static PackProgram build(ShaderPrograms builder, ProgramSource source, boolean gbuffers) {
		return build(builder, source, gbuffers, Map.of());
	}

	/** {@code renames}: the stage's custom-texture sampler renames (Iris's texture patching). */
	static PackProgram build(ShaderPrograms builder, ProgramSource source, boolean gbuffers,
			Map<String, String> renames) {
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		source.getVertexSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.VERTEX,
				prep(s, renames, SourcePrep.Stage.VERTEX)));
		source.getTessControlSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.TESS_CONTROL,
				prep(s, renames, SourcePrep.Stage.TESS_CONTROL)));
		source.getTessEvalSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.TESS_EVALUATION,
				prep(s, renames, SourcePrep.Stage.TESS_EVALUATION)));
		source.getGeometrySource().ifPresent(s -> stages.put(ShaderPrograms.Stage.GEOMETRY,
				prep(s, renames, SourcePrep.Stage.GEOMETRY)));
		source.getFragmentSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.FRAGMENT, gbuffers
				? SourcePrep.alphaTest(prep(s, renames, SourcePrep.Stage.FRAGMENT))
				: prep(s, renames, SourcePrep.Stage.FRAGMENT)));
		PendingProgram p = builder.build("altshaders " + source.getName(), stages, List.of(), List.of());
		return new PackProgram(source.getName(), source.getDirectives(), gbuffers, false, p);
	}

	private static String prep(String src, Map<String, String> renames, SourcePrep.Stage stage) {
		String s = SourcePrep.prepare(src, stage);
		return renames.isEmpty() ? s : GlslRename.rename(s, renames);
	}

	/** The source of a compute program, for its work group size directives. */
	ComputeSource computeSource;

	/**
	 * A gbuffers program for Altrender's terrain (development.md 6.6): its vertex shader reads Altrender's terrain
	 * vertices ({@link TerrainSource}), its attributes are the terrain format's.
	 */
	static PackProgram buildTerrain(ShaderPrograms builder, ProgramSource source) {
		return buildTerrain(builder, source, true);
	}

	/**
	 * As above; without {@code alphaTest} the fragment shader keeps no alpha test of AltShaders' (for quads whose
	 * texture has no see-through texels: the early depth test stays on).
	 */
	static PackProgram buildTerrain(ShaderPrograms builder, ProgramSource source, boolean alphaTest) {
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		source.getVertexSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.VERTEX,
				TerrainSource.vertex(SourcePrep.prepare(s, SourcePrep.Stage.VERTEX))));
		source.getTessControlSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.TESS_CONTROL,
				SourcePrep.prepare(s, SourcePrep.Stage.TESS_CONTROL)));
		source.getTessEvalSource().ifPresent(s -> stages.put(ShaderPrograms.Stage.TESS_EVALUATION,
				SourcePrep.prepare(s, SourcePrep.Stage.TESS_EVALUATION)));
		source.getGeometrySource().ifPresent(s -> stages.put(ShaderPrograms.Stage.GEOMETRY,
				SourcePrep.prepare(s, SourcePrep.Stage.GEOMETRY)));
		source.getFragmentSource().ifPresent(s -> {
			String prepared = SourcePrep.prepare(s, SourcePrep.Stage.FRAGMENT);
			stages.put(ShaderPrograms.Stage.FRAGMENT, alphaTest ? SourcePrep.alphaTest(prepared) : prepared);
		});
		String suffix = alphaTest ? " (terrain)" : " (terrain, solid)";
		PendingProgram p = builder.build("altshaders " + source.getName() + suffix, stages,
				com.coredex.altrender.api.render.TerrainFormat.ATTRIBUTES, List.of());
		return new PackProgram(source.getName() + suffix, source.getDirectives(), true, false, p);
	}

	/**
	 * The water program's depth-only twin for the translucent terrain's depth draw (old versions' fancy water draws the
	 * layer for depth first, colour writes off): the pack's own vertex shader (so moving water keeps its positions)
	 * and a fragment shader that only does the alpha test. Null when the pack's program has geometry or tessellation
	 * stages (its full program draws then).
	 */
	static PackProgram buildTerrainDepth(ShaderPrograms builder, ProgramSource source) {
		if (source.getGeometrySource().isPresent() || source.getTessControlSource().isPresent()
				|| source.getTessEvalSource().isPresent() || source.getVertexSource().isEmpty()) {
			return null;
		}
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		stages.put(ShaderPrograms.Stage.VERTEX, TerrainSource.vertex(
				SourcePrep.prepare(source.getVertexSource().get(), SourcePrep.Stage.VERTEX), true));
		stages.put(ShaderPrograms.Stage.FRAGMENT, """
				#version 330 compatibility
				uniform sampler2D gtexture;
				in vec2 ar_DepthUv;
				in float ar_DepthAlpha;
				void main() {
					if (texture(gtexture, ar_DepthUv).a * ar_DepthAlpha < 0.1) {
						discard;
					}
				}
				""");
		PendingProgram p = builder.build("altshaders " + source.getName() + " (terrain, depth)", stages,
				com.coredex.altrender.api.render.TerrainFormat.ATTRIBUTES, List.of());
		return new PackProgram(source.getName() + " (terrain, depth)", source.getDirectives(), true, false, p);
	}

	/** One target to the screen ({@code -Daltshaders.debug.view}): colour as is, depth as grey. */
	static PackProgram debugView(ShaderPrograms builder) {
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		stages.put(ShaderPrograms.Stage.VERTEX, """
				#version 330 compatibility
				out vec2 uv;
				void main() {
					gl_Position = ftransform();
					uv = gl_MultiTexCoord0.st;
				}
				""");
		stages.put(ShaderPrograms.Stage.FRAGMENT, """
				#version 330 compatibility
				uniform sampler2D colortex0;
				uniform int altshadersDepth;
				in vec2 uv;
				void main() {
					vec4 c = texture(colortex0, uv);
					gl_FragColor = altshadersDepth == 1 ? vec4(vec3(pow(c.r, 64.0)), 1.0)
							: altshadersDepth == 2 ? vec4(vec3(c.r), 1.0) : vec4(c.rgb, 1.0);
				}
				""");
		PendingProgram p = builder.build("altshaders debug view", stages, List.of(), List.of());
		return new PackProgram("debug view", null, false, false, p);
	}

	/** One of AltShaders' own full-screen programs (not a pack's). */
	static PackProgram internal(String name, PendingProgram p) {
		return new PackProgram(name, null, false, false, p);
	}

	/** colortex0 to the screen, for packs without a {@code final} program (as Iris). */
	static PackProgram blit(ShaderPrograms builder) {
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		stages.put(ShaderPrograms.Stage.VERTEX, """
				#version 330 compatibility
				out vec2 uv;
				void main() {
					gl_Position = ftransform();
					uv = gl_MultiTexCoord0.st;
				}
				""");
		stages.put(ShaderPrograms.Stage.FRAGMENT, """
				#version 330 compatibility
				uniform sampler2D colortex0;
				in vec2 uv;
				void main() {
					gl_FragColor = texture(colortex0, uv);
				}
				""");
		PendingProgram p = builder.build("altshaders final (copy)", stages, List.of(), List.of());
		return new PackProgram("final", null, false, false, p);
	}

	static PackProgram build(ShaderPrograms builder, ComputeSource source, Map<String, String> renames) {
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		stages.put(ShaderPrograms.Stage.COMPUTE, prep(source.getSource().orElseThrow(), renames, SourcePrep.Stage.COMPUTE));
		PendingProgram p = builder.build("altshaders " + source.getName(), stages, List.of(), List.of());
		PackProgram program = new PackProgram(source.getName(), null, false, true, p);
		program.computeSource = source;
		return program;
	}

	boolean done() {
		return pending.done();
	}

	/** Another program whose sampler and image units this one shares by name (set before it links), or null. */
	PackProgram unitsLike;

	/** Whether it built; the first call after the build sets up its samplers. */
	boolean ok(Gpu gpu) {
		return ok(gpu, null);
	}

	boolean ok(Gpu gpu, PackResources resources) {
		if (!linked) {
			linked = true;
			if (pending.ok()) {
				program = pending.program();
				findSamplers(gpu, resources);
			} else {
				Log.warn("shader pack program " + name + " failed to build:\n" + pending.log());
			}
		}
		return program != 0;
	}

	String log() {
		return pending.log();
	}

	int program() {
		return program;
	}

	private static boolean isSampler(int type) {
		return type >= 0x8B5D && type <= 0x8B64 // sampler1D … sampler2DRectShadow
				|| type >= 0x8DC0 && type <= 0x8DD8 // array, buffer, cube shadow, integer samplers
				|| type >= 0x900C && type <= 0x900F // cube arrays
				|| type >= 0x9108 && type <= 0x910D; // multisample
	}

	private static boolean isImage(int type) {
		return type >= 0x904C && type <= 0x906C;
	}

	private static boolean isShadowSampler(int type) {
		return type == 0x8B61 || type == 0x8B62 || type == 0x8B64 || type == 0x8DC3 || type == 0x8DC4 || type == 0x8DC5;
	}

	/**
	 * Every sampler and image the program declares, by name, to a unit: the gbuffers' fixed four (the game's texture
	 * on 0, lightmap, normals, specular), then the pack's textures for its stage, its named textures and images,
	 * {@code noisetex}, the colour and depth targets, the shadow maps (6.7), and a black texture for anything else.
	 */
	private void findSamplers(Gpu gpu, PackResources res) {
		// A program drawn in turn with another under the same bindings (the terrain's solid and cut-out programs)
		// takes that one's unit for each name they share, and units past its for the rest.
		int[] next = {gbuffers ? 4 : 0, 0};
		if (unitsLike != null) {
			for (Sampler s : unitsLike.samplers) {
				next[0] = Math.max(next[0], s.unit() + 1);
			}
			for (ImageUnit i : unitsLike.images) {
				next[1] = Math.max(next[1], i.unit() + 1);
			}
		}
		java.util.function.ToIntFunction<String> unitFor = name -> {
			if (unitsLike != null) {
				for (Sampler s : unitsLike.samplers) {
					if (s.name().equals(name)) {
						return s.unit();
					}
				}
			}
			return next[0]++;
		};
		java.util.function.ToIntFunction<String> imageFor = name -> {
			if (unitsLike != null) {
				for (ImageUnit i : unitsLike.images) {
					if (i.name().equals(name)) {
						return i.unit();
					}
				}
			}
			return next[1]++;
		};
		for (Map.Entry<String, Integer> e : gpu.activeUniforms(program).entrySet()) {
			String n = e.getKey();
			int type = e.getValue();
			if (isImage(type)) {
				PackResources.Image image = res != null ? res.image(n) : null;
				if (image != null) {
					images.add(new ImageUnit(n, imageFor.applyAsInt(n), image));
				} else {
					int color = numbered(n, "colorimg");
					int shadowColor = numbered(n, "shadowcolorimg");
					if (color >= 0 && color < com.coredex.altshaders.pack.properties.PackRenderTargetDirectives.MAX_COLOR_BUFFERS
							|| shadowColor >= 0 && shadowColor < 8) {
						images.add(new ImageUnit(n, imageFor.applyAsInt(n), null, color, shadowColor));
					}
				}
				continue;
			}
			if (!isSampler(type)) {
				continue;
			}
			if (gbuffers && (n.equals("texture") || n.equals("gtexture") || n.equals("tex"))) {
				samplers.add(new Sampler(n, 0, Source.GAME, 0, null));
				continue;
			}
			if (gbuffers && n.equals("lightmap")) {
				samplers.add(new Sampler(n, 1, Source.LIGHTMAP, 0, null));
				continue;
			}
			if (gbuffers && n.equals("normals")) {
				samplers.add(new Sampler(n, 2, Source.NORMALS, 0, null));
				continue;
			}
			if (gbuffers && n.equals("specular")) {
				samplers.add(new Sampler(n, 3, Source.SPECULAR, 0, null));
				continue;
			}
			if (n.equals(CenterDepth.SAMPLER)) {
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.CENTER_DEPTH, 0, null));
				continue;
			}
			PackResources.TexRef custom = res != null ? res.sampler(stage, n) : null;
			if (custom != null) {
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.CUSTOM, 0, custom));
				continue;
			}
			if (n.equals("noisetex")) {
				PackResources.TexRef noise = res != null ? res.noise() : null;
				samplers.add(noise != null ? new Sampler(n, unitFor.applyAsInt(n), Source.CUSTOM, 0, noise)
						: new Sampler(n, unitFor.applyAsInt(n), Source.NOISE, 0, null));
				continue;
			}
			int color = colorIndex(n);
			if (color >= 0) {
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.COLOR, color, null));
				continue;
			}
			int depth = switch (n) {
				case "depthtex0", "gdepthtex" -> 0;
				case "depthtex1" -> 1;
				case "depthtex2" -> 2;
				default -> -1;
			};
			if (depth >= 0) {
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.DEPTH, depth, null));
				continue;
			}
			if (n.equals("shadowtex0") || n.equals("shadowtex1") || n.equals("shadow") || n.equals("watershadow")) {
				int which = n.equals("shadowtex1") ? 1 : 0;
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.SHADOW_DEPTH, which * 2 + (isShadowSampler(type) ? 1 : 0), null));
				continue;
			}
			if (n.startsWith("shadowcolor")) {
				int which = 0;
				try {
					which = n.length() > 11 ? Integer.parseInt(n.substring(11)) : 0;
				} catch (NumberFormatException bad) {
					which = 0;
				}
				samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.SHADOW_COLOR, which, null));
				continue;
			}
			samplers.add(new Sampler(n, unitFor.applyAsInt(n), Source.NONE, 0, null));
		}
		gpu.begin("altshaders samplers " + name);
		gpu.useProgram(program);
		for (Sampler s : samplers) {
			gpu.uniform1i(gpu.uniformLocation(program, s.name()), s.unit());
		}
		for (ImageUnit i : images) {
			gpu.uniform1i(gpu.uniformLocation(program, i.name()), i.unit());
		}
		gpu.end();
	}

	/** N for {@code <prefix>N}, else -1. */
	private static int numbered(String n, String prefix) {
		if (!n.startsWith(prefix) || n.length() == prefix.length()) {
			return -1;
		}
		for (int k = prefix.length(); k < n.length(); k++) {
			if (!Character.isDigit(n.charAt(k))) {
				return -1;
			}
		}
		try {
			return Integer.parseInt(n.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** colortex{@code N} or its legacy name's index, else -1. */
	private static int colorIndex(String n) {
		if (n.startsWith("colortex")) {
			try {
				int i = Integer.parseInt(n.substring(8));
				return i >= 0 && i < PackRenderTargetDirectives.MAX_COLOR_BUFFERS ? i : -1;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(n);
	}

	int location(Gpu gpu, String uniform) {
		Integer l = locations.get(uniform);
		if (l == null) {
			l = gpu.uniformLocation(program, uniform);
			locations.put(uniform, l);
		}
		return l;
	}

	/** {@code -Daltshaders.debug.no74}: 7.4's savings off (every uniform sent, mipmaps made at every use), to time them. */
	static final boolean NO_74 = Boolean.getBoolean("altshaders.debug.no74");

	/** Per uniform location: the last value sent (four 32-bit words), and whether one was (7.4). */
	private int[] sent = new int[0];
	private boolean[] sentAny = new boolean[0];

	/**
	 * Whether {@code a, b, c, d} (raw bits) differ from what was last sent to {@code location} of this program, which
	 * then counts as sent. AltShaders is the only one to set this program's plain uniforms, so an unchanged value
	 * needn't go to the driver again. Locations past 4095 are always sent.
	 */
	boolean changed(int location, int a, int b, int c, int d) {
		if (location < 0 || location > 4095 || NO_74) {
			return true;
		}
		if (location >= sentAny.length) {
			int n = Math.min(4096, Math.max(location + 1, sentAny.length * 2));
			sent = java.util.Arrays.copyOf(sent, n * 4);
			sentAny = java.util.Arrays.copyOf(sentAny, n);
		}
		int k = location * 4;
		if (sentAny[location] && sent[k] == a && sent[k + 1] == b && sent[k + 2] == c && sent[k + 3] == d) {
			return false;
		}
		sentAny[location] = true;
		sent[k] = a;
		sent[k + 1] = b;
		sent[k + 2] = c;
		sent[k + 3] = d;
		return true;
	}

	boolean changed(int location, float a, float b, float c, float d) {
		return changed(location, Float.floatToRawIntBits(a), Float.floatToRawIntBits(b), Float.floatToRawIntBits(c),
				Float.floatToRawIntBits(d));
	}

	/** The colour buffers it writes, in fragment output order. */
	int[] drawBuffers() {
		return directives.getDrawBuffers();
	}

	/**
	 * The framebuffer writing its draw buffers' write sides ({@code gbuffers}: the current side, composites: the
	 * other), with {@code depth} attached; made once per flip state.
	 */
	GpuFramebuffer framebuffer(Gpu gpu, RenderTargets targets, boolean writeCurrent,
			com.coredex.altrender.api.gpu.GpuTexture depth) {
		int[] buffers = drawBuffers();
		int key = 0;
		for (int b : buffers) {
			RenderTargets.Target t = targets.get(b);
			int side = writeCurrent ? t.current : t.current ^ 1;
			key |= side << b;
		}
		GpuFramebuffer fb = framebuffers.get(key);
		if (fb == null) {
			fb = gpu.createFramebuffer("altshaders " + name);
			int[] attachments = new int[buffers.length];
			for (int i = 0; i < buffers.length; i++) {
				RenderTargets.Target t = targets.get(buffers[i]);
				fb.color(i, writeCurrent ? t.read() : t.write(), 0);
				attachments[i] = i;
			}
			if (depth != null) {
				fb.depth(depth);
			}
			fb.drawBuffers(attachments);
			String status = fb.check();
			if (status != null) {
				Log.warn("shader pack program " + name + ": framebuffer incomplete (" + status + ")");
			}
			framebuffers.put(key, fb);
		}
		return fb;
	}

	/** Framebuffers again on next use (after a resize, which reallocates the targets). */
	void dropFramebuffers() {
		for (GpuFramebuffer fb : framebuffers.values()) {
			fb.delete();
		}
		framebuffers.clear();
	}

	void delete() {
		dropFramebuffers();
		pending.delete();
		program = 0;
	}
}
