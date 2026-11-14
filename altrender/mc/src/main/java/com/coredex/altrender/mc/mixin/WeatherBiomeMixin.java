/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.mc.adapter.BiomeColumnCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rain and snow ask the biome source for the 21 × 21 columns around the camera twice per frame, three noise layers per
 * column (development.md 3.1's top CPU cost in rain); a column's biome never changes, so they come from the per-chunk
 * cache instead: the same biomes in the same order.
 */
@Mixin(GameRenderer.class)
abstract class WeatherBiomeMixin {

	@Shadow
	private Minecraft minecraft;

	@Unique
	private Biome[] altrender$weatherBiomes;

	@Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/biome/source/BiomeSource;getBiomes(IIII)[Lnet/minecraft/world/biome/Biome;"))
	private Biome[] altrender$cachedBiomes(BiomeSource source, int x, int z, int sizeX, int sizeZ) {
		altrender$weatherBiomes = BiomeColumnCache.region(this.minecraft.world, x, z, sizeX, sizeZ, altrender$weatherBiomes);
		return altrender$weatherBiomes;
	}
}
