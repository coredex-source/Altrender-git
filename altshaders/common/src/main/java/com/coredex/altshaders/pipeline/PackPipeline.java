/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.ShaderPrograms;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuFramebuffer;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;
import com.coredex.altrender.api.gpu.TimedPass;
import com.coredex.altrender.api.render.FixedFunction;
import com.coredex.altrender.api.render.RenderPhase;
import com.coredex.altshaders.Log;
import com.coredex.altshaders.pack.helpers.Vector4f;
import com.coredex.altshaders.pack.loading.ProgramArrayId;
import com.coredex.altshaders.pack.loading.ProgramGroup;
import com.coredex.altshaders.pack.loading.ProgramId;
import com.coredex.altshaders.pack.programs.ProgramFallbackResolver;
import com.coredex.altshaders.pack.programs.ProgramSet;
import com.coredex.altshaders.pack.programs.ProgramSource;
import com.coredex.altshaders.pack.properties.PackRenderTargetDirectives;
import com.coredex.altshaders.pack.texture.TextureStage;

/**
 * A shader pack's pipeline for one dimension's program set (development.md 6.4): the game's draws go into the pack's
 * gbuffers with the program for each phase, deferred passes run after the opaque world, composite passes after the
 * translucent one, and the final pass puts the picture on the screen. Everything GL goes through the API's
 * {@link Gpu}.
 */
final class PackPipeline {

	private static final int GL_RGBA8 = 0x8058;
	private static final int GL_RGB8 = 0x8051;
	private static final int GL_RGBA = 0x1908;
	private static final int GL_RGB = 0x1907;
	private static final int GL_UNSIGNED_BYTE = 0x1401;
	private static final int GL_LINEAR = 0x2601;
	private static final int GL_REPEAT = 0x2901;

	/**
	 * Whether b1.7.3's fancy water draws its depth before its colour under a pack (so only the nearest translucent
	 * surface takes colour): yes. {@code -Daltshaders.debug.skipWaterDepthDraw}: the water drawn once, each surface
	 * blending back to front as on modern versions; no faster with Photon (development.md 7.3: the saved draw comes
	 * back as more water shading), so not the default.
	 */
	static final boolean WATER_DEPTH_DRAW = !Boolean.getBoolean("altshaders.debug.skipWaterDepthDraw");

	/** The program the game's draws use in each phase (Iris's choice; fallbacks resolve the rest). */
	private static final Map<RenderPhase, ProgramId> PHASE_PROGRAMS = new EnumMap<>(RenderPhase.class);

	static {
		PHASE_PROGRAMS.put(RenderPhase.NONE, ProgramId.Textured);
		PHASE_PROGRAMS.put(RenderPhase.SKY, ProgramId.SkyBasic);
		PHASE_PROGRAMS.put(RenderPhase.SKY_TEXTURED, ProgramId.SkyTextured);
		PHASE_PROGRAMS.put(RenderPhase.TERRAIN_SOLID, ProgramId.TerrainSolid);
		PHASE_PROGRAMS.put(RenderPhase.TERRAIN_CUTOUT, ProgramId.TerrainCutout);
		PHASE_PROGRAMS.put(RenderPhase.TERRAIN_TRANSLUCENT, ProgramId.Water);
		PHASE_PROGRAMS.put(RenderPhase.ENTITIES, ProgramId.Entities);
		PHASE_PROGRAMS.put(RenderPhase.BLOCK_ENTITIES, ProgramId.Block);
		PHASE_PROGRAMS.put(RenderPhase.DESTROY, ProgramId.DamagedBlock);
		PHASE_PROGRAMS.put(RenderPhase.OUTLINE, ProgramId.Line);
		PHASE_PROGRAMS.put(RenderPhase.PARTICLES, ProgramId.Particles);
		PHASE_PROGRAMS.put(RenderPhase.RAIN_SNOW, ProgramId.Weather);
		PHASE_PROGRAMS.put(RenderPhase.CLOUDS, ProgramId.Clouds);
		PHASE_PROGRAMS.put(RenderPhase.HAND_SOLID, ProgramId.Hand);
		PHASE_PROGRAMS.put(RenderPhase.HAND_TRANSLUCENT, ProgramId.HandWater);
	}

	private final Gpu gpu;
	private final ProgramSet set;
	private final RenderTargets targets;
	private final Uniforms uniforms;
	/** Each gbuffers program by id, after fallbacks (several ids can share one program). */
	private final Map<ProgramId, PackProgram> gbuffers = new EnumMap<>(ProgramId.class);
	private final List<PackProgram> all = new ArrayList<>();
	/** The entity or block entity being drawn (pack ids from entity.properties/block.properties; -1: none). */
	private final Map<com.coredex.altshaders.pack.materialmap.NamespacedId, Integer> entityIds;
	private int entityId = -1;
	private int blockEntityId = -1;
	/** The entity's hurt or explosion flash ({@code entityColor}); all 0: none. */
	private final float[] entityColor = new float[4];
	/** The entity's eyes are being drawn (gbuffers_spidereyes). */
	private boolean eyes;
	/** A deferred/composite/begin/prepare pass: its compute programs, then its full-screen program (either may be absent). */
	private record Pass(List<PackProgram> computes, PackProgram fragment) {
	}

	private final List<Pass> begin = new ArrayList<>();
	private final List<Pass> prepare = new ArrayList<>();
	private final List<Pass> deferred = new ArrayList<>();
	private final List<Pass> composite = new ArrayList<>();
	private final List<PackProgram> setupComputes = new ArrayList<>();
	private final List<PackProgram> finalComputes = new ArrayList<>();
	private boolean setupDone;
	private final PackResources resources;
	/** The shadow pass, or null (no shadow program, or the pack switched shadows off). */
	private ShadowPass shadow;
	private final List<Pass> shadowcomp = new ArrayList<>();
	/** The shadow maps until 6.7: depth 1 everywhere (plain, and comparing for shadow samplers). */
	private final GpuTexture shadowDepth;
	private final GpuTexture shadowDepthCompare;
	private PackProgram finalPass;
	/** {@code -Daltshaders.debug.view=colortex4|depthtex0}: that target on the screen instead of the final pass. */
	private PackProgram debugView;
	private final GpuTexture white;
	/** The game's light curve as a modern lightmap: texel (block, sky) is the colour that light gives (6.6). */
	private final GpuTexture lightmap;
	private final ByteBuffer lightmapPixels = ByteBuffer.allocateDirect(16 * 16 * 4);
	private int lightmapHash;
	private final GpuTexture flatNormal;
	private final GpuTexture black;
	private final GpuTexture noise;
	/** The game's clear lands in colortex0 (both sides) and depthtex0. */
	private final GpuFramebuffer clearFramebuffer;
	/** The game's clear when the pack keeps colortex0 ({@code colortex0Clear=false}): depthtex0 only. */
	private final GpuFramebuffer depthClearFramebuffer;
	/** Per colour target and side, a framebuffer for its clear. */
	private final Map<Integer, GpuFramebuffer> clearTargets = new HashMap<>();
	private boolean deferredDone;
	private boolean handDepthCopied;
	private RenderPhase phase = RenderPhase.NONE;
	/** The program the game's draws use now (null during terrain or with none). */
	private PackProgram current;
	private boolean broken;
	private final PackProgram terrainCutout;
	/**
	 * The pack's solid terrain program without AltShaders' alpha test, for the opaque layer's quads whose texture has
	 * no see-through texels (Iris's {@code terrain_solid}; the early depth test stays on), or null: the cut-out
	 * program draws the whole layer.
	 */
	private final PackProgram terrainSolid;
	private final PackProgram terrainWater;
	/** The water program's depth-only twin (fancy water's depth draw), or null. */
	private final PackProgram terrainWaterDepth;
	private PackTerrain terrain;
	/** The game's particles after the translucent terrain (Iris's particle ordering AFTER). */
	private final boolean particlesLate;
	/** centerDepthSmooth on the GPU; sampled each frame only if a program reads it. */
	private final CenterDepth centerDepth;
	private boolean centerDepthUsed;

	PackPipeline(Gpu gpu, ShaderPrograms builder, ProgramSet set, int width, int height) {
		this.gpu = gpu;
		this.set = set;
		this.targets = new RenderTargets(gpu, set.getPackDirectives(), width, height);
		this.uniforms = new Uniforms(set.getPackDirectives(), set.getPack().customUniforms, set.getPack().getIdMap());
		this.entityIds = set.getPack().getIdMap().getEntityIdMap();
		white = solid(0xFFFFFFFF, "white");
		lightmap = gpu.createTexture(TextureSpec.of2d(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, 16, 16), "altshaders lightmap");
		lightmap.sampling(GL_LINEAR, GL_LINEAR, 0x812F); // clamp to edge
		updateLightmap();
		flatNormal = solid(0xFFFF8080, "flat normal"); // ABGR bytes: (128,128,255,255)
		black = solid(0, "black");
		noise = makeNoise(set.getPackDirectives().getNoiseTextureResolution());
		shadowDepth = depthOne(false);
		shadowDepthCompare = depthOne(true);
		resources = new PackResources(gpu, set.getPack(), width, height);
		java.util.Map<com.coredex.altshaders.pack.helpers.Tri<String, com.coredex.altshaders.pack.gl.TextureType, TextureStage>, String> patches =
				set.getPackDirectives().getTextureMap();

		// Build everything at once: with parallel compilation the driver works on all of them together.
		ProgramFallbackResolver resolver = new ProgramFallbackResolver(set);
		Map<String, PackProgram> byName = new HashMap<>();
		for (ProgramId id : ProgramId.values()) {
			if (id.getGroup() != ProgramGroup.Gbuffers) {
				continue;
			}
			resolver.resolve(id).ifPresent(source -> {
				PackProgram p = byName.computeIfAbsent(source.getName(), n -> track(staged(PackProgram.build(builder,
						source, true, renames(source, TextureStage.GBUFFERS_AND_SHADOW, patches)), TextureStage.GBUFFERS_AND_SHADOW)));
				gbuffers.put(id, p);
			});
		}
		// Altrender's terrain with the pack's terrain and water programs (development.md 6.6).
		// b1.7.3's layer 0 has the solid and the cut-out blocks (leaves, plants, the grass side's overlay) in one pass:
		// it takes the pack's cut-out program (Iris: terrain_cutout → terrain → …), which keeps transparent texels
		// out; a pack's solid-only program (Photon's gbuffers_terrain_solid) would draw them black.
		ProgramSource solidSource = resolver.resolve(ProgramId.TerrainCutout).orElse(null);
		ProgramSource waterSource = resolver.resolve(ProgramId.Water).orElse(null);
		// As Iris, the terrain takes the fallback chain (terrain → textured_lit → textured → basic); a pack can keep
		// Altrender's own terrain (the game's look) in its buffers with altshaders.vanillaTerrain (Review; ar_minimal
		// does, to stay identical to no pack).
		boolean packTerrain = solidSource != null && !set.getPackDirectives().isVanillaTerrain();
		terrainCutout = packTerrain ? track(staged(PackProgram.buildTerrain(builder, solidSource), TextureStage.GBUFFERS_AND_SHADOW)) : null;
		terrainSolid = terrainCutout != null ? solidTwin(builder, resolver.resolve(ProgramId.TerrainSolid).orElse(null),
				terrainCutout) : null;
		terrainWater = packTerrain && waterSource != null
				? waterSource == solidSource ? terrainCutout
						: track(staged(PackProgram.buildTerrain(builder, waterSource), TextureStage.GBUFFERS_AND_SHADOW))
				: null;
		ProgramSource depthSource = waterSource != null ? waterSource : solidSource;
		PackProgram depth = packTerrain && depthSource != null && WATER_DEPTH_DRAW
				&& !Boolean.getBoolean("altshaders.debug.noWaterDepth")
				? PackProgram.buildTerrainDepth(builder, depthSource) : null;
		terrainWaterDepth = depth != null ? track(staged(depth, TextureStage.GBUFFERS_AND_SHADOW)) : null;
		// The shadow pass (development.md 6.7): the pack's shadow programs on Altrender's terrain.
		ProgramSource shadowSource = resolver.resolve(ProgramId.ShadowCutout).orElse(null); // as above: cut-outs
		ProgramSource shadowWaterSource = resolver.resolve(ProgramId.ShadowWater).orElse(null);
		boolean shadowOn = shadowSource != null
				&& set.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true)
				&& AltrenderApi.terrainView() != null;
		if (shadowOn) {
			PackProgram ss = track(staged(PackProgram.buildTerrain(builder, shadowSource), TextureStage.GBUFFERS_AND_SHADOW));
			PackProgram sw = shadowWaterSource != null
					? track(staged(PackProgram.buildTerrain(builder, shadowWaterSource), TextureStage.GBUFFERS_AND_SHADOW))
					: ss;
			shadow = new ShadowPass(gpu, set.getPackDirectives(), ss, sw);
			shadow.textures = this::gbufferTextures;
			shadow.solidPart = solidTwin(builder, resolver.resolve(ProgramId.ShadowSolid).orElse(null), ss);
			ProgramSource shadowEntities = resolver.resolve(ProgramId.ShadowEntities).orElse(null);
			if (shadowEntities != null && AltrenderApi.entityView() != null) {
				shadow.entities = track(staged(PackProgram.build(builder, shadowEntities, true,
						renames(shadowEntities, TextureStage.GBUFFERS_AND_SHADOW, patches)), TextureStage.GBUFFERS_AND_SHADOW));
			}
			passes(builder, ProgramArrayId.ShadowComposite, TextureStage.SHADOWCOMP, patches, shadowcomp);
		}
		passes(builder, ProgramArrayId.Begin, TextureStage.BEGIN, patches, begin);
		passes(builder, ProgramArrayId.Prepare, TextureStage.PREPARE, patches, prepare);
		passes(builder, ProgramArrayId.Deferred, TextureStage.DEFERRED, patches, deferred);
		passes(builder, ProgramArrayId.Composite, TextureStage.COMPOSITE_AND_FINAL, patches, composite);
		computes(builder, set.getSetup(), TextureStage.SETUP, patches, setupComputes);
		computes(builder, set.getFinalCompute(), TextureStage.COMPOSITE_AND_FINAL, patches, finalComputes);
		finalPass = set.get(ProgramId.Final).map(s -> track(staged(PackProgram.build(builder, s, false,
				renames(s, TextureStage.COMPOSITE_AND_FINAL, patches)), TextureStage.COMPOSITE_AND_FINAL)))
				.orElseGet(() -> track(PackProgram.blit(builder)));
		java.util.Set<Integer> mipmapped = new java.util.HashSet<>();
		for (PackProgram p : all) {
			if (p.directives != null) {
				mipmapped.addAll(p.directives.getMipmappedBuffers());
			}
		}
		targets.mipmapped(mipmapped);
		// Iris's particle ordering: the pack's, else after the translucents when it has deferred passes (and doesn't
		// draw entities separately), else mixed. b1.7.3's particles are all opaque-sheet ones, which mixed and before
		// draw where the game does (before the translucent terrain).
		com.coredex.altshaders.pack.properties.ParticleRenderingSettings order = set.getPackDirectives()
				.getParticleRenderingSettings();
		if (order == null || order == com.coredex.altshaders.pack.properties.ParticleRenderingSettings.UNSET) {
			order = !deferred.isEmpty() && !set.getPackDirectives().shouldUseSeparateEntityDraws()
					? com.coredex.altshaders.pack.properties.ParticleRenderingSettings.AFTER
					: com.coredex.altshaders.pack.properties.ParticleRenderingSettings.MIXED;
		}
		particlesLate = order == com.coredex.altshaders.pack.properties.ParticleRenderingSettings.AFTER;
		centerDepth = new CenterDepth(gpu, builder, set.getPackDirectives().getCenterDepthHalfLife());
		track(centerDepth.program);
		String view = System.getProperty("altshaders.debug.view");
		debugView = view != null && !view.isBlank() ? track(PackProgram.debugView(builder)) : null;
		clearFramebuffer = gpu.createFramebuffer("altshaders clear");
		RenderTargets.Target c0 = targets.get(0);
		clearFramebuffer.color(0, c0.sides[0], 0);
		clearFramebuffer.color(1, c0.sides[1], 0);
		clearFramebuffer.depth(targets.depth0);
		clearFramebuffer.drawBuffers(0, 1);
		depthClearFramebuffer = gpu.createFramebuffer("altshaders clear (depth)");
		depthClearFramebuffer.depth(targets.depth0);
		depthClearFramebuffer.drawBuffers();
	}

	/** Whether the pack clears colortex{@code i} each frame ({@code colortexNClear}; Iris's default: yes). */
	private boolean clears(int i) {
		PackRenderTargetDirectives.RenderTargetSettings s = set.getPackDirectives().getRenderTargetDirectives()
				.getRenderTargetSettings().get(i);
		return s == null || s.shouldClear();
	}

	/**
	 * The opaque layer's program for its solid quads: {@code source} (the pack's solid program, Iris's fallbacks)
	 * without AltShaders' alpha test, sharing {@code cutout}'s texture units; null when it would draw into other
	 * buffers than the cut-out program (the cut-out program then draws the whole layer) or the split is off
	 * ({@code -Daltshaders.debug.noSolidSplit}).
	 */
	private PackProgram solidTwin(ShaderPrograms builder, ProgramSource source, PackProgram cutout) {
		if (source == null || Boolean.getBoolean("altshaders.debug.noSolidSplit")
				|| !java.util.Arrays.equals(source.getDirectives().getDrawBuffers(), cutout.drawBuffers())) {
			return null;
		}
		PackProgram p = PackProgram.buildTerrain(builder, source, false);
		p.unitsLike = cutout;
		return track(staged(p, TextureStage.GBUFFERS_AND_SHADOW));
	}

	private PackProgram track(PackProgram p) {
		all.add(p);
		return p;
	}

	private static PackProgram staged(PackProgram p, TextureStage stage) {
		p.stage = stage;
		return p;
	}

	/** The passes of one program array: each index's compute programs, then its full-screen program. */
	private void passes(ShaderPrograms builder, ProgramArrayId id, TextureStage stage,
			java.util.Map<com.coredex.altshaders.pack.helpers.Tri<String, com.coredex.altshaders.pack.gl.TextureType, TextureStage>, String> patches,
			List<Pass> into) {
		ProgramSource[] fragments = set.getComposite(id);
		com.coredex.altshaders.pack.programs.ComputeSource[][] computes = set.getCompute(id);
		for (int i = 0; i < fragments.length; i++) {
			List<PackProgram> cs = new ArrayList<>();
			if (i < computes.length && computes[i] != null) {
				computes(builder, computes[i], stage, patches, cs);
			}
			ProgramSource f = fragments[i];
			PackProgram fp = f != null && f.isValid()
					? track(staged(PackProgram.build(builder, f, false, renames(f, stage, patches)), stage)) : null;
			if (fp != null || !cs.isEmpty()) {
				into.add(new Pass(cs, fp));
			}
		}
	}

	private void computes(ShaderPrograms builder, com.coredex.altshaders.pack.programs.ComputeSource[] sources,
			TextureStage stage,
			java.util.Map<com.coredex.altshaders.pack.helpers.Tri<String, com.coredex.altshaders.pack.gl.TextureType, TextureStage>, String> patches,
			List<PackProgram> into) {
		if (sources == null || !AltrenderApi.capabilities().compute()) {
			return;
		}
		for (com.coredex.altshaders.pack.programs.ComputeSource c : sources) {
			if (c != null && c.isValid()) {
				Map<String, String> r = renames(c.getSource().orElse(""), stage, patches);
				into.add(track(staged(PackProgram.build(builder, c, r), stage)));
			}
		}
	}

	private static Map<String, String> renames(ProgramSource s, TextureStage stage,
			java.util.Map<com.coredex.altshaders.pack.helpers.Tri<String, com.coredex.altshaders.pack.gl.TextureType, TextureStage>, String> patches) {
		StringBuilder all = new StringBuilder();
		s.getVertexSource().ifPresent(all::append);
		s.getGeometrySource().ifPresent(all::append);
		s.getFragmentSource().ifPresent(all::append);
		return renames(all.toString(), stage, patches);
	}

	/**
	 * Iris's custom-texture patching: a raw texture given to a sampler in a stage ({@code texture.deferred.depthtex0 =
	 * … TEXTURE_3D …}) replaces the sampler of that name and type the stage's programs declare.
	 */
	private static Map<String, String> renames(String source, TextureStage stage,
			java.util.Map<com.coredex.altshaders.pack.helpers.Tri<String, com.coredex.altshaders.pack.gl.TextureType, TextureStage>, String> patches) {
		Map<String, String> r = new HashMap<>();
		patches.forEach((key, newName) -> {
			if (key.third() != stage || key.second() == null) {
				return;
			}
			String type = switch (key.second()) {
				case TEXTURE_1D -> "sampler1D";
				case TEXTURE_3D -> "sampler3D";
				case TEXTURE_RECTANGLE -> "sampler2DRect";
				default -> "sampler2D";
			};
			if (java.util.regex.Pattern.compile("\\buniform\\s+(?:\\w+\\s+)*" + type + "\\s+" + key.first() + "\\b")
					.matcher(source).find()) {
				r.put(key.first(), newName);
			}
		});
		return r;
	}

	/** A 1×1 depth texture at depth 1 (never in shadow). */
	private GpuTexture depthOne(boolean compare) {
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(0x81A6, 0x1902, 0x1406, 1, 1), "altshaders shadow placeholder");
		ByteBuffer b = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		b.putFloat(1f).flip();
		t.upload(0, 0, 0, 0, 1, 1, 1, b);
		t.sampling(GL_LINEAR, GL_LINEAR, 0x812F);
		t.compare(compare ? 0x0203 : 0); // GL_LEQUAL
		return t;
	}

	/** Waits for every build; false (with the logs) if one failed. */
	boolean finish() {
		boolean ok = true;
		for (PackProgram p : all) {
			ok &= p.ok(gpu, resources);
		}
		if (ok) {
			for (PackProgram p : all) {
				for (PackProgram.Sampler s : p.samplers) {
					centerDepthUsed |= s.source() == PackProgram.Source.CENTER_DEPTH;
				}
			}
		}
		if (ok && (terrainCutout != null || terrainWater != null)) {
			terrain = new PackTerrain(terrainSolid, terrainCutout, terrainWater, terrainWaterDepth,
					set.getPack().getIdMap(), AltrenderApi.blockNames(), set.getPackDirectives().isOldLighting(),
					set.getPackDirectives().shouldUseSeparateAo(), WATER_DEPTH_DRAW);
		}
		if (ok) {
			// Every target a program writes or reads, now, so frames don't allocate.
			for (PackProgram p : all) {
				if (p.directives != null) {
					for (int b : p.drawBuffers()) {
						targets.get(b);
					}
				}
				for (PackProgram.Sampler s : p.samplers) {
					if (s.source() == PackProgram.Source.COLOR) {
						targets.get(s.index());
					}
				}
				for (PackProgram.ImageUnit i : p.images) {
					if (i.color() >= 0) {
						targets.get(i.color());
					}
				}
			}
		}
		return ok;
	}

	/** The game's parts the pack switches off ({@code clouds=off}, {@code sun=false}, …). */
	boolean hides(com.coredex.altrender.api.render.VanillaPart part) {
		var d = set.getPackDirectives();
		return switch (part) {
			case CLOUDS -> d.getCloudSetting() == com.coredex.altshaders.pack.properties.CloudSetting.OFF;
			case WEATHER -> !d.shouldRenderWeather();
			case SUN -> !d.shouldRenderSun();
			case MOON -> !d.shouldRenderMoon();
			case STARS -> !d.shouldRenderStars();
			case ENTITY_SHADOWS -> shadow != null; // Iris (and OptiFine): not with a shadow map
		};
	}

	boolean particlesLate() {
		return particlesLate && !broken;
	}

	/** Its terrain for Altrender, once built (null before, or without terrain programs). */
	PackTerrain terrain() {
		return terrain;
	}

	/** The first failure's program name and log, for the pack page. */
	String failure() {
		for (PackProgram p : all) {
			if (!p.ok(gpu)) {
				return p.name + ": " + p.log();
			}
		}
		return null;
	}

	/** The lightmap from the game's brightness table and the sky's darkness, uploaded when they change. */
	private void updateLightmap() {
		com.coredex.altrender.api.GameState g = AltrenderApi.gameState();
		float[] table = g != null ? g.brightnessTable() : null;
		int dark = g != null ? g.skyDarkness() : 0;
		if (table == null || table.length < 16) {
			table = new float[16];
			java.util.Arrays.fill(table, 1f);
		}
		int hash = java.util.Arrays.hashCode(table) * 31 + dark;
		if (hash == lightmapHash) {
			return;
		}
		lightmapHash = hash;
		lightmapPixels.clear();
		for (int sky = 0; sky < 16; sky++) {
			for (int block = 0; block < 16; block++) {
				int level = Math.max(Math.max(sky - dark, 0), block);
				int v = Math.round(Math.max(0f, Math.min(1f, table[level])) * 255f);
				lightmapPixels.put((byte) v).put((byte) v).put((byte) v).put((byte) 255);
			}
		}
		lightmapPixels.flip();
		lightmap.upload(0, 0, 0, 0, 16, 16, 1, lightmapPixels);
	}

	private GpuTexture solid(int abgr, String label) {
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, 1, 1),
				"altshaders " + label);
		ByteBuffer b = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		b.putInt(abgr).flip();
		t.upload(0, 0, 0, 0, 1, 1, 1, b);
		return t;
	}

	/** Random RGB noise ({@code noisetex}), the same each load. Packs' own noise textures come with 6.4's textures. */
	private GpuTexture makeNoise(int size) {
		int n = Math.max(1, size);
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(GL_RGB8, GL_RGB, GL_UNSIGNED_BYTE, n, n), "altshaders noisetex");
		ByteBuffer b = ByteBuffer.allocateDirect(n * n * 3);
		Random r = new Random(0);
		for (int i = 0; i < n * n * 3; i++) {
			b.put((byte) r.nextInt(256));
		}
		b.flip();
		t.upload(0, 0, 0, 0, n, n, 1, b);
		t.sampling(GL_LINEAR, GL_LINEAR, GL_REPEAT);
		return t;
	}

	// ---- the frame ----------------------------------------------------------------------------------------------

	void beginWorld(int width, int height) {
		if (width != targets.width() || height != targets.height()) {
			targets.resize(width, height);
			for (PackProgram p : all) {
				p.dropFramebuffers();
			}
			for (GpuFramebuffer fb : clearTargets.values()) {
				fb.delete();
			}
			clearTargets.clear();
			RenderTargets.Target c0 = targets.get(0);
			clearFramebuffer.color(0, c0.sides[0], 0);
			clearFramebuffer.color(1, c0.sides[1], 0);
			setupDone = false; // Iris runs the setup programs again after a resize
		}
		targets.resetFlips();
		deferredDone = false;
		handDepthCopied = false;
		uniforms.frameStart(width, height);
		// The game's clear: colortex0 too, unless the pack keeps it (Iris clears it to the fog colour itself, only if
		// colortex0Clear; once after it's made in any case).
		gpu.gameDraws(clears(0) || targets.get(0).fullClear ? clearFramebuffer : depthClearFramebuffer, 0);
		gpu.gameAlphaTest(false); // the programs test alpha (Iris's rule)
		// The game's own draws have no light coordinates (b1.7.3 bakes light into the colour): full sky, no block
		// light, so packs don't light them a second time from black (per-draw light: 6.8).
		gpu.gameLightCoords(0, 240);
	}

	void cameraReady() {
		if (shadow != null) {
			shadow.matrices();
			uniforms.shadow(shadow.modelView, shadow.projection);
		}
		uniforms.camera(gpu);
		updateLightmap();
		gpu.begin("altshaders clear");
		Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> settings = set.getPackDirectives()
				.getRenderTargetDirectives().getRenderTargetSettings();
		for (int i = 0; i < PackRenderTargetDirectives.MAX_COLOR_BUFFERS; i++) {
			RenderTargets.Target t = targets.peek(i);
			PackRenderTargetDirectives.RenderTargetSettings s = settings.get(i);
			// Iris's full clear: a buffer the pack keeps is still cleared once, after it's made or resized.
			if (t == null || s != null && !s.shouldClear() && !t.fullClear) {
				continue;
			}
			t.fullClear = false;
			Vector4f c = s != null ? s.getClearColor().orElse(null) : null;
			if (i == 0 && c == null) {
				// The game's own clear (the fog colour) went there, with alpha 0 (b1.7.3); Iris's has alpha 1.
				for (int side = 0; side < 2; side++) {
					gpu.bindFramebuffer(clearTarget(t, side));
					gpu.clearAlpha(0, 1f);
				}
				continue;
			}
			if (c == null) {
				c = i == 1 ? new Vector4f(1, 1, 1, 1) : new Vector4f(0, 0, 0, 0);
			}
			boolean integer = t.format.getPixelFormat().isInteger();
			for (int side = 0; side < 2; side++) {
				gpu.bindFramebuffer(clearTarget(t, side));
				if (integer) {
					gpu.clearColorInt(0, (int) c.x, (int) c.y, (int) c.z, (int) c.w);
				} else {
					gpu.clearColor(0, c.x, c.y, c.z, c.w);
				}
			}
		}
		gpu.end();
		resources.clearImages();
		if (shadow != null) {
			shadow.clear(); // Iris: with the other clears, before the begin passes
		}
		if (!setupDone) {
			setupDone = true;
			runComputes(setupComputes);
		}
		runPasses(begin, "begin");
		if (shadow != null) {
			gpu.beginTimer(TimedPass.SHADOW);
			shadow.render(uniforms);
			runShadowcomp();
			gpu.endTimer(TimedPass.SHADOW);
		}
		runPasses(prepare, "prepare");
		phase(RenderPhase.NONE);
	}

	/**
	 * The shadowcomp passes (Iris's shadow composite): computes, then a full-screen program on the shadow targets at
	 * the shadow map's resolution, each reading one side of the shadowcolor buffers and writing the other.
	 */
	private void runShadowcomp() {
		shadow.preFlips(set.getPackDirectives().getExplicitFlips("shadowcomp_pre"));
		for (Pass pass : shadowcomp) {
			runComputes(pass.computes());
			PackProgram p = pass.fragment();
			if (p == null) {
				continue;
			}
			gpu.begin("altshaders " + p.name);
			gpu.bindFramebuffer(shadow.compositeFramebuffer(p));
			var vp = p.directives.getViewportScale();
			int r = shadow.resolution;
			gpu.viewport((int) (r * vp.viewportX()), (int) (r * vp.viewportY()), (int) (r * vp.scale()),
					(int) (r * vp.scale()));
			draw(p, passBlend(p));
			gpu.end();
			shadow.flipAfter(p);
		}
	}

	private GpuFramebuffer clearTarget(RenderTargets.Target t, int side) {
		return clearTargets.computeIfAbsent(t.index * 2 + side, k -> {
			GpuFramebuffer fb = gpu.createFramebuffer("altshaders clear colortex" + t.index);
			fb.color(0, t.sides[side], 0);
			fb.drawBuffers(0);
			return fb;
		});
	}

	void phase(RenderPhase next) {
		if (broken) {
			return;
		}
		if (next == RenderPhase.TERRAIN_TRANSLUCENT && !deferredDone) {
			deferredDone = true;
			gpu.begin("altshaders deferred");
			gpu.copyDepth(gbufferFramebuffer(ProgramId.TerrainSolid), targets.depth1);
			gpu.end();
			runPasses(deferred, "deferred");
		}
		if (phase == RenderPhase.CLOUDS && next == RenderPhase.NONE || next == RenderPhase.HAND_SOLID && !handDepthCopied) {
			// depthtex2: the depth before the hand (its depth clear comes after the clouds).
			if (!handDepthCopied) {
				handDepthCopied = true;
				gpu.begin("altshaders hand depth");
				gpu.copyDepth(gbufferFramebuffer(ProgramId.Textured), targets.depth2);
				gpu.end();
			}
		}
		phase = next;
		current = null;
		entityId = -1;
		blockEntityId = -1;
		java.util.Arrays.fill(entityColor, 0);
		eyes = false;
		gpu.gameLightCoords(0, 240);
		bind(choose());
	}

	/** An entity is drawn next: its pack id and its light (development.md 6.8). */
	boolean entity(String type, int blockLight, int skyLight) {
		if (broken || current == null || shadow != null && shadow.drawingEntities) {
			return false; // no pack program draws it: the game's own brightness stays
		}
		if (eyes) {
			eyes = false; // a layer the game started and never drew (its red pass, skipped): the entity program again
			PackProgram p = choose();
			if (p != current) {
				bind(p);
			}
		}
		Integer id = type != null ? entityIds.get(new com.coredex.altshaders.pack.materialmap.NamespacedId(type)) : null;
		entityId = id != null ? id : -1;
		blockEntityId = -1;
		java.util.Arrays.fill(entityColor, 0);
		gpu.gameLightCoords(blockLight * 16, skyLight * 16);
		if (current != null) {
			uniforms.drawIds(gpu, current, entityId, blockEntityId, entityColor);
		}
		return true;
	}

	/** The entity's hurt or explosion flash, for {@code entityColor} (Iris's: the game's overlay pass is skipped). */
	boolean entityColor(float r, float g, float b, float a) {
		if (broken || current == null || shadow != null && shadow.drawingEntities) {
			return false;
		}
		entityColor[0] = r;
		entityColor[1] = g;
		entityColor[2] = b;
		entityColor[3] = a;
		uniforms.drawIds(gpu, current, entityId, blockEntityId, entityColor);
		return true;
	}

	/** The entity's eyes: gbuffers_spidereyes (Iris; its fallbacks and default additive blend) while they draw. */
	void entityEyes(boolean on) {
		if (broken || on == eyes || current == null && on || shadow != null && shadow.drawingEntities
				|| phase != RenderPhase.ENTITIES) {
			if (!on) {
				eyes = false;
			}
			return;
		}
		eyes = on;
		PackProgram p = choose();
		if (p != current) {
			bind(p);
		}
	}

	/** A block entity is drawn next: its block's pack id and its light. */
	boolean blockEntity(int blockKey, int blockLight, int skyLight) {
		if (broken || current == null || shadow != null && shadow.drawingEntities) {
			return false; // no pack program draws it: the game's own brightness stays
		}
		entityId = -1;
		blockEntityId = terrain != null ? terrain.blockId(blockKey) : -1;
		java.util.Arrays.fill(entityColor, 0);
		gpu.gameLightCoords(blockLight * 16, skyLight * 16);
		if (current != null) {
			uniforms.drawIds(gpu, current, entityId, blockEntityId, entityColor);
		}
		return true;
	}

	/**
	 * The program for the phase; in the sky and between phases it follows the game's texturing and lighting, as
	 * OptiFine's did (sun and moon textured, the sky planes not; cracks textured, the outline not).
	 */
	private PackProgram choose() {
		ProgramId id = PHASE_PROGRAMS.get(phase);
		if (eyes && phase == RenderPhase.ENTITIES) {
			id = ProgramId.SpiderEyes;
		}
		FixedFunction ff = AltrenderApi.fixedFunction();
		if (ff != null && (phase == RenderPhase.SKY || phase == RenderPhase.SKY_TEXTURED)) {
			id = ff.texture() ? ProgramId.SkyTextured : ProgramId.SkyBasic;
		} else if (ff != null && phase == RenderPhase.NONE) {
			id = !ff.texture() ? ProgramId.Basic : ff.lighting() ? ProgramId.TexturedLit : ProgramId.Textured;
		}
		PackProgram p = id != null ? gbuffers.get(id) : null;
		return p != null ? p : gbuffers.get(ProgramId.Basic);
	}

	private void bind(PackProgram p) {
		if (p == null) {
			// No gbuffers program at all: the game draws as vanilla into colortex0, with its own alpha test and blend.
			current = null; // no program of ours is bound: its uniforms aren't ours to set
			gpu.gameDraws(clearFramebuffer, 0);
			gpu.gameAlphaTest(true);
			gpu.gameBlend(null);
			gpu.gameBufferBlends(null);
			return;
		}
		gpu.gameAlphaTest(false);
		boolean terrainPhase = phase == RenderPhase.TERRAIN_SOLID || phase == RenderPhase.TERRAIN_CUTOUT
				|| phase == RenderPhase.TERRAIN_TRANSLUCENT;
		if (terrainPhase) {
			// Altrender draws its terrain with the pack's terrain program (TerrainPipeline), into its buffers.
			PackProgram t = phase == RenderPhase.TERRAIN_TRANSLUCENT ? terrainWater : terrainCutout;
			PackProgram owner = t != null ? t : p;
			gpu.gameDraws(owner.framebuffer(gpu, targets, true, targets.depth0), 0);
			gpu.gameBlend(blendOf(owner));
			gpu.gameBufferBlends(bufferBlendsOf(owner));
			current = null;
			if (phase == RenderPhase.TERRAIN_SOLID) {
				uniforms.terrainFog(AltrenderApi.fixedFunction());
			}
			if (t != null) {
				gpu.begin("altshaders " + t.name);
				gpu.useProgram(t.program());
				uniforms.apply(gpu, t);
				uniforms.fixedFunction(gpu, t, AltrenderApi.fixedFunction());
				uniforms.renderStage(gpu, t, renderStage(phase));
				if (phase == RenderPhase.TERRAIN_SOLID) {
					uniforms.alphaTest(gpu, t, 0.1f); // the cut-outs in layer 0, as the game's terrain alpha test
				}
				if (phase == RenderPhase.TERRAIN_SOLID && terrainSolid != null) {
					// The solid quads' program reads the same uniforms (no alpha test of ours: none to set).
					gpu.useProgram(terrainSolid.program());
					uniforms.apply(gpu, terrainSolid);
					uniforms.fixedFunction(gpu, terrainSolid, AltrenderApi.fixedFunction());
					uniforms.renderStage(gpu, terrainSolid, renderStage(phase));
					gpu.useProgram(t.program());
				}
				if (phase == RenderPhase.TERRAIN_TRANSLUCENT && terrainWaterDepth != null) {
					// Its twin for the depth-only draw reads the same uniforms in its vertex shader.
					gpu.useProgram(terrainWaterDepth.program());
					uniforms.apply(gpu, terrainWaterDepth);
					uniforms.fixedFunction(gpu, terrainWaterDepth, AltrenderApi.fixedFunction());
					uniforms.renderStage(gpu, terrainWaterDepth, renderStage(phase));
					gpu.useProgram(t.program());
				}
				gpu.end();
				gbufferTextures(t);
				if (phase == RenderPhase.TERRAIN_SOLID && terrainSolid != null) {
					gbufferTextures(terrainSolid); // its own samplers on units past the cut-out program's
				}
			}
			return;
		}
		GpuFramebuffer fb = p.framebuffer(gpu, targets, true, targets.depth0);
		gpu.gameDraws(fb, p.program());
		gpu.gameBlend(blendOf(p));
		gpu.gameBufferBlends(bufferBlendsOf(p));
		current = p;
		{
			// The game's program is current now: its uniforms can be set straight away.
			uniforms.apply(gpu, p);
			uniforms.fixedFunction(gpu, p, AltrenderApi.fixedFunction());
			uniforms.renderStage(gpu, p, renderStage(phase));
			uniforms.drawIds(gpu, p, entityId, blockEntityId, entityColor);
			gbufferTextures(p);
		}
	}

	/** The program's {@code blend.<program>} (or Iris's default for it) for the game's draws; null: the game's own. */
	static com.coredex.altrender.api.gpu.Blend blendOf(PackProgram p) {
		if (p.directives == null) {
			return null;
		}
		java.util.Optional<com.coredex.altshaders.pack.gl.BlendModeOverride> o = p.directives.getBlendModeOverride();
		if (o.isEmpty()) {
			return null;
		}
		com.coredex.altshaders.pack.gl.BlendMode m = o.get().getBlendMode();
		return m == null ? com.coredex.altrender.api.gpu.Blend.NONE
				: new com.coredex.altrender.api.gpu.Blend(m.srcRgb(), m.dstRgb(), m.srcAlpha(), m.dstAlpha());
	}

	/**
	 * The program's {@code blend.<program>.<buffer>} per attachment (Iris: the buffer's place in its draw buffers;
	 * buffers it doesn't write are ignored), or null.
	 */
	static com.coredex.altrender.api.gpu.Blend[] bufferBlendsOf(PackProgram p) {
		if (p.directives == null || p.directives.getBufferBlendOverrides().isEmpty()) {
			return null;
		}
		int[] buffers = p.drawBuffers();
		com.coredex.altrender.api.gpu.Blend[] out = null;
		for (com.coredex.altshaders.pack.gl.BufferBlendInformation b : p.directives.getBufferBlendOverrides()) {
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] != b.index()) {
					continue;
				}
				if (out == null) {
					out = new com.coredex.altrender.api.gpu.Blend[buffers.length];
				}
				com.coredex.altshaders.pack.gl.BlendMode m = b.blendMode();
				out[i] = m == null ? com.coredex.altrender.api.gpu.Blend.NONE
						: new com.coredex.altrender.api.gpu.Blend(m.srcRgb(), m.dstRgb(), m.srcAlpha(), m.dstAlpha());
			}
		}
		return out;
	}

	/** The phase as Iris's {@code MC_RENDER_STAGE_*} value (the {@code renderStage} uniform). */
	private static int renderStage(RenderPhase phase) {
		String name = switch (phase) {
			case SKY_TEXTURED -> "SUN";
			default -> phase.name();
		};
		int i = com.coredex.altshaders.pack.StandardDefines.RENDER_STAGES.indexOf(name);
		return Math.max(i, 0);
	}

	/** The game changed texturing, lighting or fog in the middle of a phase. */
	void fixedFunction(FixedFunction.Change change) {
		if (broken || shadow != null && shadow.drawingEntities) {
			return;
		}
		switch (change) {
			case TEXTURE, LIGHTING -> {
				PackProgram p = choose();
				if (current != null && p != current) {
					bind(p);
				} else if (current != null) {
					uniforms.fixedFunction(gpu, current, AltrenderApi.fixedFunction());
				}
			}
			case ALPHA_TEST, FOG, FOG_PARAMETER -> {
				if (current != null) {
					uniforms.fixedFunction(gpu, current, AltrenderApi.fixedFunction());
				}
			}
		}
	}

	private GpuFramebuffer gbufferFramebuffer(ProgramId id) {
		PackProgram p = gbuffers.get(id);
		return p != null ? p.framebuffer(gpu, targets, true, targets.depth0) : clearFramebuffer;
	}

	/** The extra textures a gbuffers program samples, on its units for the game's draws. */
	private void gbufferTextures(PackProgram p) {
		for (PackProgram.Sampler s : p.samplers) {
			GpuTexture t = texture(s);
			if (t != null) {
				gpu.gameTexture(s.unit(), t);
			}
		}
		if (!p.images.isEmpty()) {
			gpu.begin("altshaders images " + p.name);
			bindImages(p);
			gpu.end();
		}
	}

	void endWorld() {
		if (!broken) {
			if (centerDepthUsed) {
				// Iris samples it as the hand begins: the depth before the hand (depthtex2 once copied).
				centerDepth.sample(handDepthCopied ? targets.depth2 : targets.depth0);
			}
			runPasses(composite, "composite");
			gpu.beginTimer(TimedPass.PASSES);
			runComputes(finalComputes);
			gpu.begin("altshaders final");
			gpu.bindFramebuffer(null);
			gpu.viewport(0, 0, targets.width(), targets.height());
			if (debugView != null) {
				drawDebugView(System.getProperty("altshaders.debug.view"));
			} else {
				for (int b : finalPass.directives != null ? finalPass.directives.getMipmappedBuffers()
						: java.util.Set.<Integer>of()) {
					targets.mipmaps(targets.get(b));
				}
				draw(finalPass, null); // Iris: the final pass never blends
			}
			gpu.end();
			keepFlipped();
			gpu.endTimer(TimedPass.PASSES);
		}
		gpu.gameDraws(null, 0);
		gpu.gameAlphaTest(true);
		gpu.gameBufferBlends(null);
		gpu.gameBlend(null);
		gpu.gameLightCoords(Float.NaN, Float.NaN);
		for (int unit = 1; unit < 32; unit++) {
			gpu.gameTexture(unit, null);
		}
	}

	/**
	 * Buffers the pack doesn't clear and that end the frame on their second side: that side copied to the first,
	 * where the next frame starts reading (Iris's swap passes after {@code final}). Not made into a trade of the two
	 * sides (7.4): after the copy both hold the picture, and a pass that leaves pixels unwritten (discard, a smaller
	 * viewport) reads it there next frame.
	 */
	private void keepFlipped() {
		if (Boolean.getBoolean("altshaders.debug.noKeepFlipped")) {
			return; // timing only (7.3): the next frame then reads stale history
		}
		Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> settings = set.getPackDirectives()
				.getRenderTargetDirectives().getRenderTargetSettings();
		boolean any = false;
		for (int i = 0; i < PackRenderTargetDirectives.MAX_COLOR_BUFFERS; i++) {
			RenderTargets.Target t = targets.peek(i);
			PackRenderTargetDirectives.RenderTargetSettings s = settings.get(i);
			if (t == null || t.current == 0 || s == null || s.shouldClear()) {
				continue;
			}
			if (!any) {
				any = true;
				gpu.begin("altshaders keep flipped");
			}
			gpu.copyColor(clearTarget(t, 1), 0, t.sides[0]);
		}
		if (any) {
			gpu.end();
		}
	}

	private void runComputes(List<PackProgram> computes) {
		for (PackProgram c : computes) {
			gpu.begin("altshaders " + c.name);
			gpu.useProgram(c.program());
			bindInputs(c);
			uniforms.apply(gpu, c);
			uniforms.passFog(gpu, c);
			int[] g = workGroups(c);
			gpu.dispatch(g[0], g[1], g[2]);
			gpu.memoryBarrier(0xFFFFFFFF);
			gpu.end();
		}
		if (!computes.isEmpty()) {
			targets.allWritten(); // their images may have written any buffer
		}
	}

	/** Iris's work groups: absolute, or relative to the screen in units of the local size (screen-sized by default). */
	private int[] workGroups(PackProgram c) {
		com.coredex.altshaders.pack.programs.ComputeSource s = c.computeSource;
		if (s != null && s.getWorkGroups() != null) {
			return new int[] {s.getWorkGroups().x, s.getWorkGroups().y, s.getWorkGroups().z};
		}
		int[] local = gpu.computeWorkGroupSize(c.program());
		float rx = 1;
		float ry = 1;
		if (s != null && s.getWorkGroupRelative() != null) {
			rx = s.getWorkGroupRelative().x;
			ry = s.getWorkGroupRelative().y;
		}
		return new int[] {(int) Math.ceil(Math.ceil(targets.width() * rx) / Math.max(1, local[0])),
				(int) Math.ceil(Math.ceil(targets.height() * ry) / Math.max(1, local[1])), 1};
	}

	/** A stage's passes, after its pre-flips ({@code flip.<stage>_pre.*}, applied even without passes, as Iris). */
	private void runPasses(List<Pass> passes, String stage) {
		set.getPackDirectives().getExplicitFlips(stage + "_pre").forEach((b, flip) -> {
			if (flip) {
				targets.get(b).current ^= 1;
			}
		});
		targets.allWritten(); // the game's draws since the last stage
		if (passes.isEmpty()) {
			return;
		}
		gpu.beginTimer(TimedPass.PASSES);
		runPassList(passes);
		gpu.endTimer(TimedPass.PASSES);
	}

	private void runPassList(List<Pass> passes) {
		for (Pass pass : passes) {
			runComputes(pass.computes());
			PackProgram p = pass.fragment();
			if (p == null) {
				continue;
			}
			GpuFramebuffer fb = p.framebuffer(gpu, targets, false, null);
			gpu.begin("altshaders " + p.name);
			gpu.bindFramebuffer(fb);
			// Iris: the pass is the size of the targets it writes (packs size some: size.buffer.*), then its scale.
			int passW = targets.width();
			int passH = targets.height();
			int[] outputs = p.drawBuffers();
			if (outputs.length > 0) {
				RenderTargets.Target first = targets.get(outputs[0]);
				passW = first.width;
				passH = first.height;
			}
			var vp = p.directives.getViewportScale();
			gpu.viewport((int) (passW * vp.viewportX()), (int) (passH * vp.viewportY()), (int) (passW * vp.scale()),
					(int) (passH * vp.scale()));
			// Mipmaps of what the pass reads, if it asks (Iris: made before the pass; the side keeps mipmap filtering
			// until the frame ends).
			for (int b : p.directives.getMipmappedBuffers()) {
				targets.mipmaps(targets.get(b));
			}
			draw(p, passBlend(p));
			gpu.end();
			for (int b : p.drawBuffers()) {
				RenderTargets.Target t = targets.get(b);
				t.mipsStale[t.current ^ 1] = true;
			}
			// What it wrote is what the next passes read, unless the pack says otherwise. As Iris: each buffer it
			// writes flips unless flip.<pass>.<buffer>=false, then each buffer with =true flips (again).
			Map<Integer, Boolean> flips = p.directives.getExplicitFlips();
			for (int b : p.drawBuffers()) {
				if (flips.getOrDefault(b, true)) {
					targets.get(b).current ^= 1;
				}
			}
			flips.forEach((b, flip) -> {
				if (flip) {
					targets.get(b).current ^= 1;
				}
			});
		}
	}

	/** A full-screen pass's {@code blend.<pass>} (Iris's composite passes; none: no blending). */
	private static com.coredex.altrender.api.gpu.Blend passBlend(PackProgram p) {
		com.coredex.altrender.api.gpu.Blend b = blendOf(p);
		return b == null || b.equals(com.coredex.altrender.api.gpu.Blend.NONE) ? null : b;
	}

	/** One full-screen pass with the program's samplers on the buffers' current sides. */
	private void draw(PackProgram p, com.coredex.altrender.api.gpu.Blend blend) {
		gpu.useProgram(p.program());
		gpu.blend(blend);
		gpu.depth(0, false);
		gpu.colorMask(true, true, true, true);
		bindInputs(p);
		uniforms.apply(gpu, p);
		uniforms.passFog(gpu, p);
		gpu.drawFullscreen();
	}

	/**
	 * The debug view: one buffer on the whole screen, or up to four (comma-separated) in a 2×2 grid. Names:
	 * {@code colortexN}, {@code depthtexN}, {@code shadowtexN}, {@code shadowcolorN}.
	 */
	private void drawDebugView(String names) {
		String[] list = names.split(",");
		int n = Math.min(list.length, 4);
		int w = targets.width();
		int h = targets.height();
		for (int k = 0; k < n; k++) {
			String name = list[k].trim();
			if (n > 1) {
				gpu.viewport((k & 1) * w / 2, (1 - (k >> 1)) * h / 2, w / 2, h / 2);
			}
			drawDebugBuffer(name);
		}
	}

	private void drawDebugBuffer(String name) {
		GpuTexture t;
		int depth = name.startsWith("depthtex") ? 1 : name.startsWith("shadowtex") ? 2 : 0;
		int i = 0;
		try {
			i = Integer.parseInt(name.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			// index 0
		}
		if (name.startsWith("depthtex")) {
			t = i == 0 ? targets.depth0 : i == 1 ? targets.depth1 : targets.depth2;
		} else if (name.startsWith("shadowtex")) {
			t = shadowDepth(i * 2);
		} else if (name.startsWith("shadowcolor")) {
			t = shadow != null && shadow.color(i) != null ? shadow.color(i) : black;
		} else {
			RenderTargets.Target target = targets.peek(i);
			t = target != null ? target.read() : black;
		}
		gpu.useProgram(debugView.program());
		gpu.blend(null);
		gpu.depth(0, false);
		gpu.colorMask(true, true, true, true);
		gpu.bindTexture(0, t);
		int l = debugView.location(gpu, "altshadersDepth");
		if (l >= 0) {
			gpu.uniform1i(l, depth);
		}
		gpu.drawFullscreen();
	}

	/** A program's samplers (current sides of the targets) and images, inside a pass. */
	private void bindInputs(PackProgram p) {
		for (PackProgram.Sampler s : p.samplers) {
			if (s.source() == PackProgram.Source.GAME) {
				continue;
			}
			if (s.source() == PackProgram.Source.CUSTOM && s.custom().gameName() != 0) {
				gpu.bindGameTexture(s.unit(), s.custom().gameName());
				continue;
			}
			gpu.bindTexture(s.unit(), texture(s));
		}
		bindImages(p);
	}

	/** A program's images: the pack's, and the colour buffers' current sides ({@code colorimgN}, as Iris). */
	private void bindImages(PackProgram p) {
		for (PackProgram.ImageUnit i : p.images) {
			if (i.image() != null) {
				gpu.bindImage(i.unit(), i.image().texture(), 0, 0x88BA, // GL_READ_WRITE
						i.image().info().internalTextureFormat().getGlFormat());
			} else if (i.color() >= 0) {
				RenderTargets.Target t = targets.get(i.color());
				gpu.bindImage(i.unit(), t.read(), 0, 0x88BA, t.format.getGlFormat());
				t.mipsStale[t.current] = true; // it may write there
			} else if (shadow != null && shadow.color(i.shadowColor()) != null) {
				gpu.bindImage(i.unit(), shadow.color(i.shadowColor()), 0, 0x88BA,
						shadow.colorFormat(i.shadowColor()).getGlFormat());
			}
		}
	}

	/** The texture a sampler reads (not for the game's own texture). */
	private GpuTexture texture(PackProgram.Sampler s) {
		return switch (s.source()) {
			case GAME -> null;
			case LIGHTMAP -> lightmap;
			case NORMALS -> flatNormal;
			case SPECULAR, NONE -> black;
			case NOISE -> noise;
			case COLOR -> targets.get(s.index()).read();
			case DEPTH -> s.index() == 0 ? targets.depth0 : s.index() == 1 ? targets.depth1 : targets.depth2;
			case SHADOW_DEPTH -> shadowDepth(s.index());
			case SHADOW_COLOR -> {
				GpuTexture c = shadow != null ? shadow.color(s.index()) : null;
				yield c != null ? c : white;
			}
			case CUSTOM -> s.custom().lightmap() ? lightmap : s.custom().texture();
			case CENTER_DEPTH -> centerDepth.read();
		};
	}

	/** shadowtex0/1 for a sampler ({@code index}: which × 2 + comparing), its compare mode set for this use. */
	private GpuTexture shadowDepth(int index) {
		boolean compare = (index & 1) != 0;
		if (shadow == null) {
			return compare ? shadowDepthCompare : shadowDepth;
		}
		int which = index >> 1;
		GpuTexture t = which == 1 ? shadow.depth1 : shadow.depth0;
		// Iris's shadow samplers: linear unless shadowtexNNearest, mipmapped if shadowtexNMipmap; comparing for a
		// sampler2DShadow (AltShaders picks that by the sampler's type).
		int[] filter = shadow.depthFilter(which);
		t.sampling(filter[0], filter[1], 0x812F);
		t.compare(compare ? 0x0203 : 0);
		return t;
	}

	void resized(int width, int height) {
		// Applied at the next beginWorld (the targets must not change mid-frame).
	}

	/** Something failed mid-frame: the rest of the world draws as vanilla until the pack is reloaded. */
	void broken(Throwable e) {
		broken = true;
		Log.error("shader pack pipeline failed; the world draws without it", e);
	}

	void delete() {
		for (PackProgram p : all) {
			p.delete();
		}
		for (GpuFramebuffer fb : clearTargets.values()) {
			fb.delete();
		}
		clearFramebuffer.delete();
		depthClearFramebuffer.delete();
		targets.delete();
		white.delete();
		lightmap.delete();
		flatNormal.delete();
		black.delete();
		noise.delete();
		shadowDepth.delete();
		shadowDepthCompare.delete();
		centerDepth.delete();
		if (shadow != null) {
			shadow.delete();
		}
		resources.delete();
	}
}
