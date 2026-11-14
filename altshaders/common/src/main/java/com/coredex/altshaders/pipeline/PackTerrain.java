/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.coredex.altrender.api.BlockNames;
import com.coredex.altrender.api.render.TerrainPipeline;
import com.coredex.altshaders.pack.IdMap;
import com.coredex.altshaders.pack.materialmap.BlockEntry;

/**
 * A pack's terrain for Altrender (development.md 6.6): the terrain programs and the block ids from its
 * block.properties, matched to the version's blocks by modern name and state (the first entry that matches wins, as
 * Iris's mapping keeps the file's order).
 */
final class PackTerrain implements TerrainPipeline {

	private static final AtomicInteger REVISIONS = new AtomicInteger();

	private record Candidate(int packId, Map<String, String> state) {
	}

	private final PackProgram solid;
	private final PackProgram cutout;
	private final PackProgram translucent;
	private final PackProgram translucentDepth;
	private final boolean oldLighting;
	private final boolean separateAo;
	private final BlockNames names;
	private final Map<String, List<Candidate>> byName = new HashMap<>();
	/** Tag entries in the file's order: a block no name entry matches takes the first of its tags that does. */
	private final List<Map.Entry<String, Candidate>> byTag = new ArrayList<>();
	private final int revision = REVISIONS.incrementAndGet();

	/**
	 * {@code solid}: the opaque layer's program for quads without see-through texels (no alpha test), or null for
	 * {@code cutout}'s on the whole layer.
	 */
	PackTerrain(PackProgram solid, PackProgram cutout, PackProgram translucent, PackProgram translucentDepth, IdMap ids,
			BlockNames names, boolean oldLighting, boolean separateAo, boolean translucentDepthDraw) {
		this.translucentDepthDraw = translucentDepthDraw;
		this.oldLighting = oldLighting;
		this.separateAo = separateAo;
		this.solid = solid;
		this.cutout = cutout;
		this.translucent = translucent;
		this.translucentDepth = translucentDepth;
		this.names = names;
		if (ids != null && ids.getBlockProperties() != null) {
			ids.getBlockProperties().forEach((packId, entries) -> {
				for (BlockEntry e : entries) {
					byName.computeIfAbsent(e.id().toString(), k -> new ArrayList<>())
							.add(new Candidate(packId, e.propertyPredicates()));
				}
			});
		}
		if (ids != null && ids.getTagEntries() != null) {
			ids.getTagEntries().forEach((packId, entries) -> {
				for (com.coredex.altshaders.pack.materialmap.TagEntry e : entries) {
					byTag.add(Map.entry(e.id().toString(), new Candidate(packId, e.propertyPredicates())));
				}
			});
		}
	}

	@Override
	public int program(Pass pass) {
		PackProgram p = switch (pass) {
			case SOLID -> solid != null ? solid : cutout;
			case CUTOUT -> solid != null ? cutout : null; // without a solid program, SOLID's draws the whole layer
			case TRANSLUCENT -> translucent;
			case TRANSLUCENT_DEPTH -> translucentDepth;
		};
		return p != null ? p.program() : 0;
	}

	@Override
	public int blockId(int blockKey) {
		String name = names != null ? names.name(blockKey) : null;
		if (name == null) {
			return -1;
		}
		Map<String, String> state = names.properties(blockKey);
		List<Candidate> list = byName.get(name);
		if (list != null) {
			for (Candidate c : list) {
				if (matches(c, state)) {
					return c.packId();
				}
			}
		}
		// Iris: tag entries only fill in what the block entries left.
		if (!byTag.isEmpty()) {
			java.util.Set<String> tags = names.tags(blockKey);
			if (!tags.isEmpty()) {
				for (Map.Entry<String, Candidate> t : byTag) {
					if (tags.contains(t.getKey()) && matches(t.getValue(), state)) {
						return t.getValue().packId();
					}
				}
			}
		}
		return -1;
	}

	private static boolean matches(Candidate c, Map<String, String> state) {
		for (Map.Entry<String, String> want : c.state().entrySet()) {
			String have = state.get(want.getKey());
			// A property the version doesn't have counts as its default, which packs rarely ask about.
			if (have != null && !have.equals(want.getValue())) {
				return false;
			}
		}
		return true;
	}

	/** Iris's {@code oldLighting} (default off): without it, no per-face shading in the vertex colour. */
	@Override
	public boolean faceShading() {
		return oldLighting;
	}

	/** Iris's {@code separateAo}: ambient occlusion in the colour's alpha. */
	@Override
	public boolean separateAo() {
		return separateAo;
	}

	@Override
	public int revision() {
		return revision;
	}

	private final boolean translucentDepthDraw;

	/** Whether fancy water's depth-only draw happens (see {@link PackPipeline#WATER_DEPTH_DRAW}). */
	@Override
	public boolean translucentDepthDraw() {
		return translucentDepthDraw;
	}
}
