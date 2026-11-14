/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import java.util.Random;

import com.coredex.altrender.debug.harness.Harness;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scene harness hook (development.md 0.7): a new world searches for its spawn point with an unseeded random, and
 * pre-generates the area around it, which decides the order neighbouring chunks are decorated in (trees on chunk
 * borders differ with it). Seeded from the world seed, generation is the same every run.
 */
@Mixin(World.class)
abstract class WorldSeedMixin {

	@Shadow
	public Random random;

	@Shadow
	public abstract long getSeed();

	@Inject(method = "<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;JLnet/minecraft/world/dimension/Dimension;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;findSpawnPoint()V"))
	private void altrender$seedSpawnSearch(CallbackInfo ci) {
		if (Harness.active()) {
			this.random.setSeed(getSeed());
		}
	}
}
