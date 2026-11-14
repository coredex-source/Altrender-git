/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import com.coredex.altrender.terrain.MeshThread;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game's {@code Vec3d} and {@code Box} pools are static lists the main thread resets every frame; liquid meshing
 * takes vectors from them (development.md 2.2). On a meshing worker {@code fromPool} returns a new object instead
 * (the same values; the game's {@code of} factories).
 */
final class MathPoolMixins {

	private MathPoolMixins() {
	}

	@Mixin(Vec3d.class)
	abstract static class Vec3dPool {

		@Inject(method = "fromPool", at = @At("HEAD"), cancellable = true)
		private static void altrender$workerVec(double x, double y, double z, CallbackInfoReturnable<Vec3d> cir) {
			if (Thread.currentThread() instanceof MeshThread) {
				cir.setReturnValue(Vec3d.of(x, y, z));
			}
		}
	}

	@Mixin(Box.class)
	abstract static class BoxPool {

		@Inject(method = "fromPool", at = @At("HEAD"), cancellable = true)
		private static void altrender$workerBox(double minX, double minY, double minZ, double maxX, double maxY,
				double maxZ, CallbackInfoReturnable<Box> cir) {
			if (Thread.currentThread() instanceof MeshThread) {
				cir.setReturnValue(Box.of(minX, minY, minZ, maxX, maxY, maxZ));
			}
		}
	}
}
