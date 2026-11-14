/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.coredex.altrender.Log;
import com.coredex.altrender.debug.harness.CaptureTarget;
import com.coredex.altrender.debug.harness.Harness;
import com.coredex.altrender.debug.harness.Output;
import com.coredex.altrender.debug.harness.Scene;
import com.coredex.altrender.mc.mixin.harness.GameRendererAccess;
import com.coredex.altrender.mc.mixin.harness.MinecraftAccess;
import com.coredex.altrender.mc.mixin.harness.ItemInHandAccess;
import com.coredex.altrender.mc.mixin.harness.WorldRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SurvivalInteractionManager;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * The b1.7.3 scene driver (development.md 0.7): with {@code -Daltrender.scene=<scene.json>} it opens a fresh world
 * from the scene's seed, builds the scene in the same frame (no world tick runs in between), freezes ticks and frame
 * interpolation, waits until terrain has settled, takes two screenshots {@code shotGap} frames apart (they must be
 * identical) and optionally benchmarks an orbit flythrough, then quits. Output: {@code <out>/<scene>-<tag>-a.png},
 * {@code -b.png}, {@code .meta} (and {@code -bench.csv}). {@code -Daltrender.scene.out} (default
 * {@code <gameDir>/altrender/scenes}), {@code -Daltrender.scene.tag} (default {@code dev}).
 */
public final class SceneDriver {

	private enum State { TITLE, SETTLE, WAIT_B, HAND_CHECK, BENCH_WARMUP, BENCH, SUNSET, ORBIT_SHOTS, TOGGLES, UI_SHOTS, SETTINGS, F3, FPS, DONE }

	private static final String SAVE = "ar-scene";
	/** {@code -Daltrender.debug.orbitRadius}: 0 turns the camera in place (a bench inside a cave). */
	private static final double ORBIT_RADIUS = Double.parseDouble(System.getProperty("altrender.debug.orbitRadius", "16"));
	private static final double ORBIT_SECONDS = 15;

	private static Scene scene;
	private static Path out;
	private static String tag;
	private static State state = State.TITLE;
	private static CaptureTarget target;
	private static int frames;
	private static int stable;
	private static int settleFrames;
	private static double[] anchor;
	private static final Map<String, Object> META = new LinkedHashMap<>();
	private static long benchStart;
	private static long benchCpuStart;
	private static long lastFrame;
	/** Frame times of the bench window, ns (primitive: a 30-minute soak records ~400,000). */
	private static long[] benchFrames = new long[1 << 16];
	/** Each bench frame's time in the buffer swap (the CPU frame time is the frame's without it). */
	private static long[] benchSwaps = new long[1 << 16];
	private static long lastSwaps;
	private static long benchThreadCpuStart;
	private static int benchFrameCount;
	/** {@code -Daltrender.debug.packSwitches=N} (development.md 2.7): N pack switches once settled, then settle again. */
	private static int packSwitches = Integer.getInteger("altrender.debug.packSwitches", 0);
	/** {@code -Daltrender.debug.blockChangeTest} (development.md 2.8): 0 off, 1 place the block, 2 check, 3 done. */
	private static int blockTest = Boolean.getBoolean("altrender.debug.blockChangeTest") ? 1 : 0;
	private static int[] blockTestSection;
	private static int blockTestBefore;
	/** {@code -Daltrender.debug.sunsetFrames=N} (development.md 2.8): a sunset swept over N frames after the shots. */
	private static final int SUNSET_FRAMES = Integer.getInteger("altrender.debug.sunsetFrames", 0);
	private static final List<Long> SUNSET_TIMES = new ArrayList<>();
	/**
	 * {@code -Daltrender.debug.orbitShots=N} (development.md 2.5): after the shots, N more from points around the bench
	 * orbit (each once terrain has settled there), to compare culling on and off across many views.
	 */
	private static final int ORBIT_SHOTS = Integer.getInteger("altrender.debug.orbitShots", 0);
	private static int orbitShot;
	/**
	 * {@code -Daltrender.debug.toggles=fancy,ao,distance:0,anaglyph,pack,tier} (development.md 3.8): after the shots,
	 * each step applied as the game's options screen does it, a shot once terrain has settled ({@code s0}, {@code s1},
	 * ...); a vanilla run with the same steps gives the comparison ({@code tier} applies to Altrender only).
	 */
	private static final String[] TOGGLES = System.getProperty("altrender.debug.toggles", "").isEmpty() ? new String[0]
			: System.getProperty("altrender.debug.toggles").split(",");
	private static int toggle;
	private static boolean toggleApplied;
	/**
	 * {@code -Daltrender.debug.uiShots=1,2,3,0} (development.md 5.1): after the shots, the widget gallery over the
	 * scene at each listed GUI scale (the game's option: 0 = Auto), a shot {@code ui<scale>} each.
	 */
	/** {@code -Daltrender.debug.fpsShots} (development.md 5.4): the FPS counter at GUI scales 1-4, every corner. */
	private static final boolean FPS_SHOTS = Boolean.getBoolean("altrender.debug.fpsShots");
	private static int fpsStep = -1;
	private static int fpsFrames;
	/** {@code -Daltrender.debug.f3Shots} (development.md 5.3): the debug screen with both charts, then the game's. */
	private static final boolean F3_SHOTS = Boolean.getBoolean("altrender.debug.f3Shots");
	private static int f3Frames = -1;
	/** {@code -Daltrender.debug.settingsTest} (development.md 5.2): Altrender's video settings, scripted; shots. */
	private static final boolean SETTINGS_TEST = Boolean.getBoolean("altrender.debug.settingsTest");
	private static int settingsFrames = -1;
	private static final String[] UI_SHOTS = System.getProperty("altrender.debug.uiShots", "").isEmpty() ? new String[0]
			: System.getProperty("altrender.debug.uiShots").split(",");
	private static int uiShot;
	private static int uiFrames;
	/** {@code -Daltrender.debug.benchJfr=true} (development.md 3.1): a JFR recording of exactly the measured bench. */
	private static jdk.jfr.Recording benchRecording;

	private SceneDriver() {
	}

	public static float fov() {
		return scene != null && state != State.TITLE ? scene.fov() : -1;
	}

	/** preLaunch: load the scene, write its options (texture pack, graphics) and remove the previous scene world. */
	public static void preLaunch(Path gameDir) {
		if (!Harness.active()) {
			return;
		}
		try {
			scene = Scene.load(Path.of(System.getProperty("altrender.scene")));
			out = Path.of(System.getProperty("altrender.scene.out", gameDir.resolve("altrender/scenes").toString()));
			tag = System.getProperty("altrender.scene.tag", "dev");
			writeOptions(gameDir.resolve("options.txt"));
			Path save = gameDir.resolve("saves").resolve(SAVE);
			if (Files.isDirectory(save)) {
				try (Stream<Path> walk = Files.walk(save)) {
					for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
						Files.delete(p);
					}
				}
			}
			Log.info("harness: scene " + scene.name() + " (tag " + tag + ") -> " + out);
		} catch (IOException | RuntimeException e) {
			Log.error("harness: could not load the scene", e);
			System.exit(2);
		}
	}

	/** The game's graphics options come from options.txt at startup, so set them there (no reload later). */
	private static void writeOptions(Path file) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		if (Files.isRegularFile(file)) {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				int colon = line.indexOf(':');
				if (colon > 0) {
					values.put(line.substring(0, colon), line.substring(colon + 1));
				}
			}
		}
		values.put("skin", "default".equals(scene.pack()) ? "Default" : scene.pack());
		values.put("bobView", "false");
		values.put("anaglyph3d", "false");
		values.put("advancedOpengl", "false");
		values.put("guiScale", "2");
		scene.options().forEach((k, v) -> {
			if (!k.startsWith("altrender.")) { // Altrender's own settings: applied when the scene is built
				values.put(k, v);
			}
		});
		StringBuilder sb = new StringBuilder();
		values.forEach((k, v) -> sb.append(k).append(':').append(v).append('\n'));
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
	}

	public static void beforeRender(Minecraft mc) {
		if (scene == null || state == State.TITLE || state == State.DONE) {
			return;
		}
		target.bind();
		if (mc.width != target.width() || mc.height != target.height()) {
			mc.width = target.width();
			mc.height = target.height();
			((MinecraftAccess) mc).altrender$resize(target.width(), target.height());
		}
	}

	public static void afterRender(Minecraft mc) {
		if (scene == null) {
			return;
		}
		frames++;
		try {
			switch (state) {
				case TITLE -> {
					if (mc.screen instanceof TitleScreen && frames > 30) {
						build(mc);
						state = State.SETTLE;
						frames = 0;
					}
					return;
				}
				case SETTLE -> {
					if (blockTest == 2) {
						blockTestCheck();
					}
					boolean idle = terrainIdle(mc);
					stable = idle ? stable + 1 : 0;
					if (stable >= scene.settleFrames() && frames >= 2 * scene.settleFrames() && blockTest == 1) {
						blockTestPlace(mc);
						stable = 0;
					} else if (stable >= scene.settleFrames() && frames >= 2 * scene.settleFrames() && packSwitches > 0) {
						switchPacks(mc);
						stable = 0;
					} else if (stable >= scene.settleFrames() && frames >= 2 * scene.settleFrames()) {
						settleFrames = frames;
						pinTickState(mc);
						// one more frame with the final fog before the first shot
						state = State.WAIT_B;
						frames = -1;
					}
				}
				case WAIT_B -> {
					if (frames == 0) {
						shot("a");
					} else if (frames == scene.shotGap()) {
						shot("b");
						if (scene.hand()) {
							// The hand check: the same view without the hand (and HUD), compared where the hand is.
							mc.options.hideGui = true;
							state = State.HAND_CHECK;
							frames = 0;
						} else if (TOGGLES.length > 0) {
							state = State.TOGGLES;
							toggle = 0;
							toggleApplied = false;
							stable = 0;
						} else if (FPS_SHOTS) {
							state = State.FPS;
							fpsStep = -1;
						} else if (F3_SHOTS) {
							state = State.F3;
							f3Frames = -1;
						} else if (SETTINGS_TEST) {
							state = State.SETTINGS;
							settingsFrames = -1;
						} else if (UI_SHOTS.length > 0) {
							state = State.UI_SHOTS;
							uiShot = 0;
							uiFrames = -1;
						} else if (ORBIT_SHOTS > 0) {
							state = State.ORBIT_SHOTS;
							frames = 0;
							stable = 0;
							orbitShot = 0;
						} else if (SUNSET_FRAMES > 0) {
							state = State.SUNSET;
							frames = 0;
							lastFrame = System.nanoTime();
						} else if (scene.benchSeconds() > 0) {
							state = State.BENCH_WARMUP;
							frames = 0;
							stable = 0;
							benchStart = System.nanoTime();
						} else {
							finish(mc);
						}
					}
				}
				case HAND_CHECK -> {
					if (frames == 10) {
						shot("c");
						handCheck();
						finish(mc);
					}
				}
				case BENCH_WARMUP, BENCH -> bench(mc);
				case SUNSET -> sunset(mc);
				case ORBIT_SHOTS -> orbitShots(mc);
				case TOGGLES -> toggles(mc);
				case UI_SHOTS -> uiShots(mc);
				case SETTINGS -> settings(mc);
				case F3 -> f3(mc);
				case FPS -> fpsCounter(mc);
				case DONE -> {
				}
			}
		} catch (IOException | RuntimeException e) {
			Log.error("harness: scene failed", e);
			state = State.DONE;
			mc.stop();
			return;
		}
		if (target != null && state != State.DONE) {
			target.present(Display.getWidth(), Display.getHeight());
		}
	}

	/** Opens the world and builds the scene in one go: no world tick runs between creation and the freeze. */
	private static void build(Minecraft mc) {
		target = new CaptureTarget(scene.width(), scene.height());
		// as the world screens do before starting a singleplayer game
		mc.interactionManager = new SurvivalInteractionManager(mc);
		mc.startGame(SAVE, SAVE, scene.seed());
		mc.openScreen(null);
		World world = mc.world;
		world.setAllowedMobSpawns(false, false);
		// b1.7.3's world random is unseeded (it picked the spawn point): seed it for anything that uses it later
		world.random.setSeed(scene.seed());
		if ("nether".equals(scene.dimension())) {
			// the Nether position follows the overworld one (÷8): stand on the fixed column first
			int[] xz = scene.anchorXZ();
			mc.player.setPositionAndAngles(xz[0] + 0.5, surface(world, xz[0], xz[1]), xz[1] + 0.5, 0, 0);
			mc.changeDimension();
			world = mc.world;
			world.random.setSeed(scene.seed());
		}
		anchor = anchor(mc, world);
		int ax = (int) anchor[0];
		int ay = (int) anchor[1];
		int az = (int) anchor[2];
		for (int[] b : scene.blocks()) {
			if (b.length == 5) {
				world.setBlockWithMetadata(ax + b[0], ay + b[1], az + b[2], b[3], b[4]);
			} else if (b.length == 8) {
				for (int x = Math.min(b[0], b[3]); x <= Math.max(b[0], b[3]); x++) {
					for (int y = Math.min(b[1], b[4]); y <= Math.max(b[1], b[4]); y++) {
						for (int z = Math.min(b[2], b[5]); z <= Math.max(b[2], b[5]); z++) {
							world.setBlockWithMetadata(ax + x, ay + y, az + z, b[6], b[7]);
						}
					}
				}
			}
		}
		for (Scene.EntitySpec spec : scene.entities()) {
			Entity e = Entities.createSilently(spec.type(), world);
			if (e == null) {
				Log.warn("harness: unknown entity " + spec.type());
				continue;
			}
			e.setPositionAndAngles(ax + spec.at()[0], ay + spec.at()[1], az + spec.at()[2], spec.yaw(), 0);
			if (e instanceof MobEntity mob) {
				// a mob's constructor takes its body rotation and animation offsets from Math.random()
				mob.bodyYaw = mob.lastBodyYaw = spec.yaw();
				mob.f_92185951 = 0.5F;
				mob.f_75186175 = 0.015F;
				mob.f_26584770 = 0.0F;
				if ("hurt".equals(spec.state())) {
					mob.damagedTimer = mob.damagedTime = 10; // ticks are frozen: it stays hurt
				}
			}
			if ("swell".equals(spec.state()) && e instanceof net.minecraft.entity.mob.monster.CreeperEntity creeper) {
				// 27 of 30: the size and the white flash (it shows on odd tenths) of a creeper about to explode
				((com.coredex.altrender.mc.mixin.harness.CreeperAccess) creeper).altrender$setFuse(27);
				((com.coredex.altrender.mc.mixin.harness.CreeperAccess) creeper).altrender$setLastFuse(27);
			}
			world.addEntity(e);
		}
		// Placing blocks can break others (a cactus next to a solid block drops itself): item drops spin with a random
		// phase, so none stay in a scene.
		for (Entity e : new ArrayList<>(world.getEntities())) {
			if (e instanceof ItemEntity) {
				// removeEntityNow is server-only (Fabric strips it on the client); removeEntity only marks the entity
				// and a tick would drop it from the list, but ticks are frozen: take it out of the client's list here.
				world.removeEntity(e);
				world.getEntities().remove(e);
			}
		}
		double[] c = scene.camera();
		mc.player.setPositionAndAngles(ax + c[0], ay + c[1], az + c[2], (float) c[3], (float) c[4]);
		String distance = scene.options().get("altrender.renderDistance");
		if (distance != null) {
			com.coredex.altrender.render.Renderer.config().setRenderDistance(Integer.parseInt(distance));
		}
		world.setTime(scene.time());
		world.initAmbientDarkness();
		world.setRain(scene.rain());
		if (scene.sleepAt() != null) {
			int[] bed = scene.sleepAt();
			Log.info("harness: sleep at the bed: " + mc.player.trySleep(ax + bed[0], ay + bed[1], az + bed[2]));
		}
		mc.options.hideGui = !scene.hand();
		mc.options.debugEnabled = false;
		mc.options.perspective = false;
		mc.particleManager.setWorld(world); // (clears the particles)
		for (Scene.EntitySpec spec : scene.particles()) {
			// After the camera is placed (the game drops particles 16 blocks away from it); still (no velocity), and
			// ticks are frozen, so they stay as spawned.
			world.addParticle(spec.type(), ax + spec.at()[0], ay + spec.at()[1], az + spec.at()[2], 0, 0, 0);
		}
		pinTickState(mc);
		Harness.freeze(true);
		META.put("scene", scene.name());
		META.put("tag", tag);
		META.put("seed", scene.seed());
		META.put("dimension", scene.dimension());
		META.put("anchor", (int) anchor[0] + "," + (int) anchor[1] + "," + (int) anchor[2]);
		META.put("camera", String.format("%.2f,%.2f,%.2f yaw=%.1f pitch=%.1f", ax + c[0], ay + c[1], az + c[2], c[3], c[4]));
		META.put("time", scene.time());
		META.put("rain", scene.rain());
		META.put("fov", scene.fov());
		META.put("size", scene.width() + "x" + scene.height());
		META.put("pack", scene.pack());
		META.put("options", scene.options());
		Log.info("harness: built " + scene.name() + " at " + META.get("anchor"));
	}

	private static double[] anchor(Minecraft mc, World world) {
		int x;
		int z;
		switch (scene.anchor()) {
			case "player" -> {
				return new double[] {MathHelper.floor(mc.player.x), MathHelper.floor(mc.player.y) + scene.anchorDy(),
						MathHelper.floor(mc.player.z)};
			}
			case "snowy" -> {
				int[] found = findSnowy(world, scene.anchorXZ()[0], scene.anchorXZ()[1]);
				x = found[0];
				z = found[1];
			}
			default -> {
				x = scene.anchorXZ()[0];
				z = scene.anchorXZ()[1];
			}
		}
		return new double[] {x, surface(world, x, z) + scene.anchorDy(), z};
	}

	/** First air block above the column's top (loads the chunk first: unloaded columns read as height 0). */
	private static int surface(World world, int x, int z) {
		world.getChunkAt(x >> 4, z >> 4);
		return world.getHeight(x, z);
	}

	/** Spiral search (16-block steps, up to 4096 blocks) for the nearest column in a snowy biome. */
	private static int[] findSnowy(World world, int cx, int cz) {
		for (int r = 0; r <= 256; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = cx + dx * 16;
					int z = cz + dz * 16;
					if (world.getBiomeSource().getBiome(x, z).isSnowy()) {
						return new int[] {x, z};
					}
				}
			}
		}
		throw new IllegalStateException("no snowy biome within 4096 blocks of the anchor for seed " + scene.seed());
	}

	/**
	 * State vanilla eases per tick: pinned to the value it converges to (fog brightness: the camera's brightness mixed
	 * with the render-distance term), and the tick counters that drive clouds and rain to 0.
	 */
	private static void pinTickState(Minecraft mc) {
		Entity cam = mc.player;
		float brightness = mc.world.getBrightness(MathHelper.floor(cam.x), MathHelper.floor(cam.y), MathHelper.floor(cam.z));
		float distance = (3 - mc.options.viewDistance) / 3.0F;
		float fog = brightness * (1.0F - distance) + distance;
		GameRendererAccess gr = (GameRendererAccess) mc.gameRenderer;
		gr.altrender$setFogBrightness(fog);
		gr.altrender$setLastFogBrightness(fog);
		gr.altrender$setTicks(0);
		((WorldRendererAccess) mc.worldRenderer).altrender$setTicks(0);
		ItemInHandAccess hand = (ItemInHandAccess) mc.gameRenderer.itemInHandRenderer;
		hand.altrender$setHandHeight(1);
		hand.altrender$setLastHandHeight(1);
	}

	/**
	 * Whether the hand drew: in the hand's corner (right third, lower half, clear of the hotbar), the mean change from
	 * shot a (hand) to shot c (no hand) against the change from a to b (both with the hand: the pack's own noise, TAA,
	 * flicker). The hand must change its corner at least 3 times the noise and by at least one level on average (a
	 * dark night hand changes little, but noise there is nearly none). {@code hand_check} pass or FAIL in the .meta and
	 * the log.
	 */
	private static void handCheck() throws IOException {
		String base = scene.name() + "-" + tag;
		java.awt.image.BufferedImage a = javax.imageio.ImageIO.read(out.resolve(base + "-a.png").toFile());
		java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(out.resolve(base + "-b.png").toFile());
		java.awt.image.BufferedImage c = javax.imageio.ImageIO.read(out.resolve(base + "-c.png").toFile());
		int w = a.getWidth();
		int h = a.getHeight();
		double hand = meanDiff(a, c, w * 2 / 3, h / 2, w, h * 9 / 10);
		double noise = meanDiff(a, b, w * 2 / 3, h / 2, w, h * 9 / 10);
		boolean pass = hand >= 1 && hand >= 3 * (noise + 0.05);
		String result = String.format(java.util.Locale.ROOT, "%s (hand corner change %.2f, noise %.2f)",
				pass ? "pass" : "FAIL", hand, noise);
		META.put("hand_check", result);
		Log.info("harness: hand check " + result);
	}

	/** Mean absolute difference per channel over the box. */
	private static double meanDiff(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b, int x0, int y0, int x1,
			int y1) {
		long sum = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				int p = a.getRGB(x, y);
				int q = b.getRGB(x, y);
				sum += Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255))
						+ Math.abs((p & 255) - (q & 255));
			}
		}
		return sum / (3.0 * (x1 - x0) * (y1 - y0));
	}

	private static void shot(String which) throws IOException {
		String base = scene.name() + "-" + tag;
		Output.png(out.resolve(base + "-" + which + ".png"), target.read(), target.width(), target.height());
		Log.info("harness: shot " + base + "-" + which + ".png");
	}

	/** Orbit flythrough (16 blocks, one lap per 15 s, looking along the path): a warm-up lap, then until terrain
	 * settles, then {@code benchSeconds} measured, every frame time recorded. */
	private static void bench(Minecraft mc) throws IOException {
		long now = System.nanoTime();
		double t = (now - benchStart) / 1e9;
		double angle = 2 * Math.PI * t / ORBIT_SECONDS;
		double[] c = scene.camera();
		double cx = anchor[0] + c[0];
		double cz = anchor[2] + c[2];
		mc.player.setPositionAndAngles(cx + ORBIT_RADIUS * Math.cos(angle), anchor[1] + c[1], cz + ORBIT_RADIUS * Math.sin(angle),
				(float) Math.toDegrees(angle), (float) c[4]);
		if (state == State.BENCH_WARMUP) {
			boolean idle = terrainIdle(mc);
			stable = idle ? stable + 1 : 0;
			if (t >= ORBIT_SECONDS && (stable >= 60 || t >= ORBIT_SECONDS + 30)) {
				state = State.BENCH;
				META.put("heap_live_mb_start", liveHeapMb());
				benchStart = System.nanoTime();
				benchCpuStart = processCpuNanos();
				benchThreadCpuStart = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
				if (com.coredex.altrender.render.Renderer.terrain() != null) {
					com.coredex.altrender.render.Renderer.terrain().resetDrawTimer();
				}
				now = benchStart;
				lastFrame = now;
				benchFrameCount = 0;
				if (Boolean.getBoolean("altrender.debug.benchJfr")) {
					try {
						benchRecording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
						benchRecording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2));
						benchRecording.enable("jdk.NativeMethodSample").withPeriod(java.time.Duration.ofMillis(2));
						benchRecording.start();
					} catch (java.io.IOException | java.text.ParseException e) {
						Log.warn("harness: no JFR recording: " + e);
					}
				}
			}
			return;
		}
		long swaps = com.coredex.altrender.debug.FrameStats.swapNanos();
		if (lastFrame != benchStart) {
			if (benchFrameCount == benchFrames.length) {
				benchFrames = java.util.Arrays.copyOf(benchFrames, benchFrames.length * 2);
				benchSwaps = java.util.Arrays.copyOf(benchSwaps, benchSwaps.length * 2);
			}
			benchSwaps[benchFrameCount] = swaps - lastSwaps;
			benchFrames[benchFrameCount++] = now - lastFrame;
		} // else the window's first frame: it follows the live-heap GC above, and a frame limit catches up on it
		lastFrame = now;
		lastSwaps = swaps;
		if (t >= scene.benchSeconds()) {
			if (benchRecording != null) {
				benchRecording.stop();
				benchRecording.dump(out.resolve(scene.name() + "-" + tag + ".jfr"));
				benchRecording.close();
				benchRecording = null;
			}
			// Process CPU time over the window, in % of one core (the 3.6 idle check).
			META.put("bench_cpu_pct", String.format(java.util.Locale.ROOT, "%.1f",
					100.0 * (processCpuNanos() - benchCpuStart) / (now - benchStart)));
			META.put("heap_live_mb_end", liveHeapMb());
			com.coredex.altrender.terrain.TerrainRenderer terrainNow = com.coredex.altrender.render.Renderer.terrain();
			if (terrainNow != null) {
				META.put("terrain_end", terrainNow.stats());
			}
			long[] frameNanos = java.util.Arrays.copyOf(benchFrames, benchFrameCount);
			Output.csv(out.resolve(scene.name() + "-" + tag + "-bench.csv"), frameNanos);
			META.putAll(Output.benchStats(frameNanos));
			META.putAll(Output.cpuStats(frameNanos, java.util.Arrays.copyOf(benchSwaps, benchFrameCount)));
			// The render thread's own CPU time per frame (waits for the GPU don't count, wherever the driver makes them).
			long threadCpu = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()
					- benchThreadCpuStart;
			META.put("bench_render_thread_cpu_ms", String.format(java.util.Locale.ROOT, "%.3f",
					threadCpu / 1e6 / Math.max(1, benchFrameCount)));
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) throws IOException {
		META.put("settle_frames", settleFrames);
		META.put("gl_version", GL11.glGetString(GL11.GL_VERSION));
		META.put("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
		META.put("gl_vendor", GL11.glGetString(GL11.GL_VENDOR));
		META.put("java", Runtime.version());
		META.put("altrender_renderer", System.getProperty("altrender.renderer", "default"));
		META.put("altrender_tier", System.getProperty("altrender.tier", "default"));
		com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
		if (terrain != null) {
			META.put("terrain", terrain.stats());
			META.put("terrain_objects", terrain.objectCounts());
		}
		Output.meta(out.resolve(scene.name() + "-" + tag + ".meta"), META);
		state = State.DONE;
		Log.info("harness: done " + scene.name());
		mc.stop();
	}

	/** Places stone two blocks from the camera and notes its section's quads (development.md 2.8). */
	private static void blockTestPlace(Minecraft mc) {
		com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
		int x = (int) Math.floor(mc.player.x) + 2;
		int y = (int) Math.floor(mc.player.y);
		int z = (int) Math.floor(mc.player.z);
		blockTestSection = new int[] {x >> 4, y >> 4, z >> 4};
		blockTestBefore = terrain != null ? terrain.quads(x >> 4, y >> 4, z >> 4) : -1;
		mc.world.setBlock(x, y, z, net.minecraft.block.Block.STONE.id);
		blockTest = 2;
	}

	/** One frame after the block went in: its section must already be rebuilt and drawn with it. */
	private static void blockTestCheck() {
		com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
		int after = terrain != null ? terrain.quads(blockTestSection[0], blockTestSection[1], blockTestSection[2]) : -1;
		boolean pass = after >= 0 && after != blockTestBefore;
		Log.info("harness: block change test: section quads " + blockTestBefore + " -> " + after
				+ " one frame later: " + (pass ? "PASS" : "FAIL"));
		META.put("block_change_test", pass ? "pass" : "fail");
		blockTest = 3;
	}

	private static void toggles(Minecraft mc) throws IOException {
		if (!toggleApplied) {
			String step = TOGGLES[toggle].trim();
			Log.info("harness: toggle " + toggle + ": " + step);
			switch (step.contains(":") ? step.substring(0, step.indexOf(':')) : step) {
				case "fancy" -> {
					mc.options.fancyGraphics = !mc.options.fancyGraphics;
					mc.worldRenderer.reload();
				}
				case "ao" -> {
					mc.options.ambientOcclusion = !mc.options.ambientOcclusion;
					mc.worldRenderer.reload();
				}
				case "distance" -> mc.options.viewDistance = Integer.parseInt(step.substring(step.indexOf(':') + 1));
				case "anaglyph" -> {
					mc.options.anaglyph = !mc.options.anaglyph;
					mc.textureManager.reload();
				}
				case "pack" -> {
					net.minecraft.client.resource.pack.TexturePack current = mc.texturePacks.selectedPack;
					for (net.minecraft.client.resource.pack.TexturePack p : mc.texturePacks.getAvailablePacks()) {
						if (p != current) {
							mc.texturePacks.select(p);
							break;
						}
					}
					mc.textureManager.reload();
				}
				case "tier" -> {
					com.coredex.altrender.render.Renderer r = com.coredex.altrender.render.Renderer.instance();
					if (r != null) {
						com.coredex.altrender.render.Renderer.switchTier(r.tier().tier() == com.coredex.altrender.caps.Tier.GL45
								? com.coredex.altrender.caps.Tier.GL33 : com.coredex.altrender.caps.Tier.GL45);
					}
				}
				case "reload" -> mc.worldRenderer.reload(); // what the options screen does for graphics changes
				case "nether" -> { // a portal trip: there (the player stays where the game puts them) or back
					mc.changeDimension();
					if (mc.player.dimension == 0) {
						placeCamera(mc);
					}
					pinTickState(mc);
				}
				case "respawn" -> { // what the death screen's respawn does
					mc.respawnPlayer(false, 0);
					placeCamera(mc);
					pinTickState(mc);
				}
				default -> Log.warn("harness: unknown toggle " + step);
			}
			toggleApplied = true;
			stable = 0;
			return;
		}
		boolean idle = terrainIdle(mc);
		stable = idle ? stable + 1 : 0;
		if (stable >= 20) {
			shot("s" + toggle);
			toggle++;
			toggleApplied = false;
			if (toggle >= TOGGLES.length) {
				finish(mc);
			}
		}
	}

	/** One orbit point per shot: place the camera, wait for terrain to settle (and 5 more frames), shoot. */
	/** The scene's camera, time and weather again (after a respawn or a portal trip back: a new world object). */
	private static void placeCamera(Minecraft mc) {
		double[] c = scene.camera();
		mc.player.setPositionAndAngles((int) anchor[0] + c[0], (int) anchor[1] + c[1], (int) anchor[2] + c[2], (float) c[3], (float) c[4]);
		mc.world.setTime(scene.time());
		mc.world.initAmbientDarkness();
		mc.world.setRain(scene.rain());
		Log.info("harness: camera " + mc.player.x + " " + mc.player.y + " " + mc.player.z + ", dimension "
				+ mc.player.dimension + ", time " + mc.world.getTime() + ", camera entity is player: " + (mc.camera == mc.player));
	}

	/** HUD and F3 on with both charts; after 2 s a shot and the draw time; then the game's own F3 for comparison. */
	private static void f3(Minecraft mc) throws IOException {
		if (f3Frames < 0) {
			mc.options.hideGui = false;
			mc.options.debugEnabled = true;
			com.coredex.altrender.debugscreen.DebugScreen.combo(2); // GPU breakdown on (the frame chart is on by default)
			f3Frames = 0;
			startTime = System.nanoTime();
			return;
		}
		f3Frames++;
		long ms = (System.nanoTime() - startTime) / 1_000_000L;
		if (f3Frames > 0 && ms >= 2500 && !f3Shot) {
			shot("f3");
			f3Shot = true;
			Log.info(String.format(java.util.Locale.ROOT, "harness: debug screen draw %.3f ms (CPU, average)",
					com.coredex.altrender.debugscreen.DebugScreen.drawMs()));
			com.coredex.altrender.debugscreen.DebugScreen.combo(2); // back off, saved as it was
			com.coredex.altrender.render.Renderer.config().setModernDebugScreen(false);
			f3Frames = 0;
			startTime = System.nanoTime();
		} else if (f3Shot && f3Frames == 30) {
			shot("f3game");
			java.util.List<String> failed = com.coredex.altrender.debugscreen.DebugScreen.selfTest("position");
			Log.info("harness: debug screen entries: " + (failed.isEmpty() ? "ok" : "FAILED " + failed));
			com.coredex.altrender.render.Renderer.config().setModernDebugScreen(true);
			mc.options.debugEnabled = false;
			finish(mc);
		}
	}

	private static long startTime;
	private static boolean f3Shot;
	private static final String[] CORNERS = {"top_left", "top_right", "bottom_left", "bottom_right"};
	private static final String[] CONTRASTS = {"background", "shadow", "none", "background"};

	/**
	 * Per GUI scale (1, 2, 3, Auto = 4 at 1080p): every corner × contrast checked to fit the screen, a shot with one
	 * of them; then 2 s of the counter for its draw time.
	 */
	private static void fpsCounter(Minecraft mc) throws IOException {
		com.coredex.altrender.settings.Config c = com.coredex.altrender.render.Renderer.config();
		if (fpsStep < 0) {
			mc.options.hideGui = false;
			c.setFpsCounter("advanced");
			c.setFpsCounterGpu(true);
			fpsStep = 0;
			fpsFrames = 0;
			return;
		}
		int[] scales = {1, 2, 3, 0};
		if (fpsStep < scales.length) {
			if (fpsFrames == 0) {
				mc.options.guiScale = scales[fpsStep];
				c.setFpsCounterCorner(CORNERS[fpsStep]);
				c.setFpsCounterContrast(CONTRASTS[fpsStep]);
			}
			if (++fpsFrames == 40) {
				net.minecraft.client.render.Window w = new net.minecraft.client.render.Window(mc.options, mc.width, mc.height);
				int textWidth = 200; // wider than the Advanced line with GPU time
				java.util.List<String> outside = new java.util.ArrayList<>();
				for (String corner : CORNERS) {
					com.coredex.altrender.ui.framework.Dim2i b = com.coredex.altrender.debugscreen.FpsCounter.bounds(corner,
							textWidth, 9, w.getWidth(), w.getHeight());
					if (b.x() < 0 || b.y() < 0 || b.getLimitX() > w.getWidth() || b.getLimitY() > w.getHeight()) {
						outside.add(corner);
					}
				}
				shot("fps" + w.scale);
				Log.info("harness: fps counter at GUI scale " + w.scale + ": " + (outside.isEmpty() ? "ok"
						: "OFF SCREEN " + outside));
				fpsStep++;
				fpsFrames = 0;
			}
			return;
		}
		if (++fpsFrames == 120) {
			Log.info(String.format(java.util.Locale.ROOT, "harness: fps counter draw %.3f ms (CPU, average)",
					com.coredex.altrender.debugscreen.FpsCounter.drawMs()));
			c.setFpsCounter("off");
			finish(mc);
		}
	}

	/** Opens the settings, shoots the General page, runs the scripted check, shoots the Performance page, ends. */
	private static void settings(Minecraft mc) throws IOException {
		if (settingsFrames < 0) {
			com.coredex.altrender.mc.ui.B173Settings.open(mc);
			settingsFrames = 0;
			return;
		}
		settingsFrames++;
		if (!(mc.screen instanceof com.coredex.altrender.mc.ui.HostScreen host)
				|| !(host.ui() instanceof com.coredex.altrender.ui.options.OptionsScreen screen)) {
			Log.warn("harness: settings screen didn't open");
			finish(mc);
			return;
		}
		if (settingsFrames == 20) {
			shot("settings-general");
			java.util.List<String> failed = com.coredex.altrender.mc.debug.SettingsSelfTest.run(mc, screen);
			Log.info("harness: settings test: " + (failed.isEmpty() ? "ok" : "FAILED " + failed));
			screen.selectPage("altrender.performance");
		} else if (settingsFrames == 40) {
			shot("settings-performance");
			screen.selectPage("altrender.advanced");
		} else if (settingsFrames == 60) {
			shot("settings-advanced");
			mc.openScreen(null);
			finish(mc);
		}
	}

	/** The widget gallery at each GUI scale of {@link #UI_SHOTS}: set the scale, lay out again, 30 frames, shoot. */
	private static void uiShots(Minecraft mc) throws IOException {
		if (uiFrames < 0) {
			if (uiShot >= UI_SHOTS.length) {
				mc.openScreen(null);
				finish(mc);
				return;
			}
			if (!(mc.screen instanceof com.coredex.altrender.mc.ui.HostScreen)) {
				// -Daltrender.debug.uiScreen: shaderpacks (the pack list) or shaderpacks:<screen id> (its options,
				// "main" or a sub-screen); default the widget gallery.
				String which = System.getProperty("altrender.debug.uiScreen", "gallery");
				com.coredex.altrender.api.shaderpacks.ShaderPackMenu menu = com.coredex.altrender.api.AltrenderApi
						.shaderPackMenu();
				if (which.equals("options")) {
					com.coredex.altrender.mc.ui.B173Settings.open(mc);
				} else if (which.startsWith("shaderpacks") && menu != null) {
					com.coredex.altrender.ui.shaderpacks.ShaderPackScreen s = new com.coredex.altrender.ui.shaderpacks.ShaderPackScreen(menu);
					if (which.contains(":")) {
						s.showOptions(which.substring(which.indexOf(':') + 1));
					}
					com.coredex.altrender.mc.ui.B173UiHost.get(mc).open(s);
				} else {
					com.coredex.altrender.mc.ui.B173UiHost.get(mc).open(new com.coredex.altrender.ui.WidgetGallery());
				}
			}
			mc.options.guiScale = Integer.parseInt(UI_SHOTS[uiShot].trim());
			net.minecraft.client.render.Window window = new net.minecraft.client.render.Window(mc.options, mc.width,
					mc.height);
			mc.screen.init(mc, window.getWidth(), window.getHeight());
			if (Boolean.getBoolean("altrender.debug.uiGallery.tooltip")
					&& mc.screen instanceof com.coredex.altrender.mc.ui.HostScreen host
					&& host.ui() instanceof com.coredex.altrender.ui.WidgetGallery gallery) {
				gallery.showcase();
			}
			uiFrames = 0;
			return;
		}
		if (++uiFrames >= 30) {
			int scale = new net.minecraft.client.render.Window(mc.options, mc.width, mc.height).scale;
			shot("ui" + scale);
			if (mc.screen instanceof com.coredex.altrender.mc.ui.HostScreen host
					&& host.ui() instanceof com.coredex.altrender.ui.WidgetGallery gallery) {
				java.util.List<String> failed = gallery.selfTest();
				Log.info("harness: ui self-test at GUI scale " + scale + ": "
						+ (failed.isEmpty() ? "ok" : "FAILED " + failed));
			}
			uiShot++;
			uiFrames = -1;
		}
	}

	/** CPU time of the whole process (all threads), ns. */
	private static long processCpuNanos() {
		return ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean())
				.getProcessCpuTime();
	}

	/** Live heap after a full collection, MB (soak checks; development.md 3.8). */
	private static long liveHeapMb() {
		System.gc();
		Runtime rt = Runtime.getRuntime();
		return (rt.totalMemory() - rt.freeMemory()) >> 20;
	}

	private static void orbitShots(Minecraft mc) throws IOException {
		double angle = 2 * Math.PI * orbitShot / ORBIT_SHOTS;
		double[] c = scene.camera();
		double cx = anchor[0] + c[0];
		double cz = anchor[2] + c[2];
		mc.player.setPositionAndAngles(cx + ORBIT_RADIUS * Math.cos(angle), anchor[1] + c[1], cz + ORBIT_RADIUS * Math.sin(angle),
				(float) Math.toDegrees(angle), (float) c[4]);
		boolean idle = terrainIdle(mc);
		stable = idle ? stable + 1 : 0;
		if (stable >= 5) {
			shot("o" + orbitShot);
			orbitShot++;
			stable = 0;
			if (orbitShot >= ORBIT_SHOTS) {
				finish(mc);
			}
		}
	}

	/** Sweeps the time through sunset, the sky darkening step by step as the game's tick does it; frame times kept. */
	private static void sunset(Minecraft mc) throws IOException {
		long now = System.nanoTime();
		SUNSET_TIMES.add(now - lastFrame);
		lastFrame = now;
		long t = 11800 + (long) frames * 2400 / SUNSET_FRAMES;
		mc.world.setTime(t);
		int darkness = mc.world.calculateAmbientDarkness(1.0F);
		if (darkness != mc.world.ambientDarkness) {
			mc.world.ambientDarkness = darkness;
			mc.worldRenderer.notifyAmbientDarknessChanged();
		}
		if (frames >= SUNSET_FRAMES) {
			List<Long> sorted = new ArrayList<>(SUNSET_TIMES.subList(1, SUNSET_TIMES.size()));
			java.util.Collections.sort(sorted);
			double p50 = sorted.get(sorted.size() / 2) / 1e6;
			double p99 = sorted.get((int) (sorted.size() * 0.99)) / 1e6;
			double max = sorted.get(sorted.size() - 1) / 1e6;
			META.put("sunset_frames", sorted.size());
			META.put("sunset_p50_ms", String.format(java.util.Locale.ROOT, "%.3f", p50));
			META.put("sunset_p99_ms", String.format(java.util.Locale.ROOT, "%.3f", p99));
			META.put("sunset_max_ms", String.format(java.util.Locale.ROOT, "%.3f", max));
			Log.info(String.format(java.util.Locale.ROOT, "harness: sunset over %d frames: p50 %.2f ms, p99 %.2f ms, max %.2f ms",
					sorted.size(), p50, p99, max));
			finish(mc);
		}
	}

	/** Switches the texture pack {@link #packSwitches} times (alternating with another pack) and back to the scene's. */
	private static void switchPacks(Minecraft mc) {
		com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
		String before = terrain != null ? terrain.objectCounts() : "-";
		java.util.List<net.minecraft.client.resource.pack.TexturePack> packs = mc.texturePacks.getAvailablePacks();
		net.minecraft.client.resource.pack.TexturePack original = mc.texturePacks.selectedPack;
		net.minecraft.client.resource.pack.TexturePack other = null;
		for (net.minecraft.client.resource.pack.TexturePack p : packs) {
			if (p != original) {
				other = p;
				break;
			}
		}
		for (int i = 0; i < packSwitches && other != null; i++) {
			mc.texturePacks.select(i % 2 == 0 ? other : original);
			mc.textureManager.reload();
		}
		mc.texturePacks.select(original);
		mc.textureManager.reload();
		Log.info("harness: " + packSwitches + " texture-pack switches; terrain objects before " + before + ", after "
				+ (terrain != null ? terrain.objectCounts() : "-"));
		META.put("pack_switches", packSwitches);
		packSwitches = 0;
	}

	/** Terrain has nothing left to build: Altrender's queue when it draws the terrain, else the game's. */
	private static boolean terrainIdle(Minecraft mc) {
		com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
		return terrain != null ? terrain.pending() == 0
				: ((WorldRendererAccess) mc.worldRenderer).altrender$dirtyChunks().isEmpty();
	}
}
