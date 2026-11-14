/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.Capabilities;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.PackLoader;
import com.coredex.altshaders.pack.StandardDefines;

/**
 * Shared, version-independent init of AltShaders. Talks to Altrender only through {@code altrender-api}.
 */
public final class AltShaders {

	public static final String MOD_ID = "altshaders";

	/** The Altrender API version AltShaders is written against (development.md 6.1). */
	public static final String API_NEEDED = "0.3.0";

	private static Path gameDir = Path.of(".");
	private static boolean apiOk;
	private static com.coredex.altshaders.pipeline.PipelineManager pipelines;

	/** The world pipeline (pack loading, reloads), or null when AltShaders is off. */
	public static com.coredex.altshaders.pipeline.PipelineManager pipelines() {
		return pipelines;
	}

	private AltShaders() {
	}

	/** The game's folder (from the loader, before the game starts). */
	public static void setGameDir(Path dir) {
		gameDir = dir;
		PackEnv.setGameDir(dir);
		ShadersConfig.load(dir);
	}

	/** {@code <game dir>/shaderpacks}, as Iris and OptiFine. */
	public static Path shaderpacksDir() {
		return gameDir.resolve("shaderpacks");
	}

	/** Whether the running Altrender's API is one AltShaders can use. */
	public static boolean apiOk() {
		return apiOk;
	}

	/**
	 * Altrender's current capabilities applied to the pack loader ({@link PackEnv#setCaps}, for the feature flags),
	 * and the values the standard defines need; {@code null} while Altrender's renderer is off.
	 */
	public static StandardDefines.Host host() {
		Capabilities c = AltrenderApi.capabilities();
		if (c == null) {
			return null;
		}
		PackEnv.setCaps(new PackEnv.Caps(c.imageLoadStore(), c.drawBuffersBlend(), c.compute(), c.tessellation(),
				c.storageBuffers()));
		// b1.7.3 has no mipmaps: MC_MIPMAP_LEVEL 0.
		return new StandardDefines.Host(c.glVersion(), c.glslVersion(), c.vendor(), c.renderer(), c.extensions(), 0);
	}

	/** Logs the startup banner (development.md 0.1); makes the shader pack folder and lists what's in it. */
	public static void onGameStart() {
		apiOk = AltrenderApi.supports(API_NEEDED);
		Log.info("loaded api=" + AltrenderApi.VERSION + (apiOk ? "" : " (AltShaders needs " + API_NEEDED
				+ "; shader packs are off)"));
		Path dir = shaderpacksDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Log.warn("can't create " + dir + ": " + e.getMessage());
		}
		List<String> packs = PackLoader.list(dir);
		Log.info(packs.size() + " shader pack(s) in " + dir + (packs.isEmpty() ? "" : ": " + String.join(", ", packs)));
		if (apiOk) {
			pipelines = new com.coredex.altshaders.pipeline.PipelineManager();
			AltrenderApi.setWorldPipeline(pipelines);
			AltrenderApi.registerShaderPackMenu(new ShaderPackMenuImpl(pipelines));
			AltrenderApi.registerOptionPages(optionPages());
		}
	}

	/** AltShaders' page in the video settings: its own options (not a pack's; those are on the shader pack screen). */
	private static com.coredex.altrender.api.options.OptionPageProvider optionPages() {
		return new com.coredex.altrender.api.options.OptionPageProvider() {
			@Override
			public String modId() {
				return "altshaders";
			}

			@Override
			public String modName() {
				return "AltShaders";
			}

			@Override
			public List<com.coredex.altrender.api.options.OptionPage> pages() {
				com.coredex.altrender.api.options.OptionStorage storage = flags -> ShadersConfig.save();
				var backFaces = com.coredex.altrender.api.options.Option.<Boolean>builder("shadow_back_faces")
						.name("Shadow back-face culling")
						.tooltip("Leaves out the backs of solid blocks (the faces turned away from the sun or moon) "
								+ "in the shadow map: a little less shadow-map work. Shadows stay the same except "
								+ "where the shadow map's range cuts a shape open. Off draws both sides, as Iris.")
						.impact(com.coredex.altrender.api.options.OptionImpact.LOW)
						.control(new com.coredex.altrender.api.options.Control.TickBox())
						.binding(ShadersConfig::shadowBackFaces, ShadersConfig::setShadowBackFaces)
						.storage(storage)
						.build();
				return List.of(new com.coredex.altrender.api.options.OptionPage("altshaders.main", "Shadows",
						List.of(com.coredex.altrender.api.options.OptionGroup.of(backFaces))));
			}
		};
	}

	private static boolean reloadKeyDown;

	/** The reload key (R, in the world with no screen open, as Iris): the pack reloads on the next frame. */
	public static void reloadKey(boolean down, boolean inWorld) {
		if (down && !reloadKeyDown && inWorld && pipelines != null) {
			Log.info("reloading the shader pack (R)");
			pipelines.requestReload();
		}
		reloadKeyDown = down;
	}
}
