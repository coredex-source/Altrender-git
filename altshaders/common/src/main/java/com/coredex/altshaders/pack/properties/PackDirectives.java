/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/properties/PackDirectives.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.properties;

import com.coredex.altshaders.pack.PackEnv;
import java.util.HashMap;
import java.util.Map;
import com.coredex.altshaders.pack.helpers.MapBuilder;
import com.coredex.altshaders.pack.gl.TextureScaleOverride;
import com.coredex.altshaders.pack.gl.TextureType;
import com.coredex.altshaders.pack.helpers.Tri;
import com.coredex.altshaders.pack.parsing.DirectiveHolder;
import com.coredex.altshaders.pack.texture.TextureStage;
import com.coredex.altshaders.pack.helpers.Vector2i;

import java.util.Set;

public class PackDirectives {
	private final PackRenderTargetDirectives renderTargetDirectives;
	private final PackShadowDirectives shadowDirectives;
	private final float drynessHalfLife;
	private int fallbackTex;
	private boolean supportsColorCorrection;
	private int noiseTextureResolution;
	private float sunPathRotation;
	private float ambientOcclusionLevel;
	private float wetnessHalfLife;
	private float eyeBrightnessHalfLife;
	private float centerDepthHalfLife;
	private CloudSetting cloudSetting;
	private CloudSetting dhCloudSetting;
	private boolean underwaterOverlay;
	private boolean vignette;
	private boolean sun;
	private boolean weather;
	private boolean weatherParticles;
	private boolean moon;
	private boolean stars;
	private boolean sky;
	private boolean rainDepth;
	private boolean separateAo;
	private boolean breaksAnisotropy;
	private boolean voxelizeLightBlocks;
	private boolean separateEntityDraws;
	private boolean skipAllRendering;
	private boolean frustumCulling;
	private boolean supportsEndFlash;
	private boolean occlusionCulling;
	private boolean oldLighting;
	private boolean concurrentCompute;
	private boolean oldHandLight;
	private boolean prepareBeforeShadow;
	private boolean vanillaTerrain;
	private Map<String, Map<String, Boolean>> explicitFlips = new HashMap<>();
	private Map<String, TextureScaleOverride> scaleOverrides = new HashMap<>();
	private Map<Tri<String, TextureType, TextureStage>, String> textureMap;
	private ParticleRenderingSettings particleRenderingSettings;

	private PackDirectives(Set<Integer> supportedRenderTargets, PackShadowDirectives packShadowDirectives) {
		noiseTextureResolution = 256;
		sunPathRotation = 0.0F;
		supportsColorCorrection = false;
		ambientOcclusionLevel = 1.0F;
		wetnessHalfLife = 600.0f;
		drynessHalfLife = 200.0f;
		eyeBrightnessHalfLife = 10.0f;
		centerDepthHalfLife = 1.0F;
		renderTargetDirectives = new PackRenderTargetDirectives(supportedRenderTargets);
		shadowDirectives = packShadowDirectives;
	}

	public PackDirectives(Set<Integer> supportedRenderTargets, ShaderProperties properties) {
		this(supportedRenderTargets, new PackShadowDirectives(properties));
		cloudSetting = properties.getCloudSetting();
		dhCloudSetting = properties.getDHCloudSetting();
		underwaterOverlay = properties.getUnderwaterOverlay().orElse(false);
		vignette = properties.getVignette().orElse(false);
		sun = properties.getSun().orElse(true);
		weather = properties.getWeather().orElse(true);
		weatherParticles = properties.getWeatherParticles().orElse(true);
		moon = properties.getMoon().orElse(true);
		stars = properties.getStars().orElse(true);
		sky = properties.getSky().orElse(true);
		rainDepth = properties.getRainDepth().orElse(false);
		separateAo = properties.getSeparateAo().orElse(false);
		breaksAnisotropy = properties.breaksAnisotropy().orElse(false);
		voxelizeLightBlocks = properties.getVoxelizeLightBlocks().orElse(false);
		separateEntityDraws = properties.getSeparateEntityDraws().orElse(false);
		skipAllRendering = properties.skipAllRendering().orElse(false);
		frustumCulling = properties.getFrustumCulling().orElse(true);
		supportsEndFlash = properties.supportsEndFlash().orElse(false);
		occlusionCulling = properties.getOcclusionCulling().orElse(true);
		oldLighting = properties.getOldLighting().orElse(false);
		fallbackTex = properties.getFallbackTex();
		supportsColorCorrection = properties.supportsColorCorrection().orElse(false);
		concurrentCompute = properties.getConcurrentCompute().orElse(false);
		oldHandLight = properties.getOldHandLight().orElse(true);
		explicitFlips = properties.getExplicitFlips();
		scaleOverrides = properties.getTextureScaleOverrides();
		prepareBeforeShadow = properties.getPrepareBeforeShadow().orElse(false);
		vanillaTerrain = properties.getVanillaTerrain().orElse(false);
		particleRenderingSettings = properties.getParticleRenderingSettings();
		textureMap = properties.getCustomTexturePatching();
	}

	PackDirectives(Set<Integer> supportedRenderTargets, PackDirectives directives) {
		this(supportedRenderTargets, new PackShadowDirectives(directives.getShadowDirectives()));
		cloudSetting = directives.cloudSetting;
		dhCloudSetting = directives.dhCloudSetting;
		separateAo = directives.separateAo;
		voxelizeLightBlocks = directives.voxelizeLightBlocks;
		separateEntityDraws = directives.separateEntityDraws;
		vanillaTerrain = directives.vanillaTerrain;
		frustumCulling = directives.frustumCulling;
		supportsEndFlash = directives.supportsEndFlash;
		oldLighting = directives.oldLighting;
		concurrentCompute = directives.concurrentCompute;
		explicitFlips = directives.explicitFlips;
		scaleOverrides = directives.scaleOverrides;
		prepareBeforeShadow = directives.prepareBeforeShadow;
		fallbackTex = directives.fallbackTex;
		particleRenderingSettings = directives.particleRenderingSettings;
		textureMap = directives.textureMap;
	}

	private static float clamp(float val, float lo, float hi) {
		return Math.max(lo, Math.min(hi, val));
	}

	public int getNoiseTextureResolution() {
		return noiseTextureResolution;
	}

	public float getSunPathRotation() {
		return sunPathRotation;
	}

	public float getAmbientOcclusionLevel() {
		return ambientOcclusionLevel;
	}

	public float getWetnessHalfLife() {
		return wetnessHalfLife;
	}

	public float getDrynessHalfLife() {
		return drynessHalfLife;
	}

	public float getEyeBrightnessHalfLife() {
		return eyeBrightnessHalfLife;
	}

	public float getCenterDepthHalfLife() {
		return centerDepthHalfLife;
	}

	public CloudSetting getCloudSetting() {
		return cloudSetting;
	}

	public CloudSetting getDHCloudSetting() {
		return dhCloudSetting;
	}

	public boolean underwaterOverlay() {
		return underwaterOverlay;
	}

	public boolean vignette() {
		return vignette;
	}

	public boolean shouldRenderSun() {
		return sun;
	}

	public boolean shouldRenderWeather() {
		return weather;
	}

	public boolean shouldRenderWeatherParticles() {
		return weatherParticles;
	}

	public boolean shouldRenderMoon() {
		return moon;
	}

	public boolean shouldRenderStars() {
		return stars;
	}

	public boolean shouldRenderSkyDisc() {
		return sky;
	}

	public ParticleRenderingSettings getParticleRenderingSettings() {
		return particleRenderingSettings;
	}

	public boolean rainDepth() {
		return rainDepth;
	}

	public boolean shouldUseSeparateAo() {
		return separateAo;
	}

	public boolean breaksAnisotropy() {
		return breaksAnisotropy;
	}

	public boolean shouldVoxelizeLightBlocks() {
		return voxelizeLightBlocks;
	}

	public boolean shouldUseSeparateEntityDraws() {
		return separateEntityDraws;
	}

	public boolean shouldUseFrustumCulling() {
		return frustumCulling;
	}

	public boolean shouldUseOcclusionCulling() {
		return occlusionCulling;
	}

	public boolean isOldLighting() {
		return oldLighting;
	}

	public boolean isOldHandLight() {
		return oldHandLight;
	}

	public boolean getConcurrentCompute() {
		return concurrentCompute;
	}

	public boolean isPrepareBeforeShadow() {
		return prepareBeforeShadow;
	}

	public boolean skipAllRendering() {
		return skipAllRendering;
	}

	public Map<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
		return textureMap;
	}

	public PackRenderTargetDirectives getRenderTargetDirectives() {
		return renderTargetDirectives;
	}

	public PackShadowDirectives getShadowDirectives() {
		return shadowDirectives;
	}

	public boolean supportsColorCorrection() {
		return supportsColorCorrection;
	}

	public int getFallbackTex() {
		return fallbackTex;
	}

	public void acceptDirectivesFrom(DirectiveHolder directives) {
		renderTargetDirectives.acceptDirectives(directives);
		shadowDirectives.acceptDirectives(directives);

		directives.acceptConstIntDirective("noiseTextureResolution",
			noiseTextureResolution -> this.noiseTextureResolution = noiseTextureResolution);

		directives.acceptConstFloatDirective("sunPathRotation",
			sunPathRotation -> this.sunPathRotation = sunPathRotation);

		directives.acceptConstFloatDirective("ambientOcclusionLevel",
			ambientOcclusionLevel -> this.ambientOcclusionLevel = clamp(ambientOcclusionLevel, 0.0f, 1.0f));

		directives.acceptConstFloatDirective("wetnessHalflife",
			wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);

		directives.acceptConstFloatDirective("drynessHalflife",
			wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);

		directives.acceptConstFloatDirective("eyeBrightnessHalflife",
			eyeBrightnessHalfLife -> this.eyeBrightnessHalfLife = eyeBrightnessHalfLife);

		directives.acceptConstFloatDirective("centerDepthHalflife",
			centerDepthHalfLife -> this.centerDepthHalfLife = centerDepthHalfLife);
	}

	public Map<Integer, Boolean> getExplicitFlips(String pass) {
		MapBuilder<Integer, Boolean> explicitFlips = new MapBuilder<>();

		Map<String, Boolean> explicitFlipsStr = this.explicitFlips.get(pass);

		if (explicitFlipsStr == null) {
			explicitFlipsStr = Map.of();
		}

		explicitFlipsStr.forEach((buffer, shouldFlip) -> {
			int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);

			if (index == -1 && buffer.startsWith("colortex")) {
				String id = buffer.substring("colortex".length());

				try {
					index = Integer.parseInt(id);
				} catch (NumberFormatException e) {
					// fall through to index == null check for unknown buffer.
				}
			}

			if (index != -1) {
				explicitFlips.put(index, shouldFlip);
			} else {
				PackEnv.LOG.warn("Unknown buffer with ID " + buffer + " specified in flip directive for pass "
					+ pass);
			}
		});

		return explicitFlips.build();
	}

	public Vector2i getTextureScaleOverride(int index, int dimensionX, int dimensionY) {
		final String name = "colortex" + index;

		// TODO: How do custom textures interact with aliases?

		Vector2i scale = new Vector2i();

		if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
			String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);

			if (scaleOverrides.containsKey(legacyName)) {
				scale.set(scaleOverrides.get(legacyName).getX(dimensionX), scaleOverrides.get(legacyName).getY(dimensionY));
			} else if (scaleOverrides.containsKey(name)) {
				scale.set(scaleOverrides.get(name).getX(dimensionX), scaleOverrides.get(name).getY(dimensionY));
			} else {
				scale.set(dimensionX, dimensionY);
			}
		} else if (scaleOverrides.containsKey(name)) {
			scale.set(scaleOverrides.get(name).getX(dimensionX), scaleOverrides.get(name).getY(dimensionY));
		} else {
			scale.set(dimensionX, dimensionY);
		}

		return scale;
	}

	public boolean supportsEndFlash() {
		return supportsEndFlash;
	}

	/** AltShaders (Review): {@code altshaders.vanillaTerrain}, Altrender's own terrain kept in the pack's buffers. */
	public boolean isVanillaTerrain() {
		return vanillaTerrain;
	}
}
