/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.coredex.altrender.Log;
import com.coredex.altrender.mc.adapter.SnapshotCapture;
import com.coredex.altrender.mc.adapter.SnapshotWorldView;
import com.coredex.altrender.mc.terrain.MeshWorkerState;
import com.coredex.altrender.mc.terrain.SectionMesher;
import com.coredex.altrender.terrain.MeshExecutor;
import com.coredex.altrender.terrain.MeshThread;
import com.coredex.altrender.terrain.SectionMesh;
import com.coredex.altrender.terrain.SectionSnapshot;
import com.coredex.altrender.terrain.SnapshotPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SurvivalInteractionManager;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.world.World;

/**
 * {@code -Daltrender.debug.meshStress=<seconds>} (development.md 2.2's endurance check): starts a world, flies the
 * player along +x (4 blocks/s by default) 120 blocks up (so chunks keep loading and generating), and meshes up to 32 random
 * loaded sections near the player every frame on the workers ({@code -Daltrender.meshThreads}, 8 here by default)
 * while the game runs normally (ticks, light updates, collisions, its own renderer). Logs progress every minute and
 * stops the game at the end. Any worker exception is counted and logged.
 */
public final class MeshStressTest {

	private static final int SECONDS = Integer.getInteger("altrender.debug.meshStress", 0);
	private static final int PER_FRAME = 32;
	private static final int MAX_IN_FLIGHT = 256;
	private static final int RADIUS = 6;
	/** Blocks per second ({@code -Daltrender.debug.meshStress.speed}); b1.7.3's world generation keeps up with about 5. */
	private static final double SPEED = Double.parseDouble(System.getProperty("altrender.debug.meshStress.speed", "4"));

	private static MeshExecutor executor;
	private static SnapshotPool pool;
	private static final Deque<CompletableFuture<Integer>> IN_FLIGHT = new ArrayDeque<>();
	private static final AtomicLong QUADS = new AtomicLong();
	private static final AtomicInteger ERRORS = new AtomicInteger();
	private static final Random RANDOM = new Random(2026);
	private static long meshes;
	private static long start;
	private static long lastReport;
	private static double startX;
	private static double startZ;
	private static boolean started;
	private static boolean finished;

	private MeshStressTest() {
	}

	/** Called at the start of every frame. */
	public static void onFrame(Minecraft mc) {
		if (SECONDS <= 0 || finished) {
			return;
		}
		if (!started) {
			if (mc.world == null && mc.screen instanceof TitleScreen) {
				mc.interactionManager = new SurvivalInteractionManager(mc);
				mc.startGame("ar-stress", "ar-stress", 20261112L);
				mc.openScreen(null);
				int threads = Integer.getInteger("altrender.meshThreads", 8);
				executor = new MeshExecutor(threads, MeshWorkerState::new);
				pool = new SnapshotPool();
				startX = mc.player.x;
				startZ = mc.player.z;
				start = lastReport = System.nanoTime();
				started = true;
				Log.info("mesh stress: " + SECONDS + " s, " + threads + " workers, flying +x at " + SPEED + " blocks/s");
			}
			return;
		}
		World world = mc.world;
		if (world == null || mc.player == null) {
			return;
		}
		long now = System.nanoTime();
		double seconds = (now - start) / 1e9;
		mc.player.setPosition(startX + seconds * SPEED, 120.0, startZ);
		mc.player.velocityX = 0;
		mc.player.velocityY = 0;
		mc.player.velocityZ = 0;

		while (!IN_FLIGHT.isEmpty() && IN_FLIGHT.peekFirst().isDone()) {
			IN_FLIGHT.pollFirst().join();
			meshes++;
		}
		int pcx = (int) Math.floor(mc.player.x) >> 4;
		int pcz = (int) Math.floor(mc.player.z) >> 4;
		for (int i = 0; i < PER_FRAME && IN_FLIGHT.size() < MAX_IN_FLIGHT; i++) {
			int cx = pcx + RANDOM.nextInt(2 * RADIUS + 1) - RADIUS;
			int cz = pcz + RANDOM.nextInt(2 * RADIUS + 1) - RADIUS;
			if (!loaded(world, cx, cz)) {
				continue;
			}
			SectionSnapshot snapshot = pool.acquire();
			SnapshotCapture.capture(world, cx, RANDOM.nextInt(8), cz, snapshot);
			IN_FLIGHT.addLast(executor.submit(() -> {
				try {
					SectionMesh m = new SectionMesh();
					SectionMesher.mesh(new SnapshotWorldView(snapshot), snapshot.originX(), snapshot.originY(),
							snapshot.originZ(), ((MeshWorkerState) MeshThread.currentState()).tesselator, m);
					QUADS.addAndGet(m.quadCount(0) + m.quadCount(1));
					return 1;
				} catch (Throwable t) {
					if (ERRORS.incrementAndGet() <= 5) {
						Log.error("mesh stress: worker failed", t);
					}
					return 0;
				} finally {
					pool.release(snapshot);
				}
			}));
		}

		if (now - lastReport >= 60_000_000_000L || seconds >= SECONDS) {
			lastReport = now;
			Runtime rt = Runtime.getRuntime();
			com.coredex.altrender.terrain.TerrainRenderer terrain = com.coredex.altrender.render.Renderer.terrain();
			Log.info(String.format(java.util.Locale.ROOT,
					"mesh stress: %.0f s, %d meshes (%d quads), %d errors, %d in flight, %d snapshots, heap %d MB, x=%.0f%s",
					seconds, meshes, QUADS.get(), ERRORS.get(), IN_FLIGHT.size(), pool.created(),
					(rt.totalMemory() - rt.freeMemory()) >> 20, mc.player.x,
					terrain != null ? "; terrain: " + terrain.stats() + ", " + terrain.meshesBuilt() + " built" : ""));
		}
		if (seconds >= SECONDS) {
			finished = true;
			executor.shutdown();
			Log.info("mesh stress: done, " + meshes + " meshes, " + ERRORS.get() + " errors");
			mc.stop();
		}
	}

	private static boolean loaded(World world, int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!world.getChunkSource().hasChunk(cx + dx, cz + dz)) {
					return false;
				}
			}
		}
		return true;
	}
}
