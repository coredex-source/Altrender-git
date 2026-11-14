/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import net.minecraft.world.chunk.WorldChunk;

/**
 * {@code -Daltrender.debug.meshTest} (development.md 2.2): every loaded section within 8 chunks of the player is meshed
 * twice: on the main thread from the live world through the game's own {@code WorldRegion} and
 * {@code Tesselator.INSTANCE} (what {@code RenderChunk} would produce), and from snapshots on the meshing workers
 * ({@code -Daltrender.meshThreads}, default 8 here), three rounds with all sections in flight at once. Each layer's
 * quad count and stream hash, and the "read sky light" flag, must match the reference.
 */
public final class MeshSelfTest {

	private static final boolean ENABLED = Boolean.getBoolean("altrender.debug.meshTest");
	private static final int WAIT_FRAMES = 200;
	private static final int RADIUS = 8;
	private static final int ROUNDS = 3;
	private static int frames;
	private static boolean done;

	private record Result(long[] hash, int[] quads, boolean sky) {
		static Result of(SectionMesh m, boolean sky) {
			long[] h = new long[SectionMesh.LAYERS];
			int[] q = new int[SectionMesh.LAYERS];
			for (int l = 0; l < SectionMesh.LAYERS; l++) {
				h[l] = m.hash(l);
				q[l] = m.quadCount(l);
			}
			return new Result(h, q, sky);
		}

		boolean same(Result o) {
			return java.util.Arrays.equals(hash, o.hash) && java.util.Arrays.equals(quads, o.quads) && sky == o.sky;
		}
	}

	private MeshSelfTest() {
	}

	/** Called at the start of each world frame. */
	public static void onWorldFrame(Minecraft minecraft) {
		if (!ENABLED || done || minecraft.world == null || minecraft.player == null || ++frames < WAIT_FRAMES) {
			return;
		}
		done = true;
		run(minecraft.world, (int) Math.floor(minecraft.player.x) >> 4, (int) Math.floor(minecraft.player.z) >> 4);
	}

	private static void run(World world, int pcx, int pcz) {
		List<int[]> sections = new ArrayList<>();
		for (int cx = pcx - RADIUS; cx <= pcx + RADIUS; cx++) {
			for (int cz = pcz - RADIUS; cz <= pcz + RADIUS; cz++) {
				if (loaded(world, cx, cz)) {
					for (int sy = 0; sy < 8; sy++) {
						sections.add(new int[] {cx, sy, cz});
					}
				}
			}
		}
		// Reference: the game's own path, single-threaded.
		long t0 = System.nanoTime();
		List<Result> reference = new ArrayList<>(sections.size());
		SectionMesh mesh = new SectionMesh();
		long quads = 0;
		for (int[] s : sections) {
			int ox = s[0] << 4;
			int oy = s[1] << 4;
			int oz = s[2] << 4;
			WorldRegion region = new WorldRegion(world, ox - 1, oy - 1, oz - 1, ox + 17, oy + 17, oz + 17);
			WorldChunk.hasSkyLight = false;
			SectionMesher.mesh(region, ox, oy, oz, Tesselator.INSTANCE, mesh);
			Result r = Result.of(mesh, WorldChunk.hasSkyLight);
			reference.add(r);
			quads += r.quads[0] + r.quads[1];
		}
		long referenceMs = (System.nanoTime() - t0) / 1_000_000;

		int threads = Integer.getInteger("altrender.meshThreads", 8);
		MeshExecutor executor = new MeshExecutor(threads, MeshWorkerState::new);
		SnapshotPool pool = new SnapshotPool();
		int mismatches = 0;
		List<String> examples = new ArrayList<>();
		long workerMs = 0;
		long captureMs = 0;
		try {
			for (int round = 0; round < ROUNDS; round++) {
				long r0 = System.nanoTime();
				List<CompletableFuture<Result>> futures = new ArrayList<>(sections.size());
				for (int[] s : sections) {
					SectionSnapshot snapshot = pool.acquire();
					long c0 = System.nanoTime();
					SnapshotCapture.capture(world, s[0], s[1], s[2], snapshot);
					captureMs += System.nanoTime() - c0;
					futures.add(executor.submit(() -> {
						MeshWorkerState state = (MeshWorkerState) MeshThread.currentState();
						SnapshotWorldView view = new SnapshotWorldView(snapshot);
						SectionMesh m = new SectionMesh();
						SectionMesher.mesh(view, snapshot.originX(), snapshot.originY(), snapshot.originZ(),
								state.tesselator, m);
						pool.release(snapshot);
						return Result.of(m, view.readSkyLight());
					}));
				}
				for (int i = 0; i < sections.size(); i++) {
					Result ours = futures.get(i).join();
					if (!ours.same(reference.get(i))) {
						mismatches++;
						if (examples.size() < 10) {
							int[] s = sections.get(i);
							Result ref = reference.get(i);
							examples.add("section " + s[0] + "," + s[1] + "," + s[2] + " quads " + ref.quads[0] + "/"
									+ ref.quads[1] + " vs " + ours.quads[0] + "/" + ours.quads[1] + " sky " + ref.sky
									+ " vs " + ours.sky + (java.util.Arrays.equals(ref.hash, ours.hash) ? "" : " hash differs"));
						}
					}
				}
				workerMs += (System.nanoTime() - r0) / 1_000_000;
			}
		} catch (RuntimeException e) {
			Log.error("mesh test: a worker failed", e);
			mismatches++;
		} finally {
			executor.shutdown();
		}
		String timing = "reference " + referenceMs + " ms single-threaded; workers " + workerMs / ROUNDS
				+ " ms per round incl. " + captureMs / 1_000_000 / ROUNDS + " ms of main-thread capture";
		if (mismatches == 0) {
			Log.info("mesh test: " + sections.size() + " sections (" + quads + " quads) × " + ROUNDS + " rounds on "
					+ threads + " workers: all equal to the game's single-threaded capture (" + timing + ")");
		} else {
			Log.warn("mesh test: " + mismatches + " of " + sections.size() * ROUNDS + " section meshes differ (" + timing
					+ "); first: " + examples);
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
