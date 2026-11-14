/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.render.FixedFunction;
import com.coredex.altshaders.Log;
import com.coredex.altshaders.pack.properties.CustomVariable;
import com.coredex.altshaders.pack.properties.PackDirectives;
import com.coredex.altshaders.uniform.StandardUniforms;
import com.coredex.altshaders.uniform.UniformDump;
import com.coredex.altshaders.uniform.UniformGl;
import com.coredex.altshaders.uniform.custom.CustomUniformFixedInputUniformsHolder;
import com.coredex.altshaders.uniform.custom.CustomUniforms;
import com.coredex.altshaders.uniform.custom.cached.CachedUniform;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.vec.Matrix3f;
import com.coredex.altshaders.uniform.vec.Matrix4f;
import com.coredex.altshaders.uniform.vec.Vector2f;
import com.coredex.altshaders.uniform.vec.Vector2i;
import com.coredex.altshaders.uniform.vec.Vector3f;
import com.coredex.altshaders.uniform.vec.Vector3i;
import com.coredex.altshaders.uniform.vec.Vector4f;
import com.coredex.altshaders.uniform.vec.Vector4i;

/**
 * Every program's uniforms (development.md 6.5): the standard set ({@link StandardUniforms}) and the pack's custom
 * uniforms and variables (shaders.properties, Iris's expression language), worked out once per frame and given to
 * each program the first time it's used in a frame, by the names and types the program declares. The game's fog and
 * the render stage follow the fixed-function state and the phase instead ({@link #fixedFunction}).
 */
public final class Uniforms {

	private static final int GL_FLOAT = 0x1406, GL_FLOAT_VEC2 = 0x8B50, GL_FLOAT_VEC3 = 0x8B51, GL_FLOAT_VEC4 = 0x8B52;
	private static final int GL_INT = 0x1404, GL_INT_VEC2 = 0x8B53, GL_INT_VEC3 = 0x8B54, GL_INT_VEC4 = 0x8B55;
	private static final int GL_UNSIGNED_INT = 0x1405, GL_BOOL = 0x8B56;
	private static final int GL_FLOAT_MAT3 = 0x8B5B, GL_FLOAT_MAT4 = 0x8B5C;

	private record Binding(CachedUniform uniform, int location, int glType) {
	}

	private final StandardUniforms standard;
	private final CustomUniforms custom;
	private final Map<PackProgram, List<Binding>> bindings = new IdentityHashMap<>();
	private final Map<PackProgram, Integer> applied = new IdentityHashMap<>();
	private final FunctionReturn value = new FunctionReturn();
	private final float[] fogColor = new float[4];
	private int frame;
	private int width;
	private int height;

	Uniforms(PackDirectives directives, List<CustomVariable> customVariables, com.coredex.altshaders.pack.IdMap ids) {
		standard = new StandardUniforms(directives);
		com.coredex.altrender.api.BlockNames names = AltrenderApi.blockNames();
		if (ids != null && names != null && !ids.getItemIdMap().isEmpty()) {
			Map<com.coredex.altshaders.pack.materialmap.NamespacedId, Integer> items = ids.getItemIdMap();
			standard.itemIds((id, damage) -> {
				String name = names.itemName(id, damage);
				return name == null ? -1
						: items.getOrDefault(new com.coredex.altshaders.pack.materialmap.NamespacedId(name), -1);
			});
		}
		CustomUniformFixedInputUniformsHolder.Builder inputs = new CustomUniformFixedInputUniformsHolder.Builder();
		standard.register(inputs);
		unknownBiomes(inputs, customVariables);
		CustomUniforms.Builder builder = new CustomUniforms.Builder();
		for (CustomVariable v : customVariables) {
			builder.addVariable(v.type(), v.name(), v.expression(), v.uniform());
		}
		custom = builder.build(inputs.build());
	}

	/**
	 * {@code BIOME_*} names a pack's expressions use that this game has no biome for (packs follow the newest
	 * version's list) are -1, an id no biome has: a test against them is false rather than the uniform failing.
	 */
	public static void unknownBiomes(CustomUniformFixedInputUniformsHolder.Builder inputs, List<CustomVariable> vars) {
		java.util.Set<String> names = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bBIOME_[A-Z0-9_]+\\b").matcher("");
		for (CustomVariable v : vars) {
			m.reset(v.expression());
			while (m.find()) {
				names.add(m.group());
			}
		}
		for (String n : names) {
			inputs.uniform1i(com.coredex.altshaders.uniform.UniformUpdateFrequency.ONCE, n, () -> -1);
		}
	}

	void frameStart(int width, int height) {
		this.width = width;
		this.height = height;
	}

	/** The shadow camera, before {@link #camera} works out the frame's values. */
	void shadow(float[] modelView, float[] projection) {
		standard.shadow(modelView, projection);
	}

	/** The frame's values, once the camera is set. */
	void camera(Gpu gpu) {
		UniformGl.gpu(gpu);
		standard.frame(AltrenderApi.gameState(), AltrenderApi.camera(), width, height);
		custom.updateAll();
		frame++;
		if (DUMP_EVERY > 0 && frame % DUMP_EVERY == 0) {
			if (dump == null) {
				dump = new UniformDump();
				standard.register(dump);
			}
			Log.info("uniforms at frame " + frame + ":\n  " + String.join("\n  ", dump.lines()));
		}
		if (frame == 3 && !unavailableUsed.isEmpty()) {
			Log.info("the pack reads uniforms this game version doesn't have (neutral values): "
					+ String.join(", ", new java.util.TreeSet<>(unavailableUsed)));
		}
	}

	/**
	 * {@code -Daltshaders.debug.uniformDump}: the standard uniforms logged every N frames ({@code true}: every 100).
	 */
	private static final int DUMP_EVERY = dumpEvery();
	private UniformDump dump;
	private final java.util.Set<String> unavailableUsed = new java.util.HashSet<>();

	private static int dumpEvery() {
		String v = System.getProperty("altshaders.debug.uniformDump");
		if (v == null || v.isBlank() || v.equals("false")) {
			return 0;
		}
		try {
			return Math.max(1, Integer.parseInt(v.trim()));
		} catch (NumberFormatException e) {
			return 100;
		}
	}

	/** Gives {@code p} the frame's values (once per frame; the program must be in use). */
	void apply(Gpu gpu, PackProgram p) {
		Integer at = applied.get(p);
		if (at != null && at == frame) {
			return;
		}
		applied.put(p, frame);
		List<Binding> list = bindings.computeIfAbsent(p, k -> bind(gpu, k));
		for (Binding b : list) {
			push(gpu, p, b);
		}
	}

	private List<Binding> bind(Gpu gpu, PackProgram p) {
		List<Binding> list = new ArrayList<>();
		for (Map.Entry<String, Integer> e : gpu.activeUniforms(p.program()).entrySet()) {
			if (StandardUniforms.UNAVAILABLE.contains(e.getKey())) {
				unavailableUsed.add(e.getKey());
			}
			CachedUniform u = custom.custom(e.getKey());
			if (u == null && custom.hasVariable(e.getKey())) {
				u = (CachedUniform) custom.getVariable(e.getKey());
			}
			if (u != null) {
				list.add(new Binding(u, p.location(gpu, e.getKey()), e.getValue()));
			}
		}
		return list;
	}

	/** One value, converted to the type the program declared (packs declare {@code worldTime} as int or float). */
	private void push(Gpu gpu, PackProgram p, Binding b) {
		b.uniform().writeTo(value);
		int l = b.location();
		switch (b.glType()) {
			case GL_FLOAT -> {
				float x = scalarFloat(b.uniform());
				if (p.changed(l, x, 0, 0, 0)) {
					gpu.uniform1f(l, x);
				}
			}
			case GL_INT, GL_UNSIGNED_INT, GL_BOOL -> {
				int x = scalarInt(b.uniform());
				if (p.changed(l, x, 0, 0, 0)) {
					gpu.uniform1i(l, x);
				}
			}
			case GL_FLOAT_VEC2 -> {
				if (value.objectReturn instanceof Vector2f v) {
					if (p.changed(l, v.x, v.y, 0, 0)) {
						gpu.uniform2f(l, v.x, v.y);
					}
				} else if (value.objectReturn instanceof Vector2i v) {
					if (p.changed(l, (float) v.x, v.y, 0, 0)) {
						gpu.uniform2f(l, v.x, v.y);
					}
				}
			}
			case GL_FLOAT_VEC3 -> {
				if (value.objectReturn instanceof Vector3f v) {
					if (p.changed(l, v.x, v.y, v.z, 0)) {
						gpu.uniform3f(l, v.x, v.y, v.z);
					}
				} else if (value.objectReturn instanceof Vector3i v) {
					if (p.changed(l, (float) v.x, v.y, v.z, 0)) {
						gpu.uniform3f(l, v.x, v.y, v.z);
					}
				}
			}
			case GL_FLOAT_VEC4 -> {
				if (value.objectReturn instanceof Vector4f v && p.changed(l, v.x, v.y, v.z, v.w)) {
					gpu.uniform4f(l, v.x, v.y, v.z, v.w);
				}
			}
			case GL_INT_VEC2 -> {
				if (value.objectReturn instanceof Vector2i v) {
					if (p.changed(l, v.x, v.y, 0, 0)) {
						gpu.uniform2i(l, v.x, v.y);
					}
				} else if (value.objectReturn instanceof Vector2f v) {
					if (p.changed(l, (int) v.x, (int) v.y, 0, 0)) {
						gpu.uniform2i(l, (int) v.x, (int) v.y);
					}
				}
			}
			case GL_INT_VEC3 -> {
				if (value.objectReturn instanceof Vector3i v && p.changed(l, v.x, v.y, v.z, 0)) {
					gpu.uniform3i(l, v.x, v.y, v.z);
				}
			}
			case GL_INT_VEC4 -> {
				if (value.objectReturn instanceof Vector4i v && p.changed(l, v.x, v.y, v.z, v.w)) {
					gpu.uniform4i(l, v.x, v.y, v.z, v.w);
				}
			}
			case GL_FLOAT_MAT4 -> {
				if (value.objectReturn instanceof Matrix4f m) {
					gpu.uniformMatrix4(l, m.m);
				}
			}
			case GL_FLOAT_MAT3 -> {
				if (value.objectReturn instanceof Matrix3f m) {
					gpu.uniformMatrix3(l, m.m);
				}
			}
			default -> {
				// a sampler or a type no standard uniform has: left alone
			}
		}
	}

	private float scalarFloat(CachedUniform u) {
		return switch (u.getType().toString()) {
			case "int" -> value.intReturn;
			case "bool" -> value.booleanReturn ? 1 : 0;
			default -> value.floatReturn;
		};
	}

	private int scalarInt(CachedUniform u) {
		return switch (u.getType().toString()) {
			case "float" -> (int) value.floatReturn;
			case "bool" -> value.booleanReturn ? 1 : 0;
			default -> value.intReturn;
		};
	}

	/**
	 * The game's fog now: {@code fogMode} (OptiFine: {@code GL_LINEAR}/{@code GL_EXP}/{@code GL_EXP2}, 0 with fog
	 * off), {@code fogStart}, {@code fogEnd}, {@code fogDensity}, {@code fogColor} and AltShaders'
	 * {@code fogDistanceMode} (the {@code GL_FOG_DISTANCE_MODE_NV} value: radial or eye-plane, as the game's own fog
	 * on this GPU), and AltShaders' {@code fixedTexture} and {@code fixedLighting} (1 where the game's own draw would
	 * be textured or lit). The program must be in use.
	 */
	void fixedFunction(Gpu gpu, PackProgram p, FixedFunction ff) {
		if (ff == null) {
			return;
		}
		alphaTest(gpu, p, ff.alphaTest() ? ff.alphaRef() : -1);
		i1(gpu, p, "fixedTexture", ff.texture() ? 1 : 0);
		i1(gpu, p, "fixedLighting", ff.lighting() ? 1 : 0);
		i1(gpu, p, "fogMode", ff.fog() ? ff.fogMode() : 0);
		f1(gpu, p, "fogStart", linearStart(ff));
		f1(gpu, p, "fogEnd", linearEnd(ff));
		f1(gpu, p, "fogDensity", ff.fogDensity());
		int l = p.location(gpu, "fogColor");
		if (l >= 0) {
			ff.fogColor(fogColor);
			if (p.changed(l, fogColor[0], fogColor[1], fogColor[2], 0)) {
				gpu.uniform3f(l, fogColor[0], fogColor[1], fogColor[2]);
			}
		}
		i1(gpu, p, "fogDistanceMode", ff.fogDistanceMode());
		i1(gpu, p, "fogShape", ff.fogDistanceMode() == 0x855B ? 0 : 1);
	}

	private static final int GL_EXP = 0x0800, GL_EXP2 = 0x0801;

	/**
	 * {@code fogStart}/{@code fogEnd} as a linear range packs can use (Iris's are always linear): with the game's
	 * exponential fog (b1.7.3 under water and in lava) the range where it reaches 99 % (ln 100 / density for
	 * {@code GL_EXP}, √ln 100 / density for {@code GL_EXP2}), from 0; else the game's own start and end.
	 */
	private static float linearStart(FixedFunction ff) {
		return ff.fogMode() == GL_EXP || ff.fogMode() == GL_EXP2 ? 0 : ff.fogStart();
	}

	private static float linearEnd(FixedFunction ff) {
		float d = Math.max(1e-4f, ff.fogDensity());
		return switch (ff.fogMode()) {
			case GL_EXP -> 4.6052f / d;
			case GL_EXP2 -> 2.1460f / d;
			default -> ff.fogEnd();
		};
	}

	/** The terrain's fog (start, end, density, colour), for the passes; NaN start until the terrain is first drawn. */
	private final float[] terrainFog = {Float.NaN, 0, 0, 0, 0, 0};

	/** The game's fog as the terrain is drawn with it: what the passes see (Iris: the environmental fog). */
	void terrainFog(FixedFunction ff) {
		if (ff == null) {
			return;
		}
		terrainFog[0] = linearStart(ff);
		terrainFog[1] = linearEnd(ff);
		terrainFog[2] = ff.fogDensity();
		ff.fogColor(fogColor);
		System.arraycopy(fogColor, 0, terrainFog, 3, 3);
	}

	/**
	 * A composite, deferred or compute pass's fog, as Iris gives it: fog off ({@code fogMode} 0, {@code fogShape}
	 * -1), and the terrain's start, end, density and colour (packs fade the land into the sky with them). The
	 * program must be in use.
	 */
	void passFog(Gpu gpu, PackProgram p) {
		if (Float.isNaN(terrainFog[0])) {
			terrainFog(AltrenderApi.fixedFunction());
			if (Float.isNaN(terrainFog[0])) {
				return;
			}
		}
		i1(gpu, p, "fogMode", 0);
		i1(gpu, p, "fogShape", -1);
		f1(gpu, p, "fogStart", terrainFog[0]);
		f1(gpu, p, "fogEnd", terrainFog[1]);
		f1(gpu, p, "fogDensity", terrainFog[2]);
		int l = p.location(gpu, "fogColor");
		if (l >= 0 && p.changed(l, terrainFog[3], terrainFog[4], terrainFog[5], 0)) {
			gpu.uniform3f(l, terrainFog[3], terrainFog[4], terrainFog[5]);
		}
	}

	/**
	 * The program's alpha test (Iris's, injected by {@link SourcePrep#alphaTest}): the pack's {@code alphaTest.*}
	 * for the program, else {@code gameRef} (the game's own test for the draw; -1: off). {@code alphaTestRef} follows
	 * it. The program must be in use.
	 */
	void alphaTest(Gpu gpu, PackProgram p, float gameRef) {
		float ref = gameRef;
		java.util.Optional<com.coredex.altshaders.pack.gl.AlphaTest> o = p.directives != null
				? p.directives.getAlphaTestOverride() : java.util.Optional.empty();
		if (o.isPresent()) {
			com.coredex.altshaders.pack.gl.AlphaTest t = o.get();
			ref = switch (t.function()) {
				case GREATER -> t.reference();
				case GEQUAL -> t.reference() - 1e-6f;
				case NEVER -> 2;
				default -> -1; // always, and the "less than" tests no pack uses (Review)
			};
		}
		f1(gpu, p, "altshaders_alphaTestRef", ref);
		f1(gpu, p, "alphaTestRef", Math.max(0, ref));
	}

	/**
	 * {@code entityId} and {@code blockEntityId} of the draw (-1: none) and its {@code entityColor} (the hurt or
	 * explosion flash). The program must be in use.
	 */
	void drawIds(Gpu gpu, PackProgram p, int entityId, int blockEntityId, float[] entityColor) {
		i1(gpu, p, "entityId", entityId);
		i1(gpu, p, "blockEntityId", blockEntityId);
		int l = p.location(gpu, "entityColor");
		if (l >= 0 && p.changed(l, entityColor[0], entityColor[1], entityColor[2], entityColor[3])) {
			gpu.uniform4f(l, entityColor[0], entityColor[1], entityColor[2], entityColor[3]);
		}
	}

	/** {@code renderStage}: the phase as Iris's {@code MC_RENDER_STAGE_*} value. The program must be in use. */
	void renderStage(Gpu gpu, PackProgram p, int stage) {
		i1(gpu, p, "renderStage", stage);
	}

	private static void i1(Gpu gpu, PackProgram p, String name, int v) {
		int l = p.location(gpu, name);
		if (l >= 0 && p.changed(l, v, 0, 0, 0)) {
			gpu.uniform1i(l, v);
		}
	}

	private static void f1(Gpu gpu, PackProgram p, String name, float v) {
		int l = p.location(gpu, name);
		if (l >= 0 && p.changed(l, v, 0, 0, 0)) {
			gpu.uniform1f(l, v);
		}
	}

	static void warn(String message) {
		Log.warn(message);
	}
}
