/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.util.Json;

/**
 * A capture scene (development.md 0.7), read from JSON (tools/testscene/scenes/*.json):
 *
 * <pre>
 * { "name": "overworld_day", "seed": 20261112, "dimension": "overworld" | "nether",
 *   "anchor": "column" | "snowy" | "player",  // where block/camera offsets start: the surface of anchorXZ, the
 *                                           // nearest snowy column's surface (searched from anchorXZ), or the
 *                                           // player after a dimension change (who stood on anchorXZ before)
 *   "anchorXZ": [35, 34],                   // fixed: b1.7.3 picks its spawn point with an unseeded random
 *   "anchorDy": 0,                          // added to the anchor's y (e.g. -30 for a room underground)
 *   "camera": [dx, dy, dz, yaw, pitch], "time": 6000, "rain": 0.0, "fov": 70, "size": [1280, 720],
 *   "options": { "fancyGraphics": true, "ao": true, "viewDistance": 1, "fpsLimit": 0 },   // options.txt keys,
 *                                           // and "altrender.renderDistance": chunks (Altrender's own setting)
 *   "pack": "ar_debug.zip",                 // texture pack in texturepacks/, or "default"
 *   "blocks": [[dx, dy, dz, id, meta], ...],   // or [x0, y0, z0, x1, y1, z1, id, meta] boxes (inclusive)
 *   "entities": [{ "type": "Pig", "at": [dx, dy, dz], "yaw": 90, "state": "hurt" }],  // state: hurt, swell
 *   "particles": [{ "type": "flame", "at": [dx, dy, dz] }],   // the game's particle names, spawned once (frozen)
 *   "settleFrames": 60, "shotGap": 60, "benchSeconds": 0,
 *   "sleepAt": [dx, dy, dz],                 // optional: the player sleeps in the bed there (a night scene)
 *   "hand": true }                           // optional: the hand and HUD drawn (b1.7.3 hides both with hideGui)
 * </pre>
 */
public record Scene(String name, long seed, String dimension, String anchor, int[] anchorXZ, int anchorDy, double[] camera, long time,
		float rain, float fov, int width, int height, Map<String, String> options, String pack, List<int[]> blocks,
		List<EntitySpec> entities, int settleFrames, int shotGap, int benchSeconds, int[] sleepAt, boolean hand,
		List<EntitySpec> particles) {

	/** {@code state}: null, {@code hurt} (red flash) or {@code swell} (a creeper about to explode). */
	public record EntitySpec(String type, double[] at, float yaw, String state) {
	}

	@SuppressWarnings("unchecked")
	public static Scene load(Path file) throws IOException {
		Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
		Map<String, String> options = new LinkedHashMap<>();
		for (var e : ((Map<String, Object>) j.getOrDefault("options", Map.of())).entrySet()) {
			Object v = e.getValue();
			options.put(e.getKey(), v instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(v));
		}
		List<int[]> blocks = new ArrayList<>();
		for (Object b : (List<Object>) j.getOrDefault("blocks", List.of())) {
			blocks.add(ints((List<Object>) b));
		}
		List<EntitySpec> entities = new ArrayList<>();
		for (Object o : (List<Object>) j.getOrDefault("entities", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			entities.add(new EntitySpec((String) m.get("type"), doubles((List<Object>) m.get("at")),
					num(m, "yaw", 0).floatValue(), (String) m.get("state")));
		}
		List<EntitySpec> particles = new ArrayList<>();
		for (Object o : (List<Object>) j.getOrDefault("particles", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			particles.add(new EntitySpec((String) m.get("type"), doubles((List<Object>) m.get("at")), 0, null));
		}
		List<Object> size = (List<Object>) j.getOrDefault("size", List.of(1280.0, 720.0));
		String name = (String) j.getOrDefault("name", file.getFileName().toString().replace(".json", ""));
		return new Scene(name, num(j, "seed", 0).longValue(), (String) j.getOrDefault("dimension", "overworld"),
				(String) j.getOrDefault("anchor", "column"), ints((List<Object>) j.getOrDefault("anchorXZ", List.of(0.0, 0.0))),
				num(j, "anchorDy", 0).intValue(),
				doubles((List<Object>) j.getOrDefault("camera", List.of(0.0, 2.0, 0.0, 0.0, 0.0))), num(j, "time", 6000).longValue(),
				num(j, "rain", 0).floatValue(), num(j, "fov", 70).floatValue(), ((Double) size.get(0)).intValue(),
				((Double) size.get(1)).intValue(), options, (String) j.getOrDefault("pack", "ar_debug.zip"), blocks, entities,
				num(j, "settleFrames", 60).intValue(), num(j, "shotGap", 60).intValue(), num(j, "benchSeconds", 0).intValue(),
				j.containsKey("sleepAt") ? ints((List<Object>) j.get("sleepAt")) : null,
				Boolean.TRUE.equals(j.get("hand")), particles);
	}

	private static Double num(Map<String, Object> j, String key, double fallback) {
		Object v = j.get(key);
		return v instanceof Double d ? d : fallback;
	}

	private static int[] ints(List<Object> l) {
		int[] r = new int[l.size()];
		for (int k = 0; k < r.length; k++) {
			r[k] = ((Double) l.get(k)).intValue();
		}
		return r;
	}

	private static double[] doubles(List<Object> l) {
		double[] r = new double[l.size()];
		for (int k = 0; k < r.length; k++) {
			r[k] = (Double) l.get(k);
		}
		return r;
	}
}
