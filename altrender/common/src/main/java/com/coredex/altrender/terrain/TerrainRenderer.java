/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.coredex.altrender.Log;
import com.coredex.altrender.caps.Feature;
import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.platform.BlockMesher;
import com.coredex.altrender.platform.WorldView;
import com.coredex.altrender.strategy.BufferUsage;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.IndexType;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.strategy.ShaderStage;
import com.coredex.altrender.strategy.Strategies;
import com.coredex.altrender.strategy.StreamBuffer;
import com.coredex.altrender.terrain.arena.BufferArena;
import com.coredex.altrender.terrain.arena.BufferSegment;
import com.coredex.altrender.util.LongMap;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Altrender's terrain (development.md 2.3, 2.4, 4.2–4.4): sections meshed on workers from snapshots, all in one buffer
 * arena, positioned per region of 8 × 4 × 8 sections. Matrices and fog reach the shaders in one uniform block per
 * pass; the region's offsets are per-draw vertex attributes. Each pass is drawn
 * <ul>
 *   <li>with one indirect multi-draw (gl45), its commands written by the CPU (or, switched on, layer 0's by a compute
 *   shader where the driver has indirect count: {@link GpuCuller});</li>
 *   <li>else with one {@code multiDrawElements} per region (layer 0) or one draw per section (layer 1), the region's
 *   offsets set as constant attributes in between (gl33, or gl45 with indirect draws switched off).</li>
 * </ul>
 * The shader and every value it reads are the same on all paths, so they draw the same picture. Main thread only,
 * except the meshing tasks.
 */
public final class TerrainRenderer {

	private static final int REGION_W = 8;
	private static final int REGION_H = 4;
	private static final int REGION_D = 8;
	private static final int GL_EYE_RADIAL_NV = 0x855B;
	private static final int GL_LINEAR = 0x2601;
	private static final int GL_EXP = 0x0800;
	private static final int GL_EXP2 = 0x0801;
	/** Uniform block offsets: 256 is the largest alignment any driver asks for. */
	private static final int UNIFORM_ALIGN = 256;

	private final Strategies gl;
	private final BlockMesher mesher;
	private final MeshExecutor executor;
	private final SnapshotPool snapshots = new SnapshotPool();
	private final LongMap<Section> sections = new LongMap<>();
	private final LongMap<Region> regions = new LongMap<>();
	private final List<Built> finished = new ArrayList<>();
	private final List<CompletableFuture<Built>> inFlight = new ArrayList<>();
	private final int maxInFlight;
	/** Altrender's settings, read live (cave culling, the upload budget). */
	private final com.coredex.altrender.settings.Config config;
	private WorldView world;
	private int program;
	/** A world pipeline's terrain, or null (development.md 6.6). */
	private final com.coredex.altrender.api.render.TerrainPipeline pack;
	private final boolean extended;
	private final int[] blockIds;
	private final int packRevision;
	private final com.coredex.altrender.strategy.VertexLayout layout;
	private final com.coredex.altrender.strategy.VertexLayout layoutIndirect;
	/** Every section's vertices (development.md 4.3: one buffer, so a pass is one draw). */
	private BufferArena arena;
	/** gl45: indirect multi-draws ({@link Feature#MULTI_DRAW_INDIRECT}). */
	private final boolean indirect;
	/**
	 * Layer 0's commands from the GPU, or {@code null}. Off unless switched on (Advanced page, or
	 * {@code -Daltrender.terrain.gpuCommands=true})
	 * (and the driver has indirect count): measured 0–4% slower than CPU-written commands (docs/bench/b1.7.3.md); kept
	 * for cameras the visibility walk doesn't serve (shadow passes, Phase 6/7).
	 */
	private final GpuCuller culler;
	private final float[] regionData = new float[6];
	private int indexBuffer;
	private int indexQuads;
	private long meshesBuilt;
	private final double[] lastCamera = new double[3];
	private final Frustum frustum = new Frustum();
	private final float[] modelview = new float[16];
	private final float[] projection = new float[16];
	private final int[] lastDrawn = new int[SectionMesh.LAYERS];
	/** GL draw calls of the last pass per layer (development.md 4.3). */
	private final int[] lastCalls = new int[SectionMesh.LAYERS];
	/** CPU time in {@link #draw} (every pass), summed since the frame count below started (4.3's submission cost). */
	private long drawNanos;
	private long drawFrames;
	private final int[] lastCulled = new int[SectionMesh.LAYERS];
	private final List<Section> visible = new ArrayList<>();
	private final java.util.ArrayDeque<Section> bfsQueue = new java.util.ArrayDeque<>();
	// Reused per frame instead of allocated (development.md 3.7).
	private final List<Section> candidates = new ArrayList<>();
	private final List<Region> drawRegions = new ArrayList<>();
	private final List<Section> sortedList = new ArrayList<>();
	/** The frame {@link #sortedList} was sorted in, and whether the draw now may use it as it is (drawAgain). */
	private long sortedFor = -1;
	private boolean reuseSorted;
	private final float[] fogColor = new float[4];
	private int frame;
	private int visibleFrame = -1;
	private int lastVisited;
	/** Dirty sections (scheduling looks only here, development.md 3.1's overhead fix). */
	private final java.util.LinkedHashSet<Section> dirtySet = new java.util.LinkedHashSet<>();
	private final List<Section> urgentList = new ArrayList<>();
	private int rangeX = Integer.MIN_VALUE;
	private int rangeZ;
	private int rangeRadius;

	private final class Section {
		final int x;
		final int y;
		final int z;
		boolean dirty = true;
		boolean building;
		/** The last mesh read sky light: it rebuilds when the sky darkens or brightens (light is baked, 2.3). */
		boolean readSkyLight;
		/** Face connectivity of the last mesh ({@link Visibility}); all open until built. */
		long visibility = Visibility.ALL;
		int visitedFrame = -1;
		/** A block change near the camera: rebuilt within the frame (development.md 2.8). */
		boolean urgent;
		CompletableFuture<Built> future;
		BufferSegment segment;
		/** The game's block entities to draw for this section: drawn now, and captured with the build in flight. */
		List<Object> blockEntities;
		List<Object> pendingBlockEntities;
		int bfsEntered;
		int bfsTaken;
		/** The frame whose visible set holds this section (frustum + visibility walk). */
		int visibleFrame = -1;
		/** Its slot in the {@link GpuCuller}'s table, or -1. */
		int slot = -1;
		final int[] firstVertex = new int[SectionMesh.LAYERS];
		final int[] quads = new int[SectionMesh.LAYERS];
		/** Layer 0's quads with no see-through texels, which come first in it (the rest are its cut-out part). */
		int solidQuads;

		Section(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		void markDirty() {
			dirty = true;
			dirtySet.add(this);
		}

		int indexInRegion() {
			return (x & (REGION_W - 1)) << 5 | (y & (REGION_H - 1)) << 3 | (z & (REGION_D - 1));
		}
	}

	private static final class Region {
		final int x;
		final int y;
		final int z;
		/** Sections of the region with a mesh. */
		int sections;
		/** This draw's sections in the region (reused, development.md 3.7). */
		final List<Section> drawList = new ArrayList<>();

		Region(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	/** Each thread's meshing scratch (development.md 3.7): the quads are copied off-heap before the build returns. */
	private static final ThreadLocal<SectionMesh> MESH = ThreadLocal.withInitial(SectionMesh::new);

	/** A finished meshing task: the section, its encoded vertices (both layers, layer 0 first), quads per layer. */
	private record Built(Section section, ByteBuffer vertices, int[] quads, boolean readSkyLight, long visibility) {
	}

	public TerrainRenderer(Strategies gl, BlockMesher mesher, com.coredex.altrender.settings.Config config) {
		this(gl, mesher, config, null);
	}

	/**
	 * @param pack a world pipeline's terrain (development.md 6.6): mesh in the extended format with its block ids and
	 *             draw with its programs; null for Altrender's own
	 */
	public TerrainRenderer(Strategies gl, BlockMesher mesher, com.coredex.altrender.settings.Config config,
			com.coredex.altrender.api.render.TerrainPipeline pack) {
		this.pack = pack;
		this.extended = pack != null;
		this.layout = extended ? TerrainVertex.EXTENDED_LAYOUT : TerrainVertex.LAYOUT;
		this.layoutIndirect = extended ? TerrainVertex.EXTENDED_LAYOUT_INDIRECT : TerrainVertex.LAYOUT_INDIRECT;
		this.packRevision = pack != null ? pack.revision() : -1;
		if (pack != null) {
			blockIds = new int[1 << 16];
			for (int key = 0; key < blockIds.length; key++) {
				blockIds[key] = pack.blockId(key);
			}
		} else {
			blockIds = null;
		}
		this.gl = gl;
		this.mesher = mesher;
		this.config = config;
		this.executor = new MeshExecutor(MeshExecutor.configuredThreads(config.meshThreads()), mesher::newWorkerState);
		this.maxInFlight = executor.threads() * 4;
		ProgramStrategy p = gl.program();
		ProgramStrategy.Result vs = p.compile(ShaderStage.VERTEX, TerrainShaders.VERTEX);
		ProgramStrategy.Result fs = p.compile(ShaderStage.FRAGMENT, TerrainShaders.FRAGMENT);
		if (!vs.ok() || !fs.ok()) {
			throw new IllegalStateException("terrain shaders: " + vs.log() + " " + fs.log());
		}
		ProgramStrategy.Result linked = p.link(vs.name(), fs.name(), TerrainVertex.ATTRIBUTES, List.of("fragColor"));
		p.deleteShader(vs.name());
		p.deleteShader(fs.name());
		if (!linked.ok()) {
			throw new IllegalStateException("terrain program: " + linked.log());
		}
		program = linked.name();
		gl.uniform().configureProgram(program); // TerrainFrame → binding 0
		// Binding the program to set its sampler is game-visible state: inside a pass, so it's put back.
		gl.beginPass("terrain-setup");
		p.setSamplerUnit(program, "u_Texture", 0);
		gl.endPass();
		indirect = gl.has(Feature.MULTI_DRAW_INDIRECT);
		culler = indirect && gl.has(Feature.INDIRECT_COUNT) && config.gpuCommands() ? new GpuCuller(gl) : null;
		Log.info("terrain: renderer ready (" + executor.threads() + " mesh workers; draws: "
				+ (culler != null ? "indirect, layer 0 commands from the GPU" : indirect ? "indirect" : "multi-draw per region")
				+ ")");
	}

	public WorldView world() {
		return world;
	}

	/** The world changed (or went away): everything is dropped and rebuilt. */
	public void setWorld(WorldView newWorld) {
		if (world != null && newWorld != null && world.identity() == newWorld.identity()) {
			return;
		}
		clear();
		world = newWorld;
		readAtlas();
	}

	/**
	 * Which atlas sprites are solid (every texel kept by the 0.1 alpha test): layer 0's quads on them can be drawn
	 * without the test (a pipeline's solid program, early depth test), the rest with it. Null: unknown (everything in
	 * the cut-out part, as before).
	 */
	private volatile boolean[] solidSprites;

	private void readAtlas() {
		int[] pixels = null;
		try {
			pixels = world != null ? world.terrainAtlas() : null;
		} catch (RuntimeException e) {
			Log.warn("terrain: couldn't read the atlas: " + e);
		}
		solidSprites = pixels != null ? solidSprites(pixels) : null;
	}

	private static final ThreadLocal<int[]> SPLIT = ThreadLocal.withInitial(() -> new int[SectionMesh.INTS_PER_QUAD * 256]);

	/**
	 * Puts layer 0's quads on solid sprites first, the others after, each in their order (so a grass side's overlay
	 * still comes after the side); returns how many are solid (0 without a table).
	 */
	static int splitSolid(SectionMesh mesh, boolean[] solid) {
		int n = mesh.quadCount(0);
		if (solid == null || n == 0) {
			return 0;
		}
		int[] d = mesh.data(0);
		int[] cut = SPLIT.get();
		if (cut.length < n * SectionMesh.INTS_PER_QUAD) {
			cut = new int[n * SectionMesh.INTS_PER_QUAD * 2];
			SPLIT.set(cut);
		}
		int s = 0;
		int c = 0;
		for (int q = 0; q < n; q++) {
			int base = q * SectionMesh.INTS_PER_QUAD;
			float u = 0;
			float v = 0;
			for (int k = 0; k < 4; k++) {
				u += Float.intBitsToFloat(d[base + k * SectionMesh.INTS_PER_VERTEX + 3]);
				v += Float.intBitsToFloat(d[base + k * SectionMesh.INTS_PER_VERTEX + 4]);
			}
			int tx = Math.min(15, Math.max(0, (int) Math.floor(u * 4f))); // (u / 4) × 16 sprites
			int ty = Math.min(15, Math.max(0, (int) Math.floor(v * 4f)));
			if (solid[ty * 16 + tx]) {
				if (s != q) {
					System.arraycopy(d, base, d, s * SectionMesh.INTS_PER_QUAD, SectionMesh.INTS_PER_QUAD);
				}
				s++;
			} else {
				System.arraycopy(d, base, cut, c * SectionMesh.INTS_PER_QUAD, SectionMesh.INTS_PER_QUAD);
				c++;
			}
		}
		System.arraycopy(cut, 0, d, s * SectionMesh.INTS_PER_QUAD, c * SectionMesh.INTS_PER_QUAD);
		return s;
	}

	static boolean[] solidSprites(int[] argb) {
		int size = (int) Math.round(Math.sqrt(argb.length));
		if (size * size != argb.length || size < 16 || size % 16 != 0) {
			return null;
		}
		int tile = size / 16;
		boolean[] solid = new boolean[256];
		for (int sprite = 0; sprite < 256; sprite++) {
			int x0 = (sprite & 15) * tile;
			int y0 = (sprite >> 4) * tile;
			boolean all = true;
			for (int y = y0; y < y0 + tile && all; y++) {
				for (int x = x0; x < x0 + tile; x++) {
					if ((argb[y * size + x] >>> 24) < 26) { // the 0.1 alpha test drops it (a > 0.1)
						all = false;
						break;
					}
				}
			}
			solid[sprite] = all;
		}
		return solid;
	}

	/**
	 * Blocks in [x0, x1] × [y0, y1] × [z0, z1] changed: the sections covering them rebuild (the game's callers already
	 * widen the range by the neighbours a change affects).
	 */
	public void markDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int sx = x0 >> 4; sx <= x1 >> 4; sx++) {
			for (int sy = Math.max(0, y0 >> 4); sy <= y1 >> 4; sy++) {
				for (int sz = z0 >> 4; sz <= z1 >> 4; sz++) {
					Section s = sections.get(key(sx, sy, sz));
					if (s != null) {
						s.markDirty();
					}
				}
			}
		}
	}

	/**
	 * Everything rebuilds (graphics options, texture pack: block colours come from the pack's colour maps). The old
	 * meshes stay drawn until the new ones replace them.
	 */
	public void markAllDirty() {
		readAtlas(); // a texture pack switch comes this way: its sprites may differ
		for (Section s : sections.values()) {
			s.markDirty();
		}
	}

	/** Live GL objects of the terrain's strategies (the 2.7 pack-switch check). */
	public String objectCounts() {
		return com.coredex.altrender.debug.GlObjectCounts.describe(gl.counts().snapshot());
	}

	/**
	 * The world's sky darkening changed (time of day, weather): sections whose mesh read sky light rebuild, as the game
	 * rebuilds its own ({@code WorldRenderer#notifyAmbientDarknessChanged}; light is baked into vertex colours).
	 */
	public void skyChanged() {
		for (Section s : sections.values()) {
			if (s.readSkyLight) {
				s.markDirty();
			}
		}
	}

	/**
	 * A single block changed (a player's edit, or anything else the game sends one block at a time): if it's within
	 * two sections of the camera, its sections are rebuilt before the next frame is drawn.
	 */
	public void blockChanged(int x, int y, int z) {
		int cx = (int) Math.floor(lastCamera[0]) >> 4;
		int cy = (int) Math.floor(lastCamera[1]) >> 4;
		int cz = (int) Math.floor(lastCamera[2]) >> 4;
		if (Math.abs((x >> 4) - cx) > 2 || Math.abs((y >> 4) - cy) > 2 || Math.abs((z >> 4) - cz) > 2) {
			return;
		}
		for (int sx = (x - 1) >> 4; sx <= (x + 1) >> 4; sx++) {
			for (int sy = Math.max(0, (y - 1) >> 4); sy <= (y + 1) >> 4; sy++) {
				for (int sz = (z - 1) >> 4; sz <= (z + 1) >> 4; sz++) {
					Section s = sections.get(key(sx, sy, sz));
					if (s != null) {
						s.markDirty();
						if (!s.urgent) {
							s.urgent = true;
							urgentList.add(s);
						}
					}
				}
			}
		}
	}

	/** Quads (both layers) of section (sx, sy, sz) as drawn now, or -1 if it isn't built (debug). */
	public int quads(int sx, int sy, int sz) {
		Section s = sections.get(key(sx, sy, sz));
		return s == null || s.dirty || s.building ? -1 : s.quads[0] + s.quads[1];
	}

	/** Sections waiting to be (re)built or in flight: 0 once everything in range is built. */
	public int pending() {
		return inFlight.size() + finished.size() + dirtySet.size();
	}

	public long meshesBuilt() {
		return meshesBuilt;
	}

	/**
	 * Once per frame, main thread: uploads finished meshes, drops sections out of range, and starts meshing the dirty
	 * sections nearest the camera.
	 *
	 * @param radius render radius in sections (horizontal)
	 */
	public void update(double camX, double camY, double camZ, int radius) {
		if (world == null) {
			return;
		}
		frame++;
		drawFrames++;
		lastCulledObjects = culledObjects;
		culledObjects = 0;
		collect();
		rebuildUrgent();
		upload();
		int cx = (int) Math.floor(camX) >> 4;
		int cz = (int) Math.floor(camZ) >> 4;
		if (cx != rangeX || cz != rangeZ || radius != rangeRadius) {
			setRange(cx, cz, radius);
		}
		candidates.clear();
		for (Section s : dirtySet) {
			if (!s.building) {
				candidates.add(s);
			}
		}
		if (candidates.isEmpty() || inFlight.size() >= maxInFlight) {
			return;
		}
		double ccx = camX / 16.0 - 0.5;
		double ccy = camY / 16.0 - 0.5;
		double ccz = camZ / 16.0 - 0.5;
		candidates.sort((a, b) -> Double.compare(dist2(a, ccx, ccy, ccz), dist2(b, ccx, ccy, ccz)));
		for (Section s : candidates) {
			if (inFlight.size() >= maxInFlight) {
				break;
			}
			schedule(s);
		}
	}

	/**
	 * The camera's chunk or the render distance changed: sections out of range go (a build in flight for one is thrown
	 * away when it arrives), every section in range exists (new ones dirty). Chunks are made available only when a
	 * section is scheduled.
	 */
	private void setRange(int cx, int cz, int radius) {
		rangeX = cx;
		rangeZ = cz;
		rangeRadius = radius;
		sections.removeIf(s -> {
			if (Math.abs(s.x - cx) > radius || Math.abs(s.z - cz) > radius) {
				release(s);
				if (culler != null) {
					culler.freeSlot(s.slot);
				}
				return true;
			}
			return false;
		});
		int height = world.heightSections();
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				for (int y = 0; y < height; y++) {
					long k = key(x, y, z);
					if (!sections.containsKey(k)) {
						Section s = new Section(x, y, z);
						if (culler != null) {
							s.slot = culler.allocSlot();
						}
						sections.put(k, s);
						s.markDirty();
					}
				}
			}
		}
	}

	private static double dist2(Section s, double x, double y, double z) {
		double dx = s.x - x;
		double dy = s.y - y;
		double dz = s.z - z;
		return dx * dx + dy * dy + dz * dz;
	}

	private void schedule(Section s) {
		world.prepare(s.x, s.z);
		SectionSnapshot snapshot = snapshots.acquire();
		world.capture(s.x, s.y, s.z, snapshot);
		s.pendingBlockEntities = world.renderedBlockEntities(snapshot);
		s.dirty = false;
		dirtySet.remove(s);
		s.building = true;
		snapshot.extended = extended;
		snapshot.faceShade = pack == null || pack.faceShading();
		snapshot.separateAo = pack != null && pack.separateAo();
		int index = s.indexInRegion();
		CompletableFuture<Built> f;
		inFlight.add(f = executor.submit(() -> {
			try {
				SectionMesh mesh = MESH.get();
				mesh.clear();
				mesher.mesh(snapshot, mesh);
				int solid = extended ? splitSolid(mesh, solidSprites) : 0;
				int[] quads = {mesh.quadCount(0), mesh.quadCount(1), solid};
				int total = quads[0] + quads[1];
				ByteBuffer vertices = null;
				if (total > 0 && extended) {
					vertices = MemoryUtil.memAlloc(TerrainVertex.extendedBytes(total)).order(ByteOrder.LITTLE_ENDIAN);
					TerrainVertex.encodeExtended(mesh, 0, index, blockIds, vertices);
					TerrainVertex.encodeExtended(mesh, 1, index, blockIds, vertices);
					vertices.flip();
				} else if (total > 0) {
					vertices = MemoryUtil.memAlloc(TerrainVertex.bytes(total)).order(ByteOrder.LITTLE_ENDIAN);
					TerrainVertex.encode(mesh, 0, index, vertices);
					TerrainVertex.encode(mesh, 1, index, vertices);
					vertices.flip();
				}
				return new Built(s, vertices, quads, mesh.readSkyLight, mesh.visibility);
			} finally {
				snapshots.release(snapshot);
			}
		}));
		s.future = f;
	}

	/**
	 * Urgent sections (block changes near the camera) are meshed now and the frame waits for them, so the change is
	 * drawn in the frame that follows it; a build already in flight for one is waited for first.
	 */
	private void rebuildUrgent() {
		if (urgentList.isEmpty()) {
			return;
		}
		List<Section> urgent = new ArrayList<>();
		for (Section s : urgentList) {
			if (s.dirty && sections.get(key(s.x, s.y, s.z)) == s) {
				urgent.add(s);
			} else {
				s.urgent = false;
			}
		}
		urgentList.clear();
		if (urgent.isEmpty()) {
			return;
		}
		for (Section s : urgent) {
			if (s.building && s.future != null) {
				s.future.join();
			}
		}
		collect();
		List<CompletableFuture<Built>> wait = new ArrayList<>();
		for (Section s : urgent) {
			s.urgent = false;
			if (!s.building) {
				schedule(s);
				wait.add(s.future);
			}
		}
		for (CompletableFuture<Built> f : wait) {
			f.join();
		}
		collect();
		uploadAll();
	}

	private void collect() {
		for (Iterator<CompletableFuture<Built>> it = inFlight.iterator(); it.hasNext(); ) {
			CompletableFuture<Built> f = it.next();
			if (f.isDone()) {
				it.remove();
				finished.add(f.join());
			}
		}
	}

	private void upload() {
		// Vertex bytes uploaded per frame at most (the setting; -Daltrender.terrain.uploadBudgetMB overrides); the rest waits.
		upload(Long.getLong("altrender.terrain.uploadBudgetMB", config.uploadBudgetMb()) << 20);
	}

	private void uploadAll() {
		upload(Long.MAX_VALUE);
	}

	private void upload(long limit) {
		if (finished.isEmpty()) {
			return;
		}
		List<BufferArena.Upload> uploads = new ArrayList<>();
		Map<BufferArena.Upload, Built> owners = new HashMap<>();
		List<Section> changed = new ArrayList<>();
		long budget = limit;
		List<Built> later = new ArrayList<>();
		// Only the newest result per section counts (an urgent rebuild can follow one that was in flight).
		Map<Section, Built> newest = new HashMap<>();
		for (Built b : finished) {
			newest.put(b.section(), b);
		}
		for (Built b : finished) {
			if (newest.get(b.section()) != b) {
				free(b);
				continue;
			}
			if (budget <= 0) {
				later.add(b);
				continue;
			}
			if (b.vertices() != null) {
				budget -= b.vertices().remaining();
			}
			Section s = b.section();
			s.building = false;
			meshesBuilt++;
			if (sections.get(key(s.x, s.y, s.z)) != s) {
				// Dropped while it was being built.
				free(b);
				continue;
			}
			releaseSegment(s);
			world.replaceRenderedBlockEntities(s.blockEntities, s.pendingBlockEntities);
			s.blockEntities = s.pendingBlockEntities;
			s.pendingBlockEntities = null;
			s.readSkyLight = b.readSkyLight();
			s.visibility = b.visibility();
			s.quads[0] = b.quads()[0];
			s.quads[1] = b.quads()[1];
			s.solidQuads = b.quads()[2];
			s.firstVertex[0] = 0;
			s.firstVertex[1] = b.quads()[0] * 4;
			changed.add(s);
			if (b.vertices() == null) {
				continue;
			}
			ensureIndices(Math.max(b.quads()[0], b.quads()[1]));
			Region r = region(s);
			r.sections++;
			BufferArena.Upload u = new BufferArena.Upload(b.vertices());
			uploads.add(u);
			owners.put(u, b);
		}
		boolean moved = false;
		if (!uploads.isEmpty()) {
			if (arena == null) {
				arena = new BufferArena(gl.buffer(), extended ? TerrainVertex.EXTENDED_STRIDE : TerrainVertex.STRIDE,
						16 << 20);
			}
			moved = arena.upload(uploads);
			for (BufferArena.Upload u : uploads) {
				Built b = owners.get(u);
				b.section().segment = u.result();
				free(b);
			}
		}
		if (culler != null) {
			// A grown arena moves every segment: every slot's base vertex changes.
			for (Section s : moved ? sections.values() : changed) {
				updateSlot(s);
			}
		}
		finished.clear();
		finished.addAll(later);
	}

	private void updateSlot(Section s) {
		boolean has = s.segment != null && s.quads[0] > 0;
		culler.set(s.slot, s.x, s.y, s.z, has ? s.quads[0] * 6 : 0, has ? s.segment.offset() + s.firstVertex[0] : 0,
				has ? s.solidQuads * 6 : 0);
	}

	private static void free(Built b) {
		if (b.vertices() != null) {
			MemoryUtil.memFree(b.vertices());
		}
	}

	private Region region(Section s) {
		int rx = Math.floorDiv(s.x, REGION_W);
		int ry = Math.floorDiv(s.y, REGION_H);
		int rz = Math.floorDiv(s.z, REGION_D);
		long k = key(rx, ry, rz);
		Region r = regions.get(k);
		if (r == null) {
			r = new Region(rx, ry, rz);
			regions.put(k, r);
		}
		return r;
	}

	private void release(Section s) {
		releaseSegment(s);
		dirtySet.remove(s);
		if (s.blockEntities != null && world != null) {
			world.replaceRenderedBlockEntities(s.blockEntities, null);
			s.blockEntities = null;
		}
	}

	/** Frees the section's segment; a region whose last segment goes is deleted. */
	private void releaseSegment(Section s) {
		if (s.segment == null) {
			return;
		}
		s.segment.delete();
		s.segment = null;
		Region r = region0(s);
		if (r != null && --r.sections <= 0) {
			regions.remove(key(r.x, r.y, r.z));
		}
	}

	/** The shared index buffer: quads → two triangles (v0 v1 v2, v0 v2 v3, the game's order), enough for {@code quads}. */
	private void ensureIndices(int quads) {
		if (quads <= indexQuads) {
			return;
		}
		int n = Math.max(quads, Math.max(4096, indexQuads * 2));
		ByteBuffer b = MemoryUtil.memAlloc(n * 6 * 4).order(ByteOrder.nativeOrder());
		for (int q = 0; q < n; q++) {
			int v = q * 4;
			b.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v).putInt(v + 2).putInt(v + 3);
		}
		b.flip();
		if (indexBuffer != 0) {
			gl.buffer().delete(indexBuffer);
		}
		indexBuffer = gl.buffer().create(b, BufferUsage.INDEX);
		MemoryUtil.memFree(b);
		indexQuads = n;
	}

	/**
	 * Draws a layer at the game's terrain pass, with the game's current matrices, fog, alpha test, depth and blend
	 * state and bound terrain texture. Returns the number of sections drawn.
	 */
	public int draw(int layer, double camX, double camY, double camZ) {
		lastCamera[0] = camX;
		lastCamera[1] = camY;
		lastCamera[2] = camZ;
		if (regions.isEmpty() || indexBuffer == 0 || arena == null) {
			return 0;
		}
		if (layer == 1 && pack != null && !pack.translucentDepthDraw() && !GlStateTracker.colorMask(0)
				&& !GlStateTracker.colorMask(1) && !GlStateTracker.colorMask(2) && !GlStateTracker.colorMask(3)) {
			// The pipeline skips fancy water's depth-only draw: nothing drawn; 1 so the game still asks for the colour
			// draw (drawAgain then sorts for itself).
			return 1;
		}
		long started = System.nanoTime();
		gl.beginPass(layer == 0 ? "terrain0" : "terrain1");
		int packProgram = pack != null ? pack.program(layer == 0
				? com.coredex.altrender.api.render.TerrainPipeline.Pass.SOLID
				: com.coredex.altrender.api.render.TerrainPipeline.Pass.TRANSLUCENT) : 0;
		int cutoutProgram = packProgram != 0 && layer == 0
				? pack.program(com.coredex.altrender.api.render.TerrainPipeline.Pass.CUTOUT) : 0;
		if (SPLIT_OFF && cutoutProgram != 0) {
			packProgram = cutoutProgram; // the whole layer with the alpha test, as before the split
			cutoutProgram = 0;
		}
		if (packProgram != 0 && layer == 1 && !GlStateTracker.colorMask(0) && !GlStateTracker.colorMask(1)
				&& !GlStateTracker.colorMask(2) && !GlStateTracker.colorMask(3)) {
			// Depth only (fancy water's first draw): the pipeline's cheaper program for it, if it has one.
			int depth = pack.program(com.coredex.altrender.api.render.TerrainPipeline.Pass.TRANSLUCENT_DEPTH);
			if (depth != 0) {
				packProgram = depth;
			}
		}
		gl.state().useProgram(packProgram != 0 ? packProgram : program);
		atlas = GlStateTracker.boundTexture2D(0);
		int drawn;
		// Altrender's own program leaves cut-outs to the game's alpha test, even while a pipeline keeps it off.
		boolean lifted = packProgram == 0 && GlStateTracker.liftAlphaSuppression();
		try {
			bindFrameBlock(packProgram == 0);
			gl.state().bindTexture(0, GlState.TARGET_2D, GlStateTracker.boundTexture2D(0));
			// The game translates by (float) base - (float) camera; the same here, per 1024-block base.
			float offX = (float) camX;
			float offY = (float) camY;
			float offZ = (float) camZ;
			if (layer == 1) {
				drawn = drawSorted(camX, camY, camZ, offX, offY, offZ);
			} else if (cutoutProgram != 0) {
				// The pipeline's solid program on the solid quads (no alpha test: early depth test), then its cut-out
				// program on the rest.
				lastCalls[0] = 0;
				drawn = drawOpaque(camX, camY, camZ, offX, offY, offZ, 1);
				gl.state().useProgram(cutoutProgram);
				drawn += drawOpaque(camX, camY, camZ, offX, offY, offZ, 2);
			} else {
				lastCalls[0] = 0;
				drawn = drawOpaque(camX, camY, camZ, offX, offY, offZ);
			}
		} finally {
			if (lifted) {
				GlStateTracker.restoreAlphaSuppression();
			}
			gl.endPass();
			drawNanos += System.nanoTime() - started;
		}
		lastDrawn[layer] = drawn;
		return drawn;
	}

	/**
	 * The pass's matrices and fog, as the game has them now, into the stream and bound as {@code TerrainFrame}
	 * (development.md 4.4: one block per pass, no {@code glUniform*}). Also sets the frustum.
	 */
	private void bindFrameBlock(boolean own) {
		GlStateTracker.matrix(0x1700, modelview); // GL_MODELVIEW
		GlStateTracker.matrix(0x1701, projection); // GL_PROJECTION
		frustum.set(projection, modelview);
		if (!own) {
			return; // a pipeline's program reads the game's matrices and does its own fog
		}
		boolean fog = GlStateTracker.isEnabled(0x0B60); // GL_FOG
		int mode = !fog ? 0 : switch (GlStateTracker.fogMode()) {
			case GL_LINEAR -> 1;
			case GL_EXP -> 2;
			case GL_EXP2 -> 3;
			default -> 1;
		};
		GlStateTracker.fogColor(fogColor);
		StreamBuffer stream = gl.stream();
		long at = stream.alloc(TerrainShaders.FRAME_BYTES, UNIFORM_ALIGN);
		ByteBuffer m = stream.memory();
		int o = (int) at;
		for (int i = 0; i < 16; i++) {
			m.putFloat(o + i * 4, modelview[i]);
			m.putFloat(o + 64 + i * 4, projection[i]);
		}
		for (int i = 0; i < 4; i++) {
			m.putFloat(o + 128 + i * 4, fogColor[i]);
		}
		m.putFloat(o + 144, GlStateTracker.fogStart());
		m.putFloat(o + 148, GlStateTracker.fogEnd());
		m.putFloat(o + 152, GlStateTracker.fogDensity());
		m.putFloat(o + 156, 0f);
		m.putInt(o + 160, mode);
		m.putInt(o + 164, GlStateTracker.fogDistanceMode() == GL_EYE_RADIAL_NV ? 1 : 0);
		m.putInt(o + 168, 0);
		m.putInt(o + 172, 0);
		stream.commit(at, TerrainShaders.FRAME_BYTES);
		gl.uniform().bindBlock(0, stream.buffer(), at, TerrainShaders.FRAME_BYTES);
	}

	/** The view's blending: {@code blend} for all buffers (null or NONE: off), then per attachment. */
	private void viewBlend(com.coredex.altrender.api.gpu.Blend blend, com.coredex.altrender.api.gpu.Blend[] buffers) {
		boolean on = blend != null && !blend.equals(com.coredex.altrender.api.gpu.Blend.NONE);
		if (on) {
			gl.state().blendGl(true, blend.srcRgb(), blend.dstRgb(), blend.srcAlpha(), blend.dstAlpha());
		} else {
			gl.state().blendGl(false, 0, 0, 0, 0);
		}
		if (buffers == null) {
			return;
		}
		org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
		boolean functions = caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
		for (int i = 0; i < buffers.length; i++) {
			com.coredex.altrender.api.gpu.Blend b = buffers[i];
			if (b == null) {
				continue;
			}
			if (b.equals(com.coredex.altrender.api.gpu.Blend.NONE)) {
				gl.state().blendGl(i, false, 0, 0, 0, 0);
			} else if (functions) {
				gl.state().blendGl(i, true, b.srcRgb(), b.dstRgb(), b.srcAlpha(), b.dstAlpha());
			} else {
				org.lwjgl.opengl.GL30C.glEnablei(org.lwjgl.opengl.GL11C.GL_BLEND, i); // the rest's function
			}
		}
	}

	/** Layer 0: every visible section with opaque or cutout quads, in any order (depth-tested). */
	private int drawOpaque(double camX, double camY, double camZ, float offX, float offY, float offZ) {
		return drawOpaque(camX, camY, camZ, offX, offY, offZ, 0);
	}

	/** Layer 0's quads of a part: 0 all, 1 the solid ones, 2 the cut-out ones (after the solid ones). */
	private static int partQuads(Section s, int part) {
		return part == 1 ? s.solidQuads : part == 2 ? s.quads[0] - s.solidQuads : s.quads[0];
	}

	private static int partFirst(Section s, int part) {
		return s.firstVertex[0] + (part == 2 ? s.solidQuads * 4 : 0);
	}

	private int drawOpaque(double camX, double camY, double camZ, float offX, float offY, float offZ, int part) {
		List<Section> visibleNow = visibleSections(camX, camY, camZ);
		if (culler != null) {
			culler.clearSelection();
			int n = 0;
			for (Section s : visibleNow) {
				if (s.segment != null && partQuads(s, part) > 0) {
					culler.select(s.slot);
					n++;
				}
			}
			if (n > 0) {
				culler.draw(arena.buffer(), indexBuffer, offX, offY, offZ, layoutIndirect, part);
			}
			lastCalls[0] += n > 0 ? 1 : 0;
			return n;
		}
		for (Region r : drawRegions) {
			r.drawList.clear();
		}
		drawRegions.clear();
		int total = 0;
		for (Section s : visibleNow) {
			if (s.segment != null && partQuads(s, part) > 0) {
				Region r = region0(s);
				if (r.drawList.isEmpty()) {
					drawRegions.add(r);
				}
				r.drawList.add(s);
				total++;
			}
		}
		if (total == 0) {
			return 0;
		}
		if (indirect) {
			lastCalls[0] += 1;
			IndirectBatch batch = new IndirectBatch(total);
			for (Region r : drawRegions) {
				regionData(r.x, r.y, r.z, offX, offY, offZ, regionData);
				for (Section s : r.drawList) {
					batch.add(partQuads(s, part) * 6, s.segment.offset() + partFirst(s, part), regionData);
				}
			}
			batch.draw();
			return total;
		}
		gl.vertexInput().bind(layout, arena.buffer(), indexBuffer);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer offsets = stack.mallocPointer(REGION_W * REGION_H * REGION_D);
			IntBuffer counts = stack.mallocInt(REGION_W * REGION_H * REGION_D);
			IntBuffer bases = stack.mallocInt(REGION_W * REGION_H * REGION_D);
			for (Region r : drawRegions) {
				regionConstants(r, offX, offY, offZ);
				int n = 0;
				for (Section s : r.drawList) {
					offsets.put(n, 0L);
					counts.put(n, partQuads(s, part) * 6);
					bases.put(n, s.segment.offset() + partFirst(s, part));
					n++;
				}
				gl.vertexInput().multiDrawElements(Primitive.TRIANGLES, IndexType.U32, offsets, counts, bases, n);
			}
		}
		lastCalls[0] += drawRegions.size();
		return total;
	}

	/** Indirect commands and their per-draw data, written to the stream in draw order, drawn with one call. */
	private final class IndirectBatch {
		private final StreamBuffer stream = gl.stream();
		private final long commandsAt;
		private final long perDrawAt;
		private final ByteBuffer m = stream.memory();
		private int n;

		IndirectBatch(int draws) {
			commandsAt = stream.alloc(draws * 20L, 16);
			perDrawAt = stream.alloc((long) draws * TerrainVertex.DRAW_STRIDE, 16);
			if (commandsAt < 0 || perDrawAt < 0) {
				throw new IllegalStateException(draws + " terrain draws don't fit the stream buffer");
			}
		}

		void add(int indexCount, int baseVertex, float[] data) {
			int c = (int) commandsAt + n * 20;
			m.putInt(c, indexCount).putInt(c + 4, 1).putInt(c + 8, 0).putInt(c + 12, baseVertex).putInt(c + 16, n);
			int d = (int) perDrawAt + n * TerrainVertex.DRAW_STRIDE;
			for (int i = 0; i < 6; i++) {
				m.putFloat(d + i * 4, data[i]);
			}
			n++;
		}

		void draw() {
			stream.commit(commandsAt, n * 20L);
			stream.commit(perDrawAt, (long) n * TerrainVertex.DRAW_STRIDE);
			gl.vertexInput().bind(layoutIndirect, arena.buffer(), indexBuffer);
			gl.vertexInput().bindInstances(layoutIndirect, stream.buffer(), perDrawAt);
			gl.vertexInput().multiDrawElementsIndirect(Primitive.TRIANGLES, IndexType.U32, stream.buffer(), commandsAt, n);
		}
	}

	/**
	 * The sections to draw this frame (development.md 2.5), computed at its first terrain draw: in the frustum and,
	 * with cave culling, reachable from the camera's section through connected faces, never stepping back toward the
	 * camera (the walk the game's later versions and Sodium use).
	 */
	private List<Section> visibleSections(double camX, double camY, double camZ) {
		if (visibleFrame == frame) {
			return visible;
		}
		visibleFrame = frame;
		visible.clear();
		lastCulled[0] = 0;
		int cx = (int) Math.floor(camX) >> 4;
		int cy = (int) Math.floor(camY) >> 4;
		int cz = (int) Math.floor(camZ) >> 4;
		Section start = sections.get(key(cx, cy, cz));
		if (!config.caveCulling() || start == null) {
			// Camera above or below the world (or culling off): frustum only.
			for (Section s : sections.values()) {
				if (inFrustum(s, camX, camY, camZ)) {
					visible.add(s);
					s.visibleFrame = frame;
				}
			}
			lastVisited = visible.size();
			return visible;
		}
		java.util.ArrayDeque<Section> queue = bfsQueue;
		queue.clear();
		start.visitedFrame = frame;
		start.visibleFrame = frame;
		start.bfsEntered = -1;
		start.bfsTaken = 0;
		visible.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			Section s = queue.poll();
			int entered = s.bfsEntered;
			int taken = s.bfsTaken;
			for (int f = 0; f < 6; f++) {
				if ((taken & 1 << Visibility.opposite(f)) != 0) {
					continue; // never back toward the camera
				}
				if (entered >= 0 && !Visibility.connected(s.visibility, entered, f)) {
					continue;
				}
				Section n = sections.get(key(s.x + Visibility.DX[f], s.y + Visibility.DY[f], s.z + Visibility.DZ[f]));
				if (n == null || n.visitedFrame == frame) {
					continue;
				}
				n.visitedFrame = frame;
				if (!inFrustum(n, camX, camY, camZ)) {
					continue;
				}
				visible.add(n);
				n.visibleFrame = frame;
				n.bfsEntered = Visibility.opposite(f);
				n.bfsTaken = taken | 1 << f;
				queue.add(n);
			}
		}
		lastVisited = visible.size();
		return visible;
	}

	/**
	 * Whether anything in the box may be seen this frame (development.md 3.4/3.5): true if a section it overlaps (one
	 * block of margin) is in this frame's visible set, or isn't tracked (out of range, before the first terrain draw).
	 * Entities and block entities in sections the frustum or the visibility walk ruled out are skipped.
	 */
	public boolean boxVisible(double x0, double y0, double z0, double x1, double y1, double z1) {
		if (visibleFrame != frame) {
			return true;
		}
		int sx0 = (int) Math.floor(x0 - 1) >> 4;
		int sy0 = Math.max(0, (int) Math.floor(y0 - 1) >> 4);
		int sz0 = (int) Math.floor(z0 - 1) >> 4;
		int sx1 = (int) Math.floor(x1 + 1) >> 4;
		int sy1 = (int) Math.floor(y1 + 1) >> 4;
		int sz1 = (int) Math.floor(z1 + 1) >> 4;
		if (sy1 >= (world != null ? world.heightSections() : 8) || sy0 > sy1) {
			return true; // above the world: no section to judge by
		}
		for (int x = sx0; x <= sx1; x++) {
			for (int y = sy0; y <= sy1; y++) {
				for (int z = sz0; z <= sz1; z++) {
					Section s = sections.get(key(x, y, z));
					if (s == null || s.visibleFrame == frame) {
						return true;
					}
				}
			}
		}
		culledObjects++;
		return false;
	}

	private int culledObjects;
	private int lastCulledObjects;

	/** Entities and block entities skipped by {@link #boxVisible} since the last call (stats). */
	public int takeCulledObjects() {
		int n = culledObjects;
		culledObjects = 0;
		return n;
	}

	private boolean inFrustum(Section s, double camX, double camY, double camZ) {
		float x0 = (float) (s.x * 16 - camX);
		float y0 = (float) (s.y * 16 - camY);
		float z0 = (float) (s.z * 16 - camZ);
		if (frustum.intersects(x0, y0, z0, x0 + 16, y0 + 16, z0 + 16)) {
			return true;
		}
		lastCulled[0]++;
		return false;
	}

	/**
	 * A region's per-draw data: {@code a_RegionOffset} = (1024-block base) - camera in float, as the game translates,
	 * and {@code a_RegionInner} = the region's origin in its base ({@link GpuCuller}'s shader computes the same).
	 */
	private static void regionData(int rx, int ry, int rz, float offX, float offY, float offZ, float[] out) {
		int ox = rx * REGION_W * 16;
		int oy = ry * REGION_H * 16;
		int oz = rz * REGION_D * 16;
		int baseX = ox - (ox & 1023);
		int baseZ = oz - (oz & 1023);
		out[0] = (float) baseX - offX;
		out[1] = 0f - offY;
		out[2] = (float) baseZ - offZ;
		out[3] = ox & 1023;
		out[4] = oy;
		out[5] = oz & 1023;
	}

	/** The region's per-draw data as constant attributes (the paths without indirect draws). */
	private void regionConstants(Region r, float offX, float offY, float offZ) {
		float[] d = regionData;
		regionData(r.x, r.y, r.z, offX, offY, offZ, d);
		gl.vertexInput().constantAttribute(TerrainVertex.REGION_OFFSET, d[0], d[1], d[2]);
		gl.vertexInput().constantAttribute(TerrainVertex.REGION_INNER, d[3], d[4], d[5]);
	}

	/**
	 * Translucent sections one by one, nearest first, as the game draws them (its Fast graphics draws them in one pass
	 * with depth writes, so the order decides what shows; development.md 2.6 sorts within sections).
	 */
	private int drawSorted(double camX, double camY, double camZ, float offX, float offY, float offZ) {
		List<Section> list = sortedList;
		lastCalls[1] = 0;
		if (reuseSorted && sortedFor == frame) {
			// drawAgain: the list as the frame's first translucent draw sorted it.
			return drawSortedList(list, camX, camY, camZ, offX, offY, offZ);
		}
		list.clear();
		for (Section s : visibleSections(camX, camY, camZ)) {
			if (s.segment != null && s.quads[1] > 0) {
				list.add(s);
			}
		}
		sortedFor = frame;
		if (list.isEmpty()) {
			return 0;
		}
		list.sort((a, b) -> {
			int c = Double.compare(centerDistance(a, camX, camY, camZ), centerDistance(b, camX, camY, camZ));
			if (c != 0) {
				return c;
			}
			c = Integer.compare(a.y, b.y);
			return c != 0 ? c : a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.x, b.x);
		});
		return drawSortedList(list, camX, camY, camZ, offX, offY, offZ);
	}

	/** Draws the sorted translucent sections, nearest first. */
	private int drawSortedList(List<Section> list, double camX, double camY, double camZ, float offX, float offY,
			float offZ) {
		if (list.isEmpty()) {
			return 0;
		}
		if (indirect) {
			// One multi-draw: its commands run in order, so nearest first still holds.
			IndirectBatch batch = new IndirectBatch(list.size());
			for (Section s : list) {
				Region r = region0(s);
				regionData(r.x, r.y, r.z, offX, offY, offZ, regionData);
				batch.add(s.quads[1] * 6, s.segment.offset() + s.firstVertex[1], regionData);
			}
			batch.draw();
			lastCalls[1] = 1;
			return list.size();
		}
		gl.vertexInput().bind(layout, arena.buffer(), indexBuffer);
		Region bound = null;
		for (Section s : list) {
			Region r = region0(s);
			if (r != bound) {
				regionConstants(r, offX, offY, offZ);
				bound = r;
			}
			gl.vertexInput().drawElements(Primitive.TRIANGLES, s.quads[1] * 6, IndexType.U32, 0L,
					s.segment.offset() + s.firstVertex[1], 1);
		}
		lastCalls[1] = list.size();
		return list.size();
	}

	/** RenderChunk#squaredDistanceToCenter: centre = origin + 8. */
	private static double centerDistance(Section s, double x, double y, double z) {
		double dx = x - (s.x * 16 + 8);
		double dy = y - (s.y * 16 + 8);
		double dz = z - (s.z * 16 + 8);
		return dx * dx + dy * dy + dz * dz;
	}

	/**
	 * The game's Fancy translucent pass draws layer 1 twice: depth only through {@code render}, then colour through
	 * {@code renderLastChunks}, which has no camera; this draws the layer again from the last {@link #draw}'s camera.
	 */
	/** The terrain atlas as the game last bound it for a terrain pass (for views drawn outside one). */
	private int atlas;
	private final Frustum viewFrustum = new Frustum();
	/** The solid/cut-out split of layer 0 under a pipeline ({@code -Daltrender.debug.cutoutSplit.disable}: off). */
	private static final boolean SPLIT_OFF = Boolean.getBoolean("altrender.debug.cutoutSplit.disable");
	/** Shadow culling for views ({@code -Daltrender.debug.shadowCulling.disable}: off, for comparisons). */
	private static final boolean SHADOW_CULLING_OFF = Boolean.getBoolean("altrender.debug.shadowCulling.disable");
	private final Frustum playerFrustum = new Frustum();
	private final ShadowCuller shadowCuller = new ShadowCuller();
	private final float[] cullModelview = new float[16];
	private final float[] cullProjection = new float[16];
	/** The CPU path's sections per region, kept between frames (cleared, not made, each draw). */
	private final java.util.Map<Region, List<Section>> viewByRegion = new java.util.LinkedHashMap<>();
	/** Sections the last view draw selected, per layer (stats). */
	private final int[] lastViewSections = new int[2];
	private final float[] viewData = new float[6];

	/**
	 * The terrain seen from another camera (development.md 6.7, the API's terrain view: a shader pack's shadow map):
	 * the sections within {@code maxDistance} blocks of the player's camera that {@code projection · modelView} keeps
	 * (no cave culling: what the sun sees isn't what the player sees), drawn with {@code program} into
	 * {@code framebuffer} with the depth test on, face culling off (on with the culling's {@code backFaces}), the
	 * game's alpha test at 0.1, the matrices as the fixed-function modelview and projection, and {@code blend} for
	 * every buffer (null: off), then {@code buffers} per attachment (null entries: as the rest). Positions are relative
	 * to the player's camera.
	 */
	public int drawView(int framebuffer, int vx, int vy, int vw, int vh, float[] modelView, float[] projection,
			double camX, double camY, double camZ, float maxDistance, int what, int program,
			com.coredex.altrender.api.gpu.Blend blend, com.coredex.altrender.api.gpu.Blend[] buffers,
			com.coredex.altrender.api.render.ViewCulling culling) {
		if (regions.isEmpty() || indexBuffer == 0 || arena == null || atlas == 0) {
			return 0;
		}
		// what: 1 layer 0's solid quads, 2 its cut-out quads, 3 layer 1
		int layer = what == 3 ? 1 : 0;
		int part = what == 3 ? 0 : what;
		viewFrustum.set(projection, modelView);
		boolean culls = culling != null && (culling.distance() > 0 || culling.toLight() != null
				|| culling.safeDistance() > 0) && !SHADOW_CULLING_OFF;
		if (culls) {
			// The player's view: the camera's matrices as the game has them now (the pass runs once they're set).
			GlStateTracker.matrix(0x1700, cullModelview); // GL_MODELVIEW
			GlStateTracker.matrix(0x1701, cullProjection); // GL_PROJECTION
			playerFrustum.set(cullProjection, cullModelview);
			shadowCuller.set(playerFrustum, culling.toLight(), culling.distance(), culling.safeDistance());
		}
		float offX = (float) camX;
		float offY = (float) camY;
		float offZ = (float) camZ;
		// Layer 0 on the GPU-driven path when there is one: the sections' bits, then one indirect draw.
		boolean gpu = culler != null && layer == 0;
		if (gpu) {
			culler.clearSelection();
		} else {
			viewByRegion.values().forEach(List::clear);
		}
		int selected = 0;
		float reach = maxDistance + 16;
		for (Section s : sections.values()) {
			if (s.segment == null || (layer == 0 ? partQuads(s, part) : s.quads[1]) == 0) {
				continue;
			}
			float x0 = (float) (s.x * 16 - camX);
			float y0 = (float) (s.y * 16 - camY);
			float z0 = (float) (s.z * 16 - camZ);
			if (Math.abs(x0 + 8) > reach || Math.abs(z0 + 8) > reach) {
				continue;
			}
			if (!viewFrustum.intersects(x0, y0, z0, x0 + 16, y0 + 16, z0 + 16)) {
				continue;
			}
			if (culls && !shadowCuller.visible(x0, y0, z0, x0 + 16, y0 + 16, z0 + 16)) {
				continue;
			}
			if (gpu) {
				culler.select(s.slot);
			} else {
				viewByRegion.computeIfAbsent(region0(s), k -> new ArrayList<>()).add(s);
			}
			selected++;
		}
		lastViewSections[layer] = part == 2 ? lastViewSections[0] + selected : selected;
		if (selected == 0) {
			return 0;
		}
		gl.beginPass("terrain-view");
		int mode = GlStateTracker.matrixMode();
		boolean alpha = GlStateTracker.isEnabled(0x0BC0);
		int alphaFunc = GlStateTracker.alphaFunc();
		float alphaRef = GlStateTracker.alphaRef();
		int drawn = 0;
		try {
			gl.state().bindDrawFramebuffer(framebuffer);
			gl.state().viewport(vx, vy, vw, vh);
			gl.state().depthGl(0x0203, true); // GL_LEQUAL
			viewBlend(blend, buffers);
			gl.state().cullGl(culling != null && culling.backFaces()); // both sides unless asked (Iris: both)
			gl.state().colorMask(15);
			gl.state().useProgram(program);
			gl.state().bindTexture(0, GlState.TARGET_2D, atlas);
			org.lwjgl.opengl.GL11.glEnable(0x0BC0);
			org.lwjgl.opengl.GL11.glAlphaFunc(0x0204, 0.1f); // GL_GREATER
			org.lwjgl.opengl.GL11.glMatrixMode(0x1701);
			org.lwjgl.opengl.GL11.glPushMatrix();
			org.lwjgl.opengl.GL11.glLoadMatrixf(projection);
			org.lwjgl.opengl.GL11.glMatrixMode(0x1700);
			org.lwjgl.opengl.GL11.glPushMatrix();
			org.lwjgl.opengl.GL11.glLoadMatrixf(modelView);
			if (gpu) {
				culler.draw(arena.buffer(), indexBuffer, offX, offY, offZ, layoutIndirect, part);
				drawn = selected;
			} else {
			gl.vertexInput().bind(layout, arena.buffer(), indexBuffer);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				int max = REGION_W * REGION_H * REGION_D;
				PointerBuffer offsets = stack.mallocPointer(max);
				IntBuffer counts = stack.mallocInt(max);
				IntBuffer bases = stack.mallocInt(max);
				for (java.util.Map.Entry<Region, List<Section>> e : viewByRegion.entrySet()) {
					if (e.getValue().isEmpty()) {
						continue;
					}
					Region r = e.getKey();
					regionData(r.x, r.y, r.z, offX, offY, offZ, viewData);
					gl.vertexInput().constantAttribute(TerrainVertex.REGION_OFFSET, viewData[0], viewData[1], viewData[2]);
					gl.vertexInput().constantAttribute(TerrainVertex.REGION_INNER, viewData[3], viewData[4], viewData[5]);
					int n = 0;
					for (Section s : e.getValue()) {
						offsets.put(n, 0L);
						counts.put(n, (layer == 0 ? partQuads(s, part) : s.quads[1]) * 6);
						bases.put(n, s.segment.offset() + (layer == 0 ? partFirst(s, part) : s.firstVertex[1]));
						n++;
					}
					gl.vertexInput().multiDrawElements(Primitive.TRIANGLES, IndexType.U32, offsets, counts, bases, n);
					drawn += n;
				}
			}
			}
		} finally {
			org.lwjgl.opengl.GL11.glMatrixMode(0x1700);
			org.lwjgl.opengl.GL11.glPopMatrix();
			org.lwjgl.opengl.GL11.glMatrixMode(0x1701);
			org.lwjgl.opengl.GL11.glPopMatrix();
			org.lwjgl.opengl.GL11.glMatrixMode(mode);
			org.lwjgl.opengl.GL11.glAlphaFunc(alphaFunc, alphaRef);
			if (!alpha) {
				org.lwjgl.opengl.GL11.glDisable(0x0BC0);
			}
			gl.endPass();
		}
		return drawn;
	}

	/** The pipeline terrain revision this renderer was made for (-1: Altrender's own). */
	public int packRevision() {
		return packRevision;
	}

	public int drawAgain(int layer) {
		reuseSorted = layer == 1; // same frame, same camera: the depth draw's sorted sections
		try {
			return draw(layer, lastCamera[0], lastCamera[1], lastCamera[2]);
		} finally {
			reuseSorted = false;
		}
	}

	private Region region0(Section s) {
		return regions.get(key(Math.floorDiv(s.x, REGION_W), Math.floorDiv(s.y, REGION_H), Math.floorDiv(s.z, REGION_D)));
	}

	/** Drops every section and buffer (world change). */
	public void clear() {
		for (CompletableFuture<Built> f : inFlight) {
			f.thenAccept(TerrainRenderer::free);
		}
		inFlight.clear();
		for (Built b : finished) {
			free(b);
		}
		finished.clear();
		if (arena != null) {
			arena.delete();
			arena = null;
		}
		if (culler != null) {
			culler.reset();
		}
		viewByRegion.clear();
		regions.clear();
		if (world != null) {
			for (Section s : sections.values()) {
				if (s.blockEntities != null) {
					world.replaceRenderedBlockEntities(s.blockEntities, null);
				}
			}
		}
		sections.clear();
		dirtySet.clear();
		urgentList.clear();
		rangeX = Integer.MIN_VALUE;
	}

	public void delete() {
		clear();
		executor.shutdown();
		if (indexBuffer != 0) {
			gl.buffer().delete(indexBuffer);
			indexBuffer = 0;
		}
		if (culler != null) {
			culler.delete();
		}
		gl.program().deleteProgram(program);
		gl.vertexInput().clear();
	}

	/** Region count, arena bytes used and allocated (F3 later; the 2.3 leak check). */
	/** Lines for the debug screen (development.md 5.3). */
	public void debugLines(java.util.List<String> out) {
		long used = arena != null ? arena.usedBytes() : 0;
		long allocated = arena != null ? arena.allocatedBytes() : 0;
		out.add("Sections: " + lastDrawn[0] + " drawn (" + lastDrawn[1] + " translucent) of " + sections.size()
				+ ", " + regions.size() + " regions");
		out.add("Culled: " + lastCulled[0] + " by frustum, " + Math.max(0, sections.size() - lastVisited - lastCulled[0])
				+ " behind terrain, " + lastCulledObjects + " entities");
		out.add("Draw calls: " + lastCalls[0] + " opaque, " + lastCalls[1] + " translucent"
				+ (culler != null ? " (GPU commands)" : indirect ? " (indirect)" : ""));
		out.add("Arena: " + (used >> 20) + " / " + (allocated >> 20) + " MB");
		out.add("Builds: " + dirtySet.size() + " waiting, " + inFlight.size() + " meshing, " + finished.size()
				+ " to upload");
	}

	/** Restarts the drawing-time average (a benchmark window starts). */
	public void resetDrawTimer() {
		drawNanos = 0;
		drawFrames = 0;
	}

	public String stats() {
		long used = arena != null ? arena.usedBytes() : 0;
		long allocated = arena != null ? arena.allocatedBytes() : 0;
		return "sections " + sections.size() + ", regions " + regions.size() + ", arena " + (used >> 20) + "/"
				+ (allocated >> 20) + " MB, in flight " + inFlight.size() + ", snapshots " + snapshots.created()
				+ ", drawn " + lastDrawn[0] + "/" + lastDrawn[1] + " in " + lastCalls[0] + "/" + lastCalls[1]
				+ " draw calls, " + String.format(java.util.Locale.ROOT, "%.1f", drawFrames == 0 ? 0.0
						: drawNanos / 1e3 / drawFrames) + " µs/frame drawing (reached " + lastVisited + ", frustum culled "
				+ lastCulled[0] + "), entities/block entities culled " + lastCulledObjects
				+ (lastViewSections[0] + lastViewSections[1] > 0
						? ", other view (shadow) " + lastViewSections[0] + "/" + lastViewSections[1] : "");
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF);
	}
}
