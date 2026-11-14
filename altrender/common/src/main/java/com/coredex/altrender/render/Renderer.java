/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.Log;
import com.coredex.altrender.caps.Feature;
import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.caps.Tier;
import com.coredex.altrender.caps.TierChoice;
import com.coredex.altrender.debug.FrameStats;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.debug.GpuTimers;
import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.gl.GlTrackerCheck;
import com.coredex.altrender.platform.FrameHooks;
import com.coredex.altrender.settings.Config;
import com.coredex.altrender.platform.BlockMesher;
import com.coredex.altrender.strategy.Strategies;
import com.coredex.altrender.terrain.TerrainRenderer;

/**
 * Altrender's renderer: installed once the game's context exists, then called through {@link FrameHooks}. Phase 1
 * draws nothing; the game renders exactly as vanilla.
 */
public final class Renderer implements FrameHooks {

	private static final int FRAME = 1;
	private static final int WORLD = 2;
	private static final int CAMERA = 4;
	private static final int TERRAIN_0 = 8;
	private static final int TERRAIN_1 = 16;

	/** GPU timer passes. Until Phase 2 they time the game's own stages, a baseline for Altrender's passes. */
	private static final int T_FRAME = 0;
	private static final int T_WORLD = 1;
	private static final int T_TERRAIN_0 = 2;
	private static final String[] GROUPS = {"frame", "world", "terrain0", "terrain1", "sky", "entities", "particles",
			"weather", "clouds", "hand", "hud", "shadow", "pack"};
	/** A pipeline's own passes (API {@code TimedPass}): the shadow pass, its full-screen and compute passes. */
	private static final int T_PIPELINE = 11;
	/** The game's own stages, timed for the profiling pass (development.md 3.1). */
	public static final int P_SKY = 4;
	public static final int P_ENTITIES = 5;
	public static final int P_PARTICLES = 6;
	public static final int P_WEATHER = 7;
	public static final int P_CLOUDS = 8;
	public static final int P_HAND = 9;
	public static final int P_HUD = 10;

	private static volatile FrameHooks hooks = FrameHooks.NONE;
	private static Renderer instance;
	private static boolean installed;
	private static String notice;
	private static Config config = Config.DEFAULTS;
	private static long lastIdleFrame;

	private final GlCaps caps;
	private TierChoice tier;
	private BlockMesher mesher;
	/** {@code null} unless switched on ({@code -Daltrender.debug.gpuTimers}; later F3 and benchmarks). */
	private GpuTimers timers;
	/** Per-pass GPU timers asked for by the debug screen's GPU breakdown (made and dropped between frames). */
	private static volatile boolean timersWanted;
	private final boolean timersForced;
	private Strategies strategies;
	private TerrainRenderer terrain;
	private com.coredex.altrender.ui.UiRenderer ui;
	private boolean resourceTestPending = Boolean.getBoolean("altrender.debug.resourceTest");
	private int hooksSeen;
	private boolean hooksLogged;
	private long frames;

	/** The API's GPU layer on the current strategies (development.md 6.1). */
	private ApiGpu gpu;
	private final ApiCamera camera = new ApiCamera();
	private final ApiFixedFunction fixedFunction = new ApiFixedFunction();
	/** The game's framebuffer size, from {@link #resized} (0 until the first one: read from the viewport then). */
	private int width;
	private int height;

	private Renderer(GlCaps caps, TierChoice tier) {
		this.caps = caps;
		this.tier = tier;
		boolean gpuTimers = Boolean.getBoolean("altrender.debug.gpuTimers");
		timers = gpuTimers && caps.timerQuery ? new GpuTimers(true, GROUPS) : null;
		timersForced = timers != null;
		strategies = Strategies.create(tier, caps);
		installApi();
	}

	/** The API's services on the current strategies (development.md 4.5). */
	private void installApi() {
		ShaderProgramBuilds builds = new ShaderProgramBuilds(strategies.program());
		com.coredex.altrender.api.AltrenderApi.Host.shaderPrograms(builds);
		com.coredex.altrender.api.AltrenderApi.Host.capabilities(ApiCapabilities.of(caps, tier.tier()));
		gpu = new ApiGpu(strategies);
		com.coredex.altrender.api.AltrenderApi.Host.gpu(gpu);
		com.coredex.altrender.api.AltrenderApi.Host.camera(camera);
		com.coredex.altrender.api.AltrenderApi.Host.entityView((target, x, y, w, h, mv, proj, cx, cy, cz, dist, prog,
				entities, player) -> gpu != null && gpu.drawEntities(target, x, y, w, h, mv, proj, cx, cy, cz, dist, prog,
						entities, player));
		com.coredex.altrender.api.AltrenderApi.Host.terrainView((target, x, y, w, h, mv, proj, cx, cy, cz, dist, pass, prog,
				blend, buffers, culling) -> {
			TerrainRenderer t = terrain();
			return t == null ? 0 : t.drawView(target.name(), x, y, w, h, mv, proj, cx, cy, cz, dist,
					switch (pass) {
						case SOLID -> 1;
						case CUTOUT -> 2;
						default -> 3;
					}, prog, blend, buffers, culling);
		});
		com.coredex.altrender.api.AltrenderApi.Host.fixedFunction(fixedFunction);
		int test = Integer.getInteger("altrender.debug.programTest", 0);
		if (test > 0) {
			builds.selfTest(test);
		}
	}

	/**
	 * Probes the context current on this thread, chooses the tier and installs the renderer unless the tier is
	 * {@link Tier#OFF}. Called by the mc layer right after the game creates its window; later calls do nothing.
	 *
	 * @param window  the window system, for the log (e.g. {@code sdl3})
	 * @param gameDir the game directory (config, {@code altrender/caps.txt})
	 */
	public static synchronized void install(String window, Path gameDir) {
		if (installed) {
			return;
		}
		installed = true;
		GlCaps caps = GlCaps.probe();
		GlStateTracker.contextCreated();
		Log.info("gl: vendor=" + caps.vendor + " renderer=" + caps.renderer + " version=" + caps.version + " profile="
				+ caps.profile + " window=" + window + " lwjgl=" + org.lwjgl.Version.VERSION_MAJOR + "."
				+ org.lwjgl.Version.VERSION_MINOR + "." + org.lwjgl.Version.VERSION_REVISION);
		for (String line : caps.table()) {
			Log.info("caps: " + line);
		}
		String override = System.getProperty("altrender.tier");
		if (override != null && !override.equals("gl33") && !override.equals("gl45")) {
			Log.warn("-Daltrender.tier=" + override + " ignored (gl33 or gl45)");
			override = null;
		}
		config = Config.load(gameDir);
		TierChoice choice = TierChoice.choose(caps, config.tier(), override,
				"off".equals(System.getProperty("altrender.renderer")));
		String tierLine = "tier=" + choice.tier() + " (" + choice.reason() + ")";
		if (choice.tier() == Tier.OFF) {
			Log.info(tierLine + ": renderer off, the game renders as vanilla");
			if (!choice.reason().startsWith("-D")) {
				notice = "Altrender is off: " + choice.reason();
			}
		} else {
			Log.info(tierLine + " features=" + keys(choice) + (choice.notes().isEmpty() ? "" : " " + choice.notes()));
		}
		writeCaps(gameDir, caps, tierLine, choice);
		if (choice.tier() != Tier.OFF) {
			try {
				if (Boolean.getBoolean("altrender.debug.failInit")) {
					throw new IllegalStateException("-Daltrender.debug.failInit");
				}
				GlDebug.install(caps);
				com.coredex.altrender.immediate.ImmediateStream.install();
				instance = new Renderer(caps, choice);
				hooks = instance;
			} catch (RuntimeException | LinkageError e) {
				fail("renderer setup failed", e);
			}
		}
	}

	/**
	 * Graceful failure (development.md 3.8): Altrender steps aside, the game renders as vanilla, one log line (with
	 * the cause) and the player notice. Everything Altrender made is released where possible.
	 */
	private static void fail(String what, Throwable e) {
		Log.error("Altrender: " + what + "; the game renders as vanilla: " + e, e);
		notice = "Altrender is off: " + what + " (see the log)";
		hooks = FrameHooks.NONE;
		com.coredex.altrender.api.AltrenderApi.Host.shaderPrograms(null);
		com.coredex.altrender.api.AltrenderApi.Host.capabilities(null);
		try {
			com.coredex.altrender.api.AltrenderApi.Host.gpu(null);
		} catch (RuntimeException ignored) {
			// the pipeline's own cleanup failed; ours below still runs
		}
		Renderer r = instance;
		instance = null;
		try {
			if (r != null && r.terrain != null) {
				r.terrain.delete();
			}
			if (r != null && r.gpu != null) {
				r.gpu.delete();
			}
			if (r != null && r.strategies != null) {
				r.strategies.delete();
			}
			com.coredex.altrender.immediate.ImmediateStream.delete();
			com.coredex.altrender.immediate.ListCapture.deleteAll();
		} catch (RuntimeException ignored) {
			// best effort
		}
	}

	/**
	 * Switches the GL tier at run time (development.md 3.8; the settings screen in Phase 5): the strategies and the
	 * terrain are rebuilt on the new tier for the same world. Main thread, between frames.
	 */
	public static synchronized void switchTier(Tier newTier) {
		Renderer r = instance;
		if (r == null || newTier == Tier.OFF || newTier == r.tier.tier() || newTier == Tier.GL45 && !r.caps.gl45()) {
			return;
		}
		com.coredex.altrender.platform.WorldView world = r.terrain != null ? r.terrain.world() : null;
		// The pipeline's objects live on the old strategies: it goes first (it reloads on the new tier itself).
		com.coredex.altrender.api.AltrenderApi.Host.gpu(null);
		r.gpu.delete();
		if (r.terrain != null) {
			r.terrain.delete();
			r.terrain = null;
		}
		if (r.ui != null) {
			r.ui.delete();
			r.ui = null;
		}
		r.strategies.delete();
		String before = com.coredex.altrender.debug.GlObjectCounts.describe(r.strategies.counts().snapshot());
		TierChoice chosen = TierChoice.choose(r.caps, "auto", newTier == Tier.GL33 ? "gl33" : "gl45", false);
		r.tier = new TierChoice(newTier, "switched at run time", chosen.features(), chosen.notes());
		r.strategies = Strategies.create(r.tier, r.caps);
		com.coredex.altrender.api.AltrenderApi.Host.shaderPrograms(new ShaderProgramBuilds(r.strategies.program()));
		com.coredex.altrender.api.AltrenderApi.Host.capabilities(ApiCapabilities.of(r.caps, newTier));
		r.gpu = new ApiGpu(r.strategies);
		com.coredex.altrender.api.AltrenderApi.Host.gpu(r.gpu);
		if (r.mesher != null) {
			r.terrain = new TerrainRenderer(r.strategies, r.mesher, config, packTerrain());
			r.terrain.setWorld(world);
		}
		Log.info("tier switched to " + newTier + " (objects left on the old tier: " + before + ")");
	}

	/** The frame's solid hand was drawn before the translucent terrain (a pipeline's {@code handFirst}). */
	private static boolean handDrawnFirst;

	/** Whether the hand draws before the translucent terrain this frame (and hasn't yet). */
	public static boolean handFirstDue() {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		return !handDrawnFirst && p != null && p.handFirst();
	}

	/** This frame's particle order: -1 not asked yet, 0 the game's, 1 after the translucent terrain. */
	private static int particlesLate = -1;

	/** Whether the game's particles wait for the translucent terrain this frame (a pipeline's {@code particlesLate}). */
	public static boolean particlesLate() {
		if (particlesLate < 0) {
			com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
			particlesLate = p != null && p.particlesLate() ? 1 : 0;
		}
		return particlesLate == 1;
	}

	/** The early hand is drawn: the game's own hand at the end is skipped. */
	public static void handDrawnFirst() {
		handDrawnFirst = true;
	}

	/** Whether the game's hand at the end of the world is skipped (it was drawn early). */
	public static boolean skipLateHand() {
		return handDrawnFirst;
	}

	/** Start or end of one of the game's own stages ({@code P_*}): a debug group and, when on, its GPU timer. */
	public static void stage(int pass, boolean start) {
		Renderer r = instance;
		if (r != null && hooks != FrameHooks.NONE) {
			if (start) {
				r.begin(pass);
			} else {
				r.end(pass);
			}
			com.coredex.altrender.api.render.RenderPhase phase = switch (pass) {
				case P_SKY -> com.coredex.altrender.api.render.RenderPhase.SKY;
				case P_ENTITIES -> com.coredex.altrender.api.render.RenderPhase.ENTITIES;
				case P_PARTICLES -> com.coredex.altrender.api.render.RenderPhase.PARTICLES;
				case P_WEATHER -> com.coredex.altrender.api.render.RenderPhase.RAIN_SNOW;
				case P_CLOUDS -> com.coredex.altrender.api.render.RenderPhase.CLOUDS;
				case P_HAND -> com.coredex.altrender.api.render.RenderPhase.HAND_SOLID;
				default -> null; // the HUD isn't part of the world
			};
			if (pass == P_HAND && start) {
				handDepth(true);
			}
			if (phase != null) {
				phase(start ? phase : com.coredex.altrender.api.render.RenderPhase.NONE);
			}
			if (pass == P_HAND && !start) {
				handDepth(false);
			}
		}
	}

	/**
	 * Makes the terrain again on the same tier and world (settings fixed when it's made: mesh threads, GPU-written
	 * commands; development.md 5.2). Main thread, between frames.
	 */
	public static synchronized void rebuildTerrain() {
		Renderer r = instance;
		if (r == null || r.terrain == null || r.mesher == null) {
			return;
		}
		com.coredex.altrender.platform.WorldView world = r.terrain.world();
		r.terrain.delete();
		r.terrain = new TerrainRenderer(r.strategies, r.mesher, config, packTerrain());
		r.terrain.setWorld(world);
		Log.info("terrain: rebuilt for new settings");
	}

	/** The world pipeline's terrain (development.md 6.6), or null. */
	private static com.coredex.altrender.api.render.TerrainPipeline packTerrain() {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		return p != null ? p.terrain() : null;
	}

	/** Meshes again when the pipeline's terrain came, went or changed (another pack). Start of a world render. */
	private void followPackTerrain() {
		if (terrain == null || mesher == null) {
			return;
		}
		com.coredex.altrender.api.render.TerrainPipeline t = packTerrain();
		int want = t != null ? t.revision() : -1;
		if (want != terrain.packRevision()) {
			com.coredex.altrender.platform.WorldView world = terrain.world();
			terrain.delete();
			terrain = new TerrainRenderer(strategies, mesher, config, t);
			terrain.setWorld(world);
			Log.info("terrain: " + (t != null ? "a shader pack's (extended vertices)" : "Altrender's own") + " from now on");
		}
	}

	/** The debug screen's GPU breakdown is shown (true) or not: per-pass timers on or off from the next frame. */
	public static void wantGpuTimers(boolean on) {
		timersWanted = on;
	}

	/** The names of the timed passes; index 0 is the whole frame. */
	public static String[] gpuPassNames() {
		return GROUPS.clone();
	}

	/** Each pass's GPU milliseconds per frame (last second's average), or {@code null} without timers. */
	public double[] gpuPassMs() {
		GpuTimers t = timers;
		if (t == null) {
			return null;
		}
		double[] ms = new double[GROUPS.length];
		for (int i = 0; i < ms.length; i++) {
			ms[i] = t.lastMs(i);
		}
		return ms;
	}

	/** The installed renderer, or {@code null}. */
	public static Renderer instance() {
		return instance;
	}

	/** The UI toolkit's renderer (development.md 5.1), made on first use; {@code null} while Altrender is off. */
	public static com.coredex.altrender.ui.UiRenderer ui() {
		Renderer r = instance;
		if (r == null) {
			return null;
		}
		if (r.ui == null) {
			r.ui = new com.coredex.altrender.ui.UiRenderer(r.strategies);
		}
		return r.ui;
	}

	/** The hooks to call: {@link FrameHooks#NONE} unless the renderer is installed. */
	public static FrameHooks hooks() {
		return hooks;
	}

	public static Config config() {
		return config;
	}

	/**
	 * After each frame's swap (development.md 3.6, 5.2): minimised, at most {@code idleFps}; in the background, at
	 * most {@code unfocusedFps}; else at most {@code maxFps} (0 / 260 = no cap). Precise and without spinning.
	 */
	public static void idlePacing(boolean visible, boolean focused) {
		int max = config.maxFps();
		int fps = !visible ? config.idleFps() : !focused ? config.unfocusedFps() : max > 0 && max < 260 ? max : 0;
		long now = System.nanoTime();
		if (fps > 0) {
			long target = lastIdleFrame + 1_000_000_000L / fps;
			if (target - now > 0 && target - now < 1_000_000_000L) {
				FramePacer.waitUntil(target);
				now = System.nanoTime();
			}
		}
		lastIdleFrame = now;
	}

	/** A message for the player when Altrender had to switch itself off, else {@code null}. */
	public static String notice() {
		return notice;
	}

	public GlCaps caps() {
		return caps;
	}

	public TierChoice tier() {
		return tier;
	}

	public Strategies strategies() {
		return strategies;
	}

	/**
	 * Starts Altrender's terrain (development.md 2.4) with the node's block mesher, unless
	 * {@code -Daltrender.debug.terrain.disable} keeps the game's own.
	 */
	public static synchronized void enableTerrain(BlockMesher mesher) {
		if (instance == null || instance.terrain != null) {
			return;
		}
		if (Boolean.getBoolean("altrender.debug.terrain.disable")) {
			Log.info("terrain: the game's own (-Daltrender.debug.terrain.disable)");
			return;
		}
		instance.mesher = mesher;
		try {
			instance.terrain = new TerrainRenderer(instance.strategies, mesher, config);
		} catch (RuntimeException | LinkageError e) {
			fail("terrain setup failed", e);
		}
	}

	/** Altrender's terrain, or {@code null} when the game draws its own. */
	public static TerrainRenderer terrain() {
		Renderer r = instance;
		return r != null ? r.terrain : null;
	}

	@Override
	public void beginFrame(float partialTick) {
		handDrawnFirst = false;
		particlesLate = -1;
		hooksSeen |= FRAME;
		check("frame start");
		begin(T_FRAME);
	}

	@Override
	public void beginWorld(float partialTick) {
		hooksSeen |= WORLD;
		begin(T_WORLD);
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		if (p != null) {
			if (width == 0) {
				int[] vp = new int[4];
				org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, vp);
				width = vp[2];
				height = vp[3];
			}
			p.beginWorld(width, height, partialTick);
			fixedFunction.listen(true);
		}
		followPackTerrain();
	}

	/** The version node's texture lookup by modern resource path, for packs' resource textures (development.md 6.4). */
	public static void gameTextures(java.util.function.ToIntFunction<String> lookup) {
		ApiGpu.gameTextures = lookup;
	}

	private static final float[] handProjection = new float[16];
	private static boolean handProjectionSet;

	/** The version node's entity render for other cameras (development.md 6.7). */
	public static void entityDrawer(com.coredex.altrender.platform.EntityDrawer drawer) {
		ApiGpu.entityDrawer = drawer;
	}

	/** True while entities are drawn for another camera (the API's entity view). */
	public static boolean otherView() {
		return ApiGpu.otherView;
	}

	/** In the other view: whether the world's entities and block entities draw (else the player alone). */
	public static boolean otherViewEntities() {
		return ApiGpu.otherViewEntities;
	}

	/** In the other view: whether the player draws too, even in first person (a pack's shadowPlayer). */
	public static boolean otherViewPlayer() {
		return ApiGpu.otherViewPlayer;
	}

	/** Whether the world pipeline leaves the game's own {@code part} out (clouds, weather, sun, moon, stars). */
	public static boolean hides(com.coredex.altrender.api.render.VanillaPart part) {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		return instance != null && p != null && p.hides(part);
	}

	/** Whether the game's depth clear before the hand is skipped (a pipeline's {@code handDepth}). */
	public static boolean skipHandDepthClear() {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		return instance != null && p != null && p.handDepth() > 0;
	}

	/** Around the hand: its clip-space depth squeezed by the pipeline's {@code handDepth} (Iris's hand depth). */
	private static void handDepth(boolean start) {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		float f = p != null ? p.handDepth() : 0;
		int mode = com.coredex.altrender.gl.GlStateTracker.matrixMode();
		if (start && f > 0) {
			com.coredex.altrender.gl.GlStateTracker.matrix(0x1701, handProjection);
			float[] m = handProjection.clone();
			m[2] *= f;
			m[6] *= f;
			m[10] *= f;
			m[14] *= f;
			org.lwjgl.opengl.GL11.glMatrixMode(0x1701);
			org.lwjgl.opengl.GL11.glLoadMatrixf(m);
			org.lwjgl.opengl.GL11.glMatrixMode(mode);
			handProjectionSet = true;
		} else if (!start && handProjectionSet) {
			handProjectionSet = false;
			org.lwjgl.opengl.GL11.glMatrixMode(0x1701);
			org.lwjgl.opengl.GL11.glLoadMatrixf(handProjection);
			org.lwjgl.opengl.GL11.glMatrixMode(mode);
		}
	}

	/** Tells the pipeline (if any) which phase the world render is in (development.md 6.1). */
	public static void phase(com.coredex.altrender.api.render.RenderPhase phase) {
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		if (p != null) {
			p.phase(phase);
		}
	}

	@Override
	public void cameraReady() {
		hooksSeen |= CAMERA;
		check("camera ready");
		com.coredex.altrender.api.render.WorldPipeline pipeline = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		if (pipeline != null) {
			camera.capture();
			pipeline.cameraReady();
		}
		if (resourceTestPending) {
			// Once, in the middle of a real frame: the tracker check that follows shows the game's state came back.
			resourceTestPending = false;
			strategies.beginPass("resource-test");
			ResourceSelfTest.run(strategies);
			strategies.endPass();
			check("after resource test");
		}
	}

	@Override
	public void terrainLayer(int layer) {
		hooksSeen |= layer == 0 ? TERRAIN_0 : TERRAIN_1;
		check(layer == 0 ? "terrain0 start" : "terrain1 start");
		phase(layer == 0 ? com.coredex.altrender.api.render.RenderPhase.TERRAIN_SOLID
				: com.coredex.altrender.api.render.RenderPhase.TERRAIN_TRANSLUCENT);
		begin(T_TERRAIN_0 + layer);
	}

	@Override
	public void terrainLayerEnd(int layer) {
		end(T_TERRAIN_0 + layer);
		phase(com.coredex.altrender.api.render.RenderPhase.NONE);
		check(layer == 0 ? "terrain0 end" : "terrain1 end");
	}

	@Override
	public void endWorld() {
		fixedFunction.listen(false);
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		if (p != null) {
			p.endWorld();
		}
		end(T_WORLD);
		check("world end");
	}

	/** {@code -Daltrender.debug.objectCounts=N}: the GL object counts logged every N frames (leak checks). */
	private static final int OBJECT_COUNTS_EVERY = Integer.getInteger("altrender.debug.objectCounts", 0);
	private long framesEnded;

	@Override
	public void endFrame() {
		if (OBJECT_COUNTS_EVERY > 0 && ++framesEnded % OBJECT_COUNTS_EVERY == 0) {
			Log.info("gl objects at frame " + framesEnded + ": "
					+ com.coredex.altrender.debug.GlObjectCounts.describe(strategies.counts().snapshot()));
		}
		end(T_FRAME);
		check("frame end");
		if (timers != null) {
			timers.endFrame();
		}
		com.coredex.altrender.debugscreen.DebugScreen.frame();
		if (!timersForced && caps.timerQuery && timersWanted != (timers != null)) {
			if (timers == null) {
				timers = new GpuTimers(false, GROUPS);
			} else {
				timers.delete();
				timers = null;
			}
		}
		FrameStats.endFrame();
		strategies.frameEnd();
		frames++;
		if (!hooksLogged && (hooksSeen & WORLD) != 0) {
			hooksLogged = true;
			Log.info("renderer: hooks reached at the first world frame: " + seen());
		}
	}

	@Override
	public void resized(int width, int height) {
		this.width = width;
		this.height = height;
		com.coredex.altrender.api.render.WorldPipeline p = com.coredex.altrender.api.AltrenderApi.worldPipeline();
		if (p != null) {
			p.resized(width, height);
		}
	}

	@Override
	public void shutdown() {
		Log.info("renderer: shutdown after " + frames + " frames");
		com.coredex.altrender.api.AltrenderApi.Host.shaderPrograms(null);
		com.coredex.altrender.api.AltrenderApi.Host.capabilities(null);
		try {
			com.coredex.altrender.api.AltrenderApi.Host.gpu(null);
		} catch (RuntimeException e) {
			Log.warn("renderer: the world pipeline failed to free itself: " + e);
		}
		if (gpu != null) {
			gpu.delete();
		}
		if (GlTrackerCheck.ENABLED) {
			GlTrackerCheck.summary();
		}
		hooks = FrameHooks.NONE;
		if (timers != null) {
			timers.delete();
		}
		if (terrain != null) {
			terrain.delete();
			terrain = null;
		}
		if (ui != null) {
			ui.delete();
			ui = null;
		}
		com.coredex.altrender.immediate.ImmediateStream.delete();
		com.coredex.altrender.immediate.ListCapture.deleteAll();
		strategies.delete();
		// Clean shutdown (3.8): everything the strategies made must be gone.
		Log.info("renderer: driver error messages this session: " + GlDebug.errors());
		Log.info("renderer: GL objects left at shutdown: "
				+ com.coredex.altrender.debug.GlObjectCounts.describe(strategies.counts().snapshot()));
		GlDebug.shutdown();
	}

	/** The tracker's shadow check at a pass boundary ({@code -Daltrender.debug.trackerCheck}). */
	private static void check(String where) {
		if (GlTrackerCheck.ENABLED) {
			GlTrackerCheck.check(where);
		}
	}

	/** A pipeline's timed pass ({@code TimedPass} ordinal) starts or ends. */
	public void pipelineTimer(int pass, boolean start) {
		if (timers == null) {
			return;
		}
		if (start) {
			timers.begin(T_PIPELINE + pass);
		} else {
			timers.end(T_PIPELINE + pass);
		}
	}

	/** Debug group {@code ar:<pass>} and, when on, the pass's GPU timer. */
	private void begin(int pass) {
		GlDebug.push(GROUPS[pass]);
		if (timers != null) {
			timers.begin(pass);
		}
	}

	private void end(int pass) {
		if (timers != null) {
			timers.end(pass);
		}
		GlDebug.pop();
	}

	private String seen() {
		List<String> names = new ArrayList<>();
		String[] all = {"frame", "world", "camera", "terrain0", "terrain1"};
		for (int i = 0; i < all.length; i++) {
			if ((hooksSeen & 1 << i) != 0) {
				names.add(all[i]);
			}
		}
		return String.join(" ", names);
	}

	private static String keys(TierChoice choice) {
		List<String> k = new ArrayList<>();
		for (Feature f : choice.features()) {
			k.add(f.key);
		}
		return k.isEmpty() ? "none" : String.join(",", k);
	}

	/** {@code <gameDir>/altrender/caps.txt}: the caps table, the tier and every extension, rewritten each start. */
	private static void writeCaps(Path gameDir, GlCaps caps, String tierLine, TierChoice choice) {
		List<String> out = new ArrayList<>(caps.table());
		out.add(tierLine);
		out.add("features: " + keys(choice));
		out.addAll(choice.notes());
		out.add("");
		out.addAll(caps.extensions);
		Path file = gameDir.resolve("altrender").resolve("caps.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			Log.warn("can't write " + file + ": " + e.getMessage());
		}
	}
}
