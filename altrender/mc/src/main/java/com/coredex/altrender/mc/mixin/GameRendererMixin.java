/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.mc.debug.MeshSelfTest;
import com.coredex.altrender.mc.debug.MeshStressTest;
import com.coredex.altrender.mc.debug.SnapshotSelfTest;
import com.coredex.altrender.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame and world hooks (FrameHooks, docs/mc-render-map.md §2). */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

	@Shadow
	private Minecraft minecraft;
	@Shadow
	private float renderDistance;

	@Inject(method = "render(F)V", at = @At("HEAD"))
	private void altrender$beginFrame(float tickDelta, CallbackInfo ci) {
		MeshStressTest.onFrame(this.minecraft);
		Renderer.hooks().beginFrame(tickDelta);
	}

	@Inject(method = "render(F)V", at = @At("RETURN"))
	private void altrender$endFrame(float tickDelta, CallbackInfo ci) {
		Renderer.hooks().endFrame();
	}

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void altrender$beginWorld(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
		SnapshotSelfTest.onWorldFrame(this.minecraft);
		MeshSelfTest.onWorldFrame(this.minecraft);
		Renderer.hooks().beginWorld(tickDelta);
	}

	/** After setupCamera and the game's frustum read-back, once per pass (two with anaglyph 3D). */
	@Inject(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;",
			shift = At.Shift.AFTER))
	private void altrender$cameraReady(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
		Renderer.hooks().cameraReady();
	}

	/** Both exits: after the first pass without anaglyph 3D, after the second with it. */
	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void altrender$endWorld(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
		Renderer.hooks().endWorld();
	}

	/** Fog end and far plane from Altrender's render distance while its terrain draws (development.md 5.2). */
	@org.spongepowered.asm.mixin.injection.Redirect(method = "setupCamera", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/render/GameRenderer;renderDistance:F",
			opcode = org.objectweb.asm.Opcodes.PUTFIELD))
	private void altrender$renderDistance(net.minecraft.client.render.GameRenderer self, float value) {
		this.renderDistance = Renderer.terrain() != null
				? com.coredex.altrender.mc.ui.RenderDistance.fogBlocks(this.minecraft.options) : value;
	}
}
