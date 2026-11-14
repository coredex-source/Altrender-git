/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

/**
 * Entry point of Altrender's public API, the only part of Altrender that AltShaders and other mods compile against.
 * Plain Java: no Minecraft or loader types, stable across every supported Minecraft version.
 */
public final class AltrenderApi {

	/**
	 * Semantic version of this API. Below 1.0.0 nothing is stable yet.
	 * <p>
	 * Assigned in a static initialiser so it isn't a compile-time constant: callers read the version of the Altrender
	 * they run with, not the one they were compiled against.
	 */
	public static final String VERSION;

	static {
		VERSION = "0.3.0";
	}

	private static volatile ShaderPrograms shaderPrograms;
	private static volatile Capabilities capabilities;
	private static volatile GameState gameState;
	private static volatile BlockNames blockNames;
	private static volatile Desktop desktop;
	private static volatile CameraState camera;
	private static volatile com.coredex.altrender.api.render.FixedFunction fixedFunction;
	private static volatile com.coredex.altrender.api.gpu.Gpu gpu;
	private static volatile com.coredex.altrender.api.render.WorldPipeline worldPipeline;

	private AltrenderApi() {
	}

	/**
	 * Whether the running Altrender's API is at least {@code version} ({@code major.minor.patch}) with the same major,
	 * or below 1.0.0 the same minor (since 0.3.0). Addons check this at start and stay off when it's false.
	 */
	public static boolean supports(String version) {
		int[] have = parse(VERSION);
		int[] want = parse(version);
		if (have[0] != want[0] || have[0] == 0 && have[1] != want[1]) {
			return false;
		}
		return have[1] > want[1] || have[1] == want[1] && have[2] >= want[2];
	}

	private static int[] parse(String v) {
		String[] p = v.split("[.-]");
		int[] r = new int[3];
		for (int i = 0; i < 3 && i < p.length; i++) {
			try {
				r[i] = Integer.parseInt(p[i]);
			} catch (NumberFormatException e) {
				r[i] = 0;
			}
		}
		return r;
	}

	/**
	 * The context's capabilities and the tier's features (since 0.3.0), or {@code null} while Altrender's renderer is
	 * off. A new object after a tier switch.
	 */
	public static Capabilities capabilities() {
		return capabilities;
	}

	/** The game's fixed-function state (since 0.3.0), or {@code null} while Altrender's renderer is off. */
	public static com.coredex.altrender.api.render.FixedFunction fixedFunction() {
		return fixedFunction;
	}

	/** The world camera (since 0.3.0), or {@code null} while Altrender's renderer is off. */
	public static CameraState camera() {
		return camera;
	}

	/** The version's blocks by modern name (since 0.3.0), or {@code null} before the game is up. */
	private static volatile com.coredex.altrender.api.shaderpacks.ShaderPackMenu shaderPackMenu;
	private static volatile Runnable shaderPackScreenOpener;

	/**
	 * A shader pack addon's menu (since 0.3.0): Altrender shows it as its shader pack screen (a button in its video
	 * settings, and the O key in the world, as Iris's).
	 */
	public static void registerShaderPackMenu(com.coredex.altrender.api.shaderpacks.ShaderPackMenu menu) {
		shaderPackMenu = menu;
	}

	public static com.coredex.altrender.api.shaderpacks.ShaderPackMenu shaderPackMenu() {
		return shaderPackMenu;
	}

	/** Opens the shader pack screen (if a menu is registered and the game can show screens now). */
	public static void openShaderPackScreen() {
		Runnable r = shaderPackScreenOpener;
		if (r != null && shaderPackMenu != null) {
			r.run();
		}
	}

	/** The desktop (file manager, file chooser), or {@code null} before the window exists. */
	public static Desktop desktop() {
		return desktop;
	}

	public static BlockNames blockNames() {
		return blockNames;
	}

	/** The game's state (since 0.3.0), or {@code null} before the game is up. */
	public static GameState gameState() {
		return gameState;
	}

	private static volatile com.coredex.altrender.api.render.TerrainView terrainView;

	private static volatile com.coredex.altrender.api.render.EntityView entityView;

	/** The game's entities from other cameras (since 0.3.0), or {@code null} where the version can't. */
	public static com.coredex.altrender.api.render.EntityView entityView() {
		return entityView;
	}

	/** Altrender's terrain from other cameras (since 0.3.0), or {@code null} without Altrender's terrain. */
	public static com.coredex.altrender.api.render.TerrainView terrainView() {
		return terrainView;
	}

	/** GPU objects and drawing (since 0.3.0), or {@code null} while Altrender's renderer is off. */
	public static com.coredex.altrender.api.gpu.Gpu gpu() {
		return gpu;
	}

	/**
	 * Replaces how the world is drawn (since 0.3.0), or with {@code null} goes back to Altrender's own. The previous
	 * pipeline is {@link com.coredex.altrender.api.render.WorldPipeline#destroy destroyed}. Render thread, between
	 * frames.
	 */
	public static void setWorldPipeline(com.coredex.altrender.api.render.WorldPipeline pipeline) {
		com.coredex.altrender.api.render.WorldPipeline old = worldPipeline;
		worldPipeline = pipeline;
		if (old != null && old != pipeline) {
			old.destroy();
		}
	}

	/** The pipeline in use, or {@code null} (since 0.3.0). */
	public static com.coredex.altrender.api.render.WorldPipeline worldPipeline() {
		return worldPipeline;
	}

	/** Program building on Altrender's context (since 0.2.0), or {@code null} while Altrender's renderer is off. */
	public static ShaderPrograms shaderPrograms() {
		return shaderPrograms;
	}

	private static final java.util.List<com.coredex.altrender.api.options.OptionPageProvider> OPTION_PAGES =
			new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Adds a mod's pages to Altrender's video settings (since 0.2.0); each mod's pages appear under its own header,
	 * in registration order after Altrender's. Call from client initialisation.
	 */
	public static void registerOptionPages(com.coredex.altrender.api.options.OptionPageProvider provider) {
		OPTION_PAGES.add(provider);
	}

	/** Every registered page provider (Altrender reads this when its settings open). */
	public static java.util.List<com.coredex.altrender.api.options.OptionPageProvider> optionPageProviders() {
		return java.util.List.copyOf(OPTION_PAGES);
	}

	/** Altrender's side of the API: only Altrender calls this. */
	public static final class Host {
		private Host() {
		}

		/** Installs (or with {@code null} withdraws) the program builder. */
		public static void shaderPrograms(ShaderPrograms programs) {
			AltrenderApi.shaderPrograms = programs;
		}

		/** Installs (or with {@code null} withdraws) the fixed-function state view. */
		public static void fixedFunction(com.coredex.altrender.api.render.FixedFunction f) {
			AltrenderApi.fixedFunction = f;
		}

		/** Installs (or with {@code null} withdraws) the entity view. */
		public static void entityView(com.coredex.altrender.api.render.EntityView view) {
			AltrenderApi.entityView = view;
		}

		/** Installs (or with {@code null} withdraws) the terrain view. */
		public static void terrainView(com.coredex.altrender.api.render.TerrainView view) {
			AltrenderApi.terrainView = view;
		}

		/** Installs (or with {@code null} withdraws) the camera. */
		public static void camera(CameraState c) {
			AltrenderApi.camera = c;
		}

		/** Installs the version node's block names. */
		public static void shaderPackScreenOpener(Runnable opener) {
			AltrenderApi.shaderPackScreenOpener = opener;
		}

		public static void desktop(Desktop d) {
			AltrenderApi.desktop = d;
		}

		public static void blockNames(BlockNames names) {
			AltrenderApi.blockNames = names;
		}

		/** Installs the version node's game state. */
		public static void gameState(GameState state) {
			AltrenderApi.gameState = state;
		}

		/** Installs (or with {@code null} withdraws) the capabilities. */
		public static void capabilities(Capabilities caps) {
			AltrenderApi.capabilities = caps;
		}

		/**
		 * Installs (or with {@code null} withdraws) the GPU layer. Withdrawing it (a tier switch, the renderer
		 * stopping) first tells the pipeline to free its objects, which belong to the old one; the pipeline stays
		 * installed and makes them again on the next one.
		 */
		public static void gpu(com.coredex.altrender.api.gpu.Gpu g) {
			com.coredex.altrender.api.render.WorldPipeline p = worldPipeline;
			if (g == null && AltrenderApi.gpu != null && p != null) {
				p.destroy();
			}
			AltrenderApi.gpu = g;
		}
	}
}
