/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.uniform;

import static com.coredex.altshaders.uniform.UniformUpdateFrequency.PER_FRAME;

import com.coredex.altrender.api.CameraState;
import com.coredex.altrender.api.GameState;
import com.coredex.altshaders.pack.BiomeCategories;
import com.coredex.altshaders.pack.Biomes;
import com.coredex.altshaders.pack.properties.PackDirectives;
import com.coredex.altshaders.uniform.vec.Matrix4f;
import com.coredex.altshaders.uniform.vec.Vector2i;
import com.coredex.altshaders.uniform.vec.Vector3f;
import com.coredex.altshaders.uniform.vec.Vector4f;

/**
 * The uniforms OptiFine and Iris give every program (development.md 6.5), by their names and meanings (OptiFine's
 * shaders.txt "Uniforms", Iris's additions), from the API's camera and game state. Values are worked out once per
 * frame ({@link #frame}); custom uniforms in shaders.properties read them as variables. Uniforms that can't exist on
 * this game version (night vision, darkness, …, {@link #UNAVAILABLE}) are their neutral values; per-draw ones (entity
 * ids) come with 6.8.
 */
public final class StandardUniforms {

	private static final double LN_2 = Math.log(2.0);

	/**
	 * Uniforms this game version can't have (development.md 6.5): they keep a neutral value (no effect, no hunger,
	 * not riding, …). docs/compat/shaderpacks.md lists them; a pack that reads one is told once.
	 */
	public static final java.util.Set<String> UNAVAILABLE = java.util.Set.of("nightVision", "blindness",
			"darknessFactor", "darknessLightFactor", "playerMood", "is_sprinting", "is_invisible", "currentPlayerHunger",
			"maxPlayerHunger", "constantMood", "endFlashIntensity", "previousEndFlashIntensity", "endFlashPosition", "inSwimmingAnimation",
			"isElytraFlying", "isSpectator", "heldItemId2", "heldBlockLightValue2", "heldBlockLightColor2",
			"currentColorSpace", "textureFilteringMode");

	private final PackDirectives directives;
	// camera
	private final Matrix4f modelView = new Matrix4f();
	private final Matrix4f modelViewInverse = new Matrix4f();
	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f projectionInverse = new Matrix4f();
	private final Matrix4f previousModelView = new Matrix4f();
	private final Matrix4f previousProjection = new Matrix4f();
	private final Matrix4f shadowModelView = new Matrix4f();
	private final Matrix4f shadowModelViewInverse = new Matrix4f();
	private final Matrix4f shadowProjection = new Matrix4f();
	private final Matrix4f shadowProjectionInverse = new Matrix4f();
	private final double[] position = new double[3];
	private final Vector3f cameraPosition = new Vector3f();
	private final Vector3f previousCameraPosition = new Vector3f();
	private final Vector3f sunPosition = new Vector3f();
	private final Vector3f moonPosition = new Vector3f();
	private final Vector3f shadowLightPosition = new Vector3f();
	private final Vector3f upPosition = new Vector3f();
	private final Vector3f skyColor = new Vector3f();
	private final Vector2i eyeBrightness = new Vector2i();
	private final Vector2i eyeBrightnessSmooth = new Vector2i();
	private final com.coredex.altshaders.uniform.vec.Vector2i atlasSize = new Vector2i(256, 256);
	private final com.coredex.altshaders.uniform.vec.Vector3i cameraPositionInt = new com.coredex.altshaders.uniform.vec.Vector3i();
	private final Vector3f cameraPositionFract = new Vector3f();
	private final com.coredex.altshaders.uniform.vec.Vector3i previousCameraPositionInt = new com.coredex.altshaders.uniform.vec.Vector3i();
	private final Vector3f previousCameraPositionFract = new Vector3f();
	private final Vector3f lookVector = new Vector3f(0, 0, -1);
	private float frameTimeSmooth = -1;
	private float velocity;
	private float smoothBlock = -1;
	private float smoothSky = -1;
	private float wetness = -1;
	private float sunAngle;
	private float shadowAngle;
	private float near = 0.05f;
	private float far = 256;
	private float viewWidth;
	private float viewHeight;
	private final long start = System.nanoTime();
	private long lastFrame = System.nanoTime();
	private float frameTime;
	private float frameTimeCounter;
	private int frameCounter;
	private GameState game;
	private long worldTime;
	private int biome;
	private int biomeCategory;

	public StandardUniforms(PackDirectives directives) {
		this.directives = directives;
	}

	/** The pack's item.properties ids: (item id, damage) → pack id, -1 for unlisted. */
	private java.util.function.IntBinaryOperator itemIds = (id, damage) -> -1;
	private int heldItemId = -1;

	public void itemIds(java.util.function.IntBinaryOperator ids) {
		itemIds = ids;
	}

	/** The shadow camera of this frame (development.md 6.7). */
	public void shadow(float[] modelView, float[] projection) {
		shadowModelView.set(modelView);
		shadowProjection.set(projection);
		invert(shadowModelView, shadowModelViewInverse);
		invert(shadowProjection, shadowProjectionInverse);
	}

	/** The frame's values (render thread, after the camera is set). */
	public void frame(GameState game, CameraState camera, int width, int height) {
		this.game = game;
		long now = System.nanoTime();
		frameTime = (now - lastFrame) / 1e9f;
		lastFrame = now;
		UniformGl.lastFrameTime(frameTime);
		frameTimeCounter = (float) (((now - start) / 1e9) % 3600.0); // OptiFine wraps it at an hour
		frameCounter = (frameCounter + 1) % 720720;
		viewWidth = width;
		viewHeight = height;
		if (camera != null) {
			modelView.set(camera.modelView());
			projection.set(camera.projection());
			previousModelView.set(camera.previousModelView());
			previousProjection.set(camera.previousProjection());
			invert(modelView, modelViewInverse);
			invert(projection, projectionInverse);
			float a = projection.m[10];
			float b = projection.m[14];
			if (a != -1 && a != 1 && b != 0) {
				near = b / (a - 1);
			}
		}
		if (game == null) {
			return;
		}
		far = game.renderDistance() * 16f;
		previousCameraPosition.set(cameraPosition);
		previousCameraPositionInt.set(cameraPositionInt);
		previousCameraPositionFract.set(cameraPositionFract);
		double px = position[0], py = position[1], pz = position[2];
		game.cameraPosition(position);
		cameraPosition.set((float) position[0], (float) position[1], (float) position[2]);
		cameraPositionInt.set((int) Math.floor(position[0]), (int) Math.floor(position[1]), (int) Math.floor(position[2]));
		cameraPositionFract.set((float) (position[0] - Math.floor(position[0])), (float) (position[1] - Math.floor(position[1])),
				(float) (position[2] - Math.floor(position[2])));
		if (frameTime > 0) {
			double dx = position[0] - px, dy = position[1] - py, dz = position[2] - pz;
			velocity = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) / frameTime);
		}
		frameTimeSmooth = smooth(frameTimeSmooth, frameTime, 1, 1);
		// The view direction in the world: the inverse modelview's -Z.
		lookVector.set(-modelViewInverse.m[8], -modelViewInverse.m[9], -modelViewInverse.m[10]);
		worldTime = game.worldTime();
		float skyAngle = game.skyAngle();
		sunAngle = skyAngle < 0.75f ? skyAngle + 0.25f : skyAngle - 0.75f;
		shadowAngle = sunAngle > 0.5f ? sunAngle - 0.5f : sunAngle;
		celestial(skyAngle, 100, sunPosition);
		celestial(skyAngle, -100, moonPosition);
		shadowLightPosition.set(sunAngle <= 0.5f ? sunPosition : moonPosition);
		transformDirection(modelView, 0, 100, 0, upPosition);
		float[] sky = game.skyColor();
		skyColor.set(sky[0], sky[1], sky[2]);
		int block = game.eyeBlockLight() * 16;
		int skyLight = game.eyeSkyLight() * 16;
		eyeBrightness.set(block, skyLight);
		float h = directives.getEyeBrightnessHalfLife();
		smoothBlock = smooth(smoothBlock, block, h, h);
		smoothSky = smooth(smoothSky, skyLight, h, h);
		eyeBrightnessSmooth.set(Math.round(smoothBlock), Math.round(smoothSky));
		wetness = smooth(wetness, game.rainStrength(), directives.getWetnessHalfLife(),
				directives.getDrynessHalfLife());
		int held = game.heldItem();
		heldItemId = held < 0 ? -1 : itemIds.applyAsInt(held, game.heldItemDamage());
		String b = game.biome();
		biome = b == null ? 0 : Math.max(0, Biomes.id(Biomes.of(b)));
		biomeCategory = category(b);
	}

	/** Iris's sun and moon: the sky's own rotation (renderSky's) applied to the camera's modelview. */
	private void celestial(float skyAngle, float y, Vector3f out) {
		float[] m = modelView.m.clone();
		rotate(m, -90, 0, 1, 0);
		rotate(m, directives.getSunPathRotation(), 0, 0, 1);
		rotate(m, skyAngle * 360f, 1, 0, 0);
		transformDirectionRaw(m, 0, y, 0, out);
	}

	private float smooth(float current, float target, float halfLifeUp, float halfLifeDown) {
		if (current < 0) {
			return target; // the first value isn't smoothed (as Iris)
		}
		float halfLife = (target > current ? halfLifeUp : halfLifeDown) * 0.1f; // deciseconds
		if (halfLife <= 0) {
			return target;
		}
		float alpha = 1 - (float) Math.exp(-LN_2 / halfLife * frameTime);
		return current + (target - current) * alpha;
	}

	private static int category(String betaBiome) {
		if (betaBiome == null) {
			return BiomeCategories.NONE.ordinal();
		}
		return switch (betaBiome) {
			case "rainforest" -> BiomeCategories.JUNGLE.ordinal();
			case "swampland" -> BiomeCategories.SWAMP.ordinal();
			case "seasonal_forest", "forest" -> BiomeCategories.FOREST.ordinal();
			case "savanna", "shrubland" -> BiomeCategories.SAVANNA.ordinal();
			case "taiga" -> BiomeCategories.TAIGA.ordinal();
			case "desert" -> BiomeCategories.DESERT.ordinal();
			case "plains" -> BiomeCategories.PLAINS.ordinal();
			case "ice_desert", "tundra" -> BiomeCategories.ICY.ordinal();
			case "hell" -> BiomeCategories.NETHER.ordinal();
			case "sky" -> BiomeCategories.THE_END.ordinal();
			default -> BiomeCategories.NONE.ordinal();
		};
	}

	/** Every standard uniform into {@code h} (the custom uniforms' inputs, and what programs are given). */
	public void register(UniformHolder h) {
		h.uniformMatrix(PER_FRAME, "gbufferModelView", () -> modelView);
		h.uniformMatrix(PER_FRAME, "gbufferModelViewInverse", () -> modelViewInverse);
		h.uniformMatrix(PER_FRAME, "gbufferProjection", () -> projection);
		h.uniformMatrix(PER_FRAME, "gbufferProjectionInverse", () -> projectionInverse);
		h.uniformMatrix(PER_FRAME, "gbufferPreviousModelView", () -> previousModelView);
		h.uniformMatrix(PER_FRAME, "gbufferPreviousProjection", () -> previousProjection);
		h.uniformMatrix(PER_FRAME, "shadowModelView", () -> shadowModelView);
		h.uniformMatrix(PER_FRAME, "shadowModelViewInverse", () -> shadowModelViewInverse);
		h.uniformMatrix(PER_FRAME, "shadowProjection", () -> shadowProjection);
		h.uniformMatrix(PER_FRAME, "shadowProjectionInverse", () -> shadowProjectionInverse);
		h.uniform3f(PER_FRAME, "cameraPosition", () -> cameraPosition);
		h.uniform3f(PER_FRAME, "previousCameraPosition", () -> previousCameraPosition);
		h.uniform3f(PER_FRAME, "eyePosition", () -> cameraPosition);
		h.uniform3f(PER_FRAME, "relativeEyePosition", () -> new Vector3f());
		h.uniform3f(PER_FRAME, "sunPosition", () -> sunPosition);
		h.uniform3f(PER_FRAME, "moonPosition", () -> moonPosition);
		h.uniform3f(PER_FRAME, "shadowLightPosition", () -> shadowLightPosition);
		h.uniform3f(PER_FRAME, "upPosition", () -> upPosition);
		h.uniform3f(PER_FRAME, "skyColor", () -> skyColor);
		h.uniform1f(PER_FRAME, "sunAngle", () -> sunAngle);
		h.uniform1f(PER_FRAME, "shadowAngle", () -> shadowAngle);
		h.uniform1f(PER_FRAME, "near", () -> near);
		h.uniform1f(PER_FRAME, "far", () -> far);
		h.uniform1f(PER_FRAME, "viewWidth", () -> viewWidth);
		h.uniform1f(PER_FRAME, "viewHeight", () -> viewHeight);
		h.uniform1f(PER_FRAME, "aspectRatio", () -> viewHeight == 0 ? 1 : viewWidth / viewHeight);
		h.uniform1f(PER_FRAME, "frameTime", () -> frameTime);
		h.uniform1f(PER_FRAME, "frameTimeCounter", () -> frameTimeCounter);
		h.uniform1i(PER_FRAME, "frameCounter", () -> frameCounter);
		h.uniform1i(PER_FRAME, "worldTime", () -> (int) Math.floorMod(worldTime, 24000L));
		h.uniform1i(PER_FRAME, "worldDay", () -> (int) Math.floorDiv(worldTime, 24000L));
		h.uniform1i(PER_FRAME, "moonPhase", () -> game != null ? game.moonPhase() : 0);
		h.uniform1f(PER_FRAME, "rainStrength", () -> game != null ? game.rainStrength() : 0);
		h.uniform1f(PER_FRAME, "wetness", () -> Math.max(0, wetness));
		h.uniform1f(PER_FRAME, "thunderStrength", () -> game != null ? game.thunderStrength() : 0);
		h.uniform1i(PER_FRAME, "isEyeInWater", () -> game != null ? game.eyeFluid() : 0);
		h.uniform2i(PER_FRAME, "eyeBrightness", () -> eyeBrightness);
		h.uniform2i(PER_FRAME, "eyeBrightnessSmooth", () -> eyeBrightnessSmooth);
		h.uniform1f(PER_FRAME, "eyeAltitude", () -> cameraPosition.y);
		h.uniform1i(PER_FRAME, "biome", () -> biome);
		h.uniform1i(PER_FRAME, "biome_category", () -> biomeCategory);
		h.uniform1i(PER_FRAME, "biome_precipitation", () -> game != null ? game.precipitation() : 0);
		h.uniform1f(PER_FRAME, "rainfall", () -> game != null ? game.rainfall() : 0.5f);
		h.uniform1f(PER_FRAME, "temperature", () -> game != null ? game.temperature() : 0.5f);
		h.uniform1i(PER_FRAME, "heldItemId", () -> heldItemId);
		h.uniform1i(PER_FRAME, "heldItemId2", () -> -1); // no off hand
		h.uniform1i(PER_FRAME, "heldBlockLightValue", () -> game != null ? game.heldItemLight() : 0);
		h.uniform1i(PER_FRAME, "heldBlockLightValue2", () -> 0);
		h.uniform1f(PER_FRAME, "nightVision", () -> 0);
		h.uniform1f(PER_FRAME, "blindness", () -> 0);
		h.uniform1f(PER_FRAME, "darknessFactor", () -> 0);
		h.uniform1f(PER_FRAME, "darknessLightFactor", () -> 0);
		h.uniform1f(PER_FRAME, "playerMood", () -> 0);
		h.uniform1f(PER_FRAME, "constantMood", () -> 0); // as playerMood: no cave sounds' mood before 1.15
		h.uniform1f(PER_FRAME, "pi", () -> (float) Math.PI);
		h.uniform1i(PER_FRAME, "anisotropicFiltering", () -> 0); // Iris: the level, only with anisotropic filtering
		h.uniform1f(PER_FRAME, "screenBrightness", () -> 0.5f);
		h.uniform1i(PER_FRAME, "hideGUI", () -> game != null && game.hideGui() ? 1 : 0);
		h.uniform1f(PER_FRAME, "currentPlayerHealth", () -> game != null ? game.health() / game.maxHealth() : 1);
		h.uniform1f(PER_FRAME, "maxPlayerHealth", () -> game != null ? game.maxHealth() : 20);
		h.uniform1f(PER_FRAME, "currentPlayerAir", () -> game != null ? (float) game.air() / game.maxAir() : 1);
		h.uniform1f(PER_FRAME, "maxPlayerAir", () -> game != null ? game.maxAir() : 300);
		h.uniform1f(PER_FRAME, "currentPlayerHunger", () -> 1); // no hunger before b1.8
		h.uniform1f(PER_FRAME, "maxPlayerHunger", () -> 20);
		h.uniform1b(PER_FRAME, "is_sneaking", () -> game != null && game.sneaking());
		h.uniform1b(PER_FRAME, "is_sprinting", () -> false);
		h.uniform1b(PER_FRAME, "is_hurt", () -> game != null && game.hurt());
		h.uniform1b(PER_FRAME, "is_invisible", () -> false);
		h.uniform1b(PER_FRAME, "is_burning", () -> game != null && game.burning());
		h.uniform1b(PER_FRAME, "is_on_ground", () -> game == null || game.onGround());
		h.uniform1b(PER_FRAME, "isRightHanded", () -> true);
		h.uniform2i(PER_FRAME, "atlasSize", () -> atlasSize);
		h.uniform1i(PER_FRAME, "entityId", () -> -1); // per draw: 6.8
		h.uniform1i(PER_FRAME, "blockEntityId", () -> -1);
		h.uniform1i(PER_FRAME, "currentRenderedItemId", () -> -1);
		h.uniform4f(PER_FRAME, "entityColor", () -> new Vector4f());
		// Declared as a uniform it reads the GPU-smoothed depth instead (CenterDepth); this is only for other forms.
		h.uniform1f(PER_FRAME, "centerDepthSmooth", () -> 1);
		h.uniform1f(PER_FRAME, "alphaTestRef", () -> 0.1f);
		h.uniform3f(PER_FRAME, "chunkOffset", () -> new Vector3f());
		h.uniform1f(PER_FRAME, "cloudHeight", () -> 108.33f); // b1.7.3's overworld cloud height
		h.uniform1f(PER_FRAME, "cloudTime", () -> game != null ? worldTime + game.partialTick() : 0);
		h.uniform3i(PER_FRAME, "cameraPositionInt", () -> cameraPositionInt);
		h.uniform3f(PER_FRAME, "cameraPositionFract", () -> cameraPositionFract);
		h.uniform3i(PER_FRAME, "previousCameraPositionInt", () -> previousCameraPositionInt);
		h.uniform3f(PER_FRAME, "previousCameraPositionFract", () -> previousCameraPositionFract);
		h.uniform3f(PER_FRAME, "playerLookVector", () -> lookVector);
		h.uniform3f(PER_FRAME, "playerBodyVector", () -> lookVector);
		h.uniform1f(PER_FRAME, "ambientLight", () -> nether() ? 0.1f : 0);
		h.uniform1b(PER_FRAME, "hasCeiling", this::nether);
		h.uniform1b(PER_FRAME, "hasSkylight", () -> !nether());
		h.uniform1i(PER_FRAME, "bedrockLevel", () -> 0);
		h.uniform1i(PER_FRAME, "heightLimit", () -> 128);
		h.uniform1i(PER_FRAME, "logicalHeightLimit", () -> 128);
		h.uniform1i(PER_FRAME, "seaLevel", () -> 64);
		h.uniform4f(PER_FRAME, "lightningBoltPosition", () -> new Vector4f()); // per bolt: 6.8
		h.uniform1f(PER_FRAME, "endFlashIntensity", () -> 0);
		h.uniform1f(PER_FRAME, "previousEndFlashIntensity", () -> 0);
		h.uniform3f(PER_FRAME, "endFlashPosition", () -> new Vector3f());
		h.uniform1b(PER_FRAME, "firstPersonCamera", () -> true);
		h.uniform1i(PER_FRAME, "currentColorSpace", () -> 0);
		h.uniform3i(PER_FRAME, "currentDate", () -> {
			java.time.LocalDate d = java.time.LocalDate.now();
			return new com.coredex.altshaders.uniform.vec.Vector3i(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
		});
		h.uniform3i(PER_FRAME, "currentTime", () -> {
			java.time.LocalTime t = java.time.LocalTime.now();
			return new com.coredex.altshaders.uniform.vec.Vector3i(t.getHour(), t.getMinute(), t.getSecond());
		});
		h.uniform2i(PER_FRAME, "currentYearTime", () -> {
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			java.time.LocalDateTime start = now.toLocalDate().withDayOfYear(1).atStartOfDay();
			long since = java.time.Duration.between(start, now).getSeconds();
			long until = java.time.Duration.between(now, start.plusYears(1)).getSeconds();
			return new Vector2i((int) since, (int) until);
		});
		h.uniform1f(PER_FRAME, "currentPlayerArmor", () -> 0);
		h.uniform1f(PER_FRAME, "maxPlayerArmor", () -> 20);
		h.uniform1i(PER_FRAME, "currentSelectedBlockId", () -> 0);
		h.uniform3f(PER_FRAME, "currentSelectedBlockPos", () -> new Vector3f(-256, -256, -256));
		h.uniform3f(PER_FRAME, "heldBlockLightColor", () -> new Vector3f());
		h.uniform3f(PER_FRAME, "heldBlockLightColor2", () -> new Vector3f());
		h.uniform1b(PER_FRAME, "inSwimmingAnimation", () -> false);
		h.uniform1b(PER_FRAME, "isElytraFlying", () -> false);
		h.uniform1b(PER_FRAME, "isRiding", () -> false);
		h.uniform1b(PER_FRAME, "isSpectator", () -> false);
		h.uniform1i(PER_FRAME, "vehicleId", () -> -1);
		h.uniform1b(PER_FRAME, "vehicleInWater", () -> false);
		h.uniform3f(PER_FRAME, "vehicleLookVector", () -> new Vector3f());
		h.uniform3f(PER_FRAME, "relativeVehiclePosition", () -> new Vector3f());
		h.uniform1i(PER_FRAME, "textureFilteringMode", () -> 0);
	}

	private boolean nether() {
		return game != null && "minecraft:the_nether".equals(game.dimension());
	}

	// ---- small matrix helpers (column-major float[16]) ----------------------------------------------------------

	private static void invert(Matrix4f m, Matrix4f out) {
		com.coredex.altshaders.pipeline.Mat4.invert(m.m, out.m);
	}

	/** {@code m = m · R(angle, axis)} (degrees), as {@code glRotatef}. */
	private static void rotate(float[] m, float angle, float x, float y, float z) {
		double r = Math.toRadians(angle);
		float c = (float) Math.cos(r);
		float s = (float) Math.sin(r);
		float t = 1 - c;
		float[] rot = {
				t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0,
				t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0,
				t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0,
				0, 0, 0, 1};
		float[] o = new float[16];
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				float v = 0;
				for (int k = 0; k < 4; k++) {
					v += m[k * 4 + row] * rot[col * 4 + k];
				}
				o[col * 4 + row] = v;
			}
		}
		System.arraycopy(o, 0, m, 0, 16);
	}

	private static void transformDirection(Matrix4f m, float x, float y, float z, Vector3f out) {
		transformDirectionRaw(m.m, x, y, z, out);
	}

	private static void transformDirectionRaw(float[] m, float x, float y, float z, Vector3f out) {
		out.set(m[0] * x + m[4] * y + m[8] * z, m[1] * x + m[5] * y + m[9] * z, m[2] * x + m[6] * y + m[10] * z);
	}
}
