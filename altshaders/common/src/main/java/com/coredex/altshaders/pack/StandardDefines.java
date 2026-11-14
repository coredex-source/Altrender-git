/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.coredex.altshaders.pack.helpers.StringPair;
import com.coredex.altshaders.pack.properties.PackRenderTargetDirectives;

/**
 * The macros every pack source and properties file sees (development.md 6.2): the interface OptiFine documents
 * (shaders.txt, "Standard Macros") plus Iris's additions, with the values of this game and GPU. Owner's decisions
 * (2026-11-13): {@code MC_VERSION} is {@code 10710} and {@code IS_IRIS} is defined. {@code IRIS_VERSION} is the
 * pinned Iris's (1.11.4).
 */
public final class StandardDefines {

	public static final String MC_VERSION = "10710";
	/**
	 * {@code MC_VERSION} in block.properties, item.properties and entity.properties (Review): their entries are
	 * matched to the version's blocks, items and entities by modern names (Minecraft 1.20.3 and later, e.g.
	 * {@code short_grass}), so the files are read in their modern branch; packs' pre-1.13 branches name numeric ids
	 * or old names, and some (Photon) are empty.
	 */
	public static final String ID_MAP_MC_VERSION = "12111";
	public static final String IRIS_VERSION = "11104";
	/** The view-model depth scale (OptiFine/Iris: 0.125). */
	public static final String HAND_DEPTH = "0.125";

	/** Iris's render stages ({@code MC_RENDER_STAGE_*}, the {@code renderStage} uniform's values), in order. */
	public static final List<String> RENDER_STAGES = List.of("NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON",
			"STARS", "VOID", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT", "ENTITIES", "BLOCK_ENTITIES",
			"DESTROY", "OUTLINE", "DEBUG", "HAND_SOLID", "TERRAIN_TRANSLUCENT", "TRIPWIRE", "PARTICLES", "CLOUDS",
			"RAIN_SNOW", "WORLD_BORDER", "HAND_TRANSLUCENT");

	/** The {@code COLOR_SPACE_*} values (Iris's colour spaces, in its order). */
	public static final List<String> COLOR_SPACES = List.of("SRGB", "DCI_P3", "DISPLAY_P3", "REC2020", "ADOBE_RGB");

	/** Distant Horizons' block kinds ({@code DH_BLOCK_*}), defined even without it, as Iris does. */
	private static final List<String> DH_BLOCKS = List.of("UNKNOWN", "LEAVES", "STONE", "WOOD", "METAL", "DIRT", "LAVA",
			"DEEPSLATE", "SNOW", "SAND", "TERRACOTTA", "NETHER_STONE", "WATER", "GRASS", "AIR", "ILLUMINATED");

	private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.?(\\d*).*", Pattern.DOTALL);

	/**
	 * What the defines depend on, from the renderer.
	 *
	 * @param glVersion   {@code GL_VERSION}
	 * @param glslVersion {@code GL_SHADING_LANGUAGE_VERSION}
	 * @param extensions  the context's extensions ({@code GL_ARB_...})
	 */
	public record Host(String glVersion, String glslVersion, String vendor, String renderer,
			Collection<String> extensions, int mipmapLevels) {
	}

	private StandardDefines() {
	}

	public static List<StringPair> create(Host host) {
		List<StringPair> d = new ArrayList<>();
		define(d, "MC_VERSION", MC_VERSION);
		define(d, "MC_MIPMAP_LEVEL", String.valueOf(host.mipmapLevels()));
		define(d, "IRIS_VERSION", IRIS_VERSION);
		define(d, "MC_GL_VERSION", version(host.glVersion()));
		define(d, "MC_GLSL_VERSION", version(host.glslVersion()));
		define(d, os());
		define(d, vendor(host.vendor()));
		define(d, renderer(host.renderer()));
		define(d, "IS_IRIS");
		define(d, "IRIS_REQUIRES_SEPARATE_ENTITY_DRAWS");
		define(d, "MAX_COLOR_BUFFERS", String.valueOf(PackRenderTargetDirectives.MAX_COLOR_BUFFERS));
		define(d, "IRIS_HAS_TRANSLUCENCY_SORTING");
		// Block tags in block.properties (%leaves): matched through the version's tag lists (BlockNames.tags), as Iris 2.
		define(d, "IRIS_TAG_SUPPORT", "2");
		for (int i = 0; i < DH_BLOCKS.size(); i++) {
			define(d, "DH_BLOCK_" + DH_BLOCKS.get(i), String.valueOf(i));
		}
		Set<String> extensions = new LinkedHashSet<>(host.extensions()); // drivers (and RenderDoc) can repeat some
		for (String e : extensions) {
			define(d, "MC_" + e);
		}
		define(d, "MC_NORMAL_MAP");
		define(d, "MC_SPECULAR_MAP");
		define(d, "MC_RENDER_QUALITY", "1.0");
		define(d, "MC_SHADOW_QUALITY", "1.0");
		define(d, "MC_HAND_DEPTH", HAND_DEPTH);
		for (int i = 0; i < RENDER_STAGES.size(); i++) {
			define(d, "MC_RENDER_STAGE_" + RENDER_STAGES.get(i), String.valueOf(i));
		}
		for (int i = 0; i < Biomes.NAMES.size(); i++) {
			define(d, "BIOME_" + Biomes.NAMES.get(i).toUpperCase(Locale.ROOT), String.valueOf(i));
		}
		BiomeCategories[] categories = BiomeCategories.values();
		for (int i = 0; i < categories.length; i++) {
			define(d, "CAT_" + categories[i].name(), String.valueOf(i));
		}
		define(d, "PPT_NONE", "0");
		define(d, "PPT_RAIN", "1");
		define(d, "PPT_SNOW", "2");
		return List.copyOf(d);
	}

	private static void define(List<StringPair> d, String key) {
		d.add(new StringPair(key, ""));
	}

	private static void define(List<StringPair> d, String key, String value) {
		d.add(new StringPair(key, value));
	}

	/** "4.6.0 NVIDIA 550.1" → 460; "3.3 (Core Profile) Mesa" → 330. */
	static String version(String s) {
		Matcher m = VERSION.matcher(s == null ? "" : s.strip());
		if (!m.matches()) {
			return "0";
		}
		String bugfix = m.group(3).isEmpty() ? "0" : m.group(3);
		return m.group(1) + m.group(2) + bugfix;
	}

	private static String os() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("mac") || os.contains("darwin")) {
			return "MC_OS_MAC";
		} else if (os.contains("linux") || os.contains("bsd") || os.contains("unix")) {
			return "MC_OS_LINUX";
		} else if (os.contains("win")) {
			return "MC_OS_WINDOWS";
		}
		return "MC_OS_UNKNOWN";
	}

	static String vendor(String vendor) {
		String v = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
		if (v.startsWith("ati")) {
			return "MC_GL_VENDOR_ATI";
		} else if (v.startsWith("intel")) {
			return "MC_GL_VENDOR_INTEL";
		} else if (v.startsWith("nvidia")) {
			return "MC_GL_VENDOR_NVIDIA";
		} else if (v.startsWith("amd")) {
			return "MC_GL_VENDOR_AMD";
		} else if (v.startsWith("x.org")) {
			return "MC_GL_VENDOR_XORG";
		}
		return "MC_GL_VENDOR_OTHER";
	}

	static String renderer(String renderer) {
		String r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
		if (r.startsWith("amd") || r.startsWith("ati") || r.startsWith("radeon")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (r.startsWith("gallium")) {
			return "MC_GL_RENDERER_GALLIUM";
		} else if (r.startsWith("intel")) {
			return "MC_GL_RENDERER_INTEL";
		} else if (r.startsWith("geforce") || r.startsWith("nvidia")) {
			return "MC_GL_RENDERER_GEFORCE";
		} else if (r.startsWith("quadro") || r.startsWith("nvs")) {
			return "MC_GL_RENDERER_QUADRO";
		} else if (r.startsWith("mesa")) {
			return "MC_GL_RENDERER_MESA";
		} else if (r.startsWith("apple")) {
			return "MC_GL_RENDERER_APPLE";
		}
		return "MC_GL_RENDERER_OTHER";
	}

	/** {@code defines} with {@code MC_VERSION} as {@link #ID_MAP_MC_VERSION}: for the id map files. */
	public static List<StringPair> forIdMaps(Iterable<StringPair> defines) {
		List<StringPair> d = new ArrayList<>();
		for (StringPair p : defines) {
			d.add(p.key().equals("MC_VERSION") ? new StringPair("MC_VERSION", ID_MAP_MC_VERSION) : p);
		}
		return d;
	}
}
