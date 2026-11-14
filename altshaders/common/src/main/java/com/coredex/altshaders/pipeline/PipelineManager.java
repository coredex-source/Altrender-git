/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.List;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.GameState;
import com.coredex.altrender.api.ShaderPrograms;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.render.RenderPhase;
import com.coredex.altrender.api.render.WorldPipeline;
import com.coredex.altshaders.AltShaders;
import com.coredex.altshaders.Log;
import com.coredex.altshaders.ShadersConfig;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.PackLoader;
import com.coredex.altshaders.pack.StandardDefines;
import com.coredex.altshaders.pack.helpers.StringPair;
import com.coredex.altshaders.pack.materialmap.NamespacedId;

/**
 * AltShaders' world pipeline (development.md 6.4), installed once: it loads the selected pack at the first world
 * frame that wants it, builds the pipeline for the world's dimension (again when the dimension, the pack or
 * Altrender's tier changes) and hands every call to it. With no pack the world draws as Altrender's own. A pack
 * that fails to load or build is logged once and left off until the selection changes or a reload.
 */
public final class PipelineManager implements WorldPipeline {

	private PackLoader.Loaded loaded;
	private String failed;
	private String lastError;
	private PackPipeline pipeline;
	private String dimension;
	private Gpu gpu;
	private boolean reload;

	/** Load the pack again (its files and options) at the next world frame. */
	public void requestReload() {
		reload = true;
	}

	/** The last load or build failure, for the pack page; null after a success. */
	public String lastError() {
		return lastError;
	}

	/** The loaded pack, or null. */
	public PackLoader.Loaded loaded() {
		return loaded;
	}

	/** {@code -Daltshaders.debug.reloadTest=N}: N reloads, one every 60 frames (the 6.3 leak check). */
	private static final int RELOAD_TEST = Integer.getInteger("altshaders.debug.reloadTest", 0);
	private int reloadFrames;
	private int reloadsDone;

	@Override
	public void beginWorld(int width, int height, float partialTick) {
		if (RELOAD_TEST > 0 && reloadsDone < RELOAD_TEST && ++reloadFrames % 60 == 0) {
			reloadsDone++;
			Log.info("reload test: reload " + reloadsDone + " of " + RELOAD_TEST);
			requestReload();
		}
		try {
			ensure(width, height);
			if (pipeline != null) {
				pipeline.beginWorld(width, height);
			}
		} catch (RuntimeException e) {
			fail("frame start", e);
		}
	}

	private void ensure(int width, int height) {
		String want = ShadersConfig.selected();
		if (reload) {
			reload = false;
			closePack();
			failed = null;
		}
		if (want == null) {
			closePack();
			return;
		}
		if (loaded == null || !loaded.name().equals(want)) {
			closePack();
			if (want.equals(failed)) {
				return;
			}
			load(want);
			if (loaded == null) {
				return;
			}
		}
		Gpu g = AltrenderApi.gpu();
		ShaderPrograms builder = AltrenderApi.shaderPrograms();
		if (g == null || builder == null) {
			return;
		}
		GameState state = AltrenderApi.gameState();
		String dim = state != null && state.dimension() != null ? state.dimension() : "minecraft:overworld";
		if (pipeline == null || g != gpu || !dim.equals(dimension)) {
			closePipeline();
			long start = System.nanoTime();
			PackPipeline p = new PackPipeline(g, builder, loaded.pack().getProgramSet(new NamespacedId(dim)), width,
					height);
			if (!p.finish()) {
				lastError = p.failure();
				p.delete();
				failed = loaded.name();
				Log.warn("shader pack " + loaded.name() + " is off: a program failed to build");
				closePack();
				return;
			}
			pipeline = p;
			gpu = g;
			dimension = dim;
			lastError = null;
			Log.info("shader pack " + loaded.name() + " ready for " + dim + " in "
					+ (System.nanoTime() - start) / 1_000_000 + " ms (" + (builder.parallel() ? "parallel" : "serial")
					+ " compile)");
		}
	}

	private void load(String name) {
		StandardDefines.Host host = AltShaders.host();
		if (host == null) {
			return;
		}
		List<StringPair> defines = StandardDefines.create(host);
		try {
			loaded = PackLoader.load(AltShaders.shaderpacksDir(), name, PackEnv.optionQueue(), false, defines);
			PackEnv.optionQueue().clear();
		} catch (Exception e) {
			failed = name;
			lastError = e.getMessage();
			Log.warn("shader pack " + name + " didn't load: " + e.getMessage());
		}
	}

	/** Iris's hand depth while a pack is on: composites see the world's depth, the hand at 0.4375–0.5625. */
	@Override
	public float handDepth() {
		return pipeline != null ? 0.125f : 0;
	}

	@Override
	public boolean hides(com.coredex.altrender.api.render.VanillaPart part) {
		return pipeline != null && pipeline.hides(part);
	}

	@Override
	public com.coredex.altrender.api.render.TerrainPipeline terrain() {
		return pipeline != null ? pipeline.terrain() : null;
	}

	@Override
	public void cameraReady() {
		if (pipeline != null) {
			try {
				pipeline.cameraReady();
			} catch (RuntimeException e) {
				fail("camera", e);
			}
		}
	}

	@Override
	public void phase(RenderPhase phase) {
		if (pipeline != null) {
			try {
				pipeline.phase(phase);
			} catch (RuntimeException e) {
				fail("phase " + phase, e);
			}
		}
	}

	@Override
	public void fixedFunction(com.coredex.altrender.api.render.FixedFunction.Change change) {
		if (pipeline != null) {
			try {
				pipeline.fixedFunction(change);
			} catch (RuntimeException e) {
				fail("fixed-function change " + change, e);
			}
		}
	}

	@Override
	public boolean handFirst() {
		return pipeline != null; // Iris: the solid hand before the deferred passes
	}

	@Override
	public boolean entity(String type, int blockLight, int skyLight) {
		if (pipeline != null) {
			try {
				return pipeline.entity(type, blockLight, skyLight);
			} catch (RuntimeException e) {
				fail("entity " + type, e);
			}
		}
		return false;
	}

	@Override
	public boolean particlesLate() {
		return pipeline != null && pipeline.particlesLate();
	}

	@Override
	public boolean entityColor(float r, float g, float b, float a) {
		if (pipeline != null) {
			try {
				return pipeline.entityColor(r, g, b, a);
			} catch (RuntimeException e) {
				fail("entity colour", e);
			}
		}
		return false;
	}

	@Override
	public void entityEyes(boolean on) {
		if (pipeline != null) {
			try {
				pipeline.entityEyes(on);
			} catch (RuntimeException e) {
				fail("entity eyes", e);
			}
		}
	}

	@Override
	public boolean blockEntity(int blockKey, int blockLight, int skyLight) {
		if (pipeline != null) {
			try {
				return pipeline.blockEntity(blockKey, blockLight, skyLight);
			} catch (RuntimeException e) {
				fail("block entity " + blockKey, e);
			}
		}
		return false;
	}

	@Override
	public void endWorld() {
		if (pipeline != null) {
			try {
				pipeline.endWorld();
			} catch (RuntimeException e) {
				fail("frame end", e);
			}
		}
	}

	/** A failure in a frame: the pipeline goes (the world draws as Altrender's own) until a reload. */
	private void fail(String where, RuntimeException e) {
		Log.error("shader pack pipeline failed at " + where + "; the pack is off until reloaded", e);
		lastError = where + ": " + e;
		if (loaded != null) {
			failed = loaded.name();
		}
		Gpu g = AltrenderApi.gpu();
		if (g != null) {
			try {
				g.gameDraws(null, 0);
			} catch (RuntimeException ignored) {
				// best effort
			}
		}
		closePack();
	}

	private void closePipeline() {
		if (pipeline != null) {
			try {
				pipeline.delete();
			} catch (RuntimeException e) {
				Log.warn("shader pack pipeline: cleanup failed: " + e);
			}
			pipeline = null;
		}
	}

	private void closePack() {
		closePipeline();
		if (loaded != null) {
			loaded.close();
			loaded = null;
		}
	}

	@Override
	public void destroy() {
		// The renderer is switching tier or stopping: free the GPU side; the pack stays loaded.
		closePipeline();
	}
}
