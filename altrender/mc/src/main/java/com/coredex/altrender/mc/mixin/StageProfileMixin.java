/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's own frame stages as debug groups and (with {@code -Daltrender.debug.gpuTimers}) GPU timers: sky,
 * entities, particles, weather, clouds, hand, HUD (development.md 3.1's profiling pass). A pipeline that wants the
 * particles after the translucent terrain ({@code particlesLate}) gets them there, as the game draws them otherwise.
 */
@Mixin(GameRenderer.class)
abstract class StageProfileMixin {

	@Shadow
	private Minecraft minecraft;

	@Shadow
	private void setupFog(int mode, float tickDelta) {
	}

	/** The particles were held back this frame: drawn after the translucent terrain. */
	@Unique
	private boolean altrender$particlesWaiting;

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(F)V"))
	private void altrender$skyStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_SKY, true);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(F)V", shift = At.Shift.AFTER))
	private void altrender$skyEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_SKY, false);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Culler;F)V"))
	private void altrender$entitiesStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_ENTITIES, true);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Culler;F)V", shift = At.Shift.AFTER))
	private void altrender$entitiesEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_ENTITIES, false);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"))
	private void altrender$particlesLitStart(CallbackInfo ci) {
		if (!Renderer.particlesLate()) {
			Renderer.stage(Renderer.P_PARTICLES, true);
			altrender$particleNormal();
		}
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER))
	private void altrender$particlesLitEnd(CallbackInfo ci) {
		if (!Renderer.particlesLate()) {
			Renderer.stage(Renderer.P_PARTICLES, false);
		}
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"))
	private void altrender$particlesStart(CallbackInfo ci) {
		if (!Renderer.particlesLate()) {
			Renderer.stage(Renderer.P_PARTICLES, true);
			altrender$particleNormal();
		}
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER))
	private void altrender$particlesEnd(CallbackInfo ci) {
		if (!Renderer.particlesLate()) {
			Renderer.stage(Renderer.P_PARTICLES, false);
		}
	}

	/**
	 * The game's particles have no normals (they take GL's current one, left over from whatever drew last): under a
	 * world pipeline, whose programs light them from {@code gl_Normal}, a defined one, facing +z (Review).
	 */
	@Unique
	private static void altrender$particleNormal() {
		if (com.coredex.altrender.api.AltrenderApi.worldPipeline() != null) {
			org.lwjgl.opengl.GL11.glNormal3f(0, 0, 1);
		}
	}

	@Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"))
	private void altrender$particlesLit(ParticleManager particles, Entity camera, float tickDelta) {
		if (Renderer.particlesLate()) {
			altrender$particlesWaiting = true;
			return;
		}
		particles.renderLit(camera, tickDelta);
	}

	@Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"))
	private void altrender$particles(ParticleManager particles, Entity camera, float tickDelta) {
		if (Renderer.particlesLate()) {
			altrender$particlesWaiting = true;
			return;
		}
		particles.render(camera, tickDelta);
	}

	/**
	 * The held-back particles, after the translucent terrain and before the weather (declared before the weather's
	 * stage start, so it runs first): lit ones with the game's lighting, then the rest with its fog, as it draws them.
	 */
	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
	private void altrender$lateParticles(float tickDelta, long time, CallbackInfo ci) {
		if (!altrender$particlesWaiting) {
			return;
		}
		altrender$particlesWaiting = false;
		Lighting.turnOn();
		Renderer.stage(Renderer.P_PARTICLES, true);
		altrender$particleNormal();
		minecraft.particleManager.renderLit(minecraft.camera, tickDelta);
		Renderer.stage(Renderer.P_PARTICLES, false);
		Lighting.turnOff();
		setupFog(0, tickDelta);
		Renderer.stage(Renderer.P_PARTICLES, true);
		altrender$particleNormal();
		minecraft.particleManager.render(minecraft.camera, tickDelta);
		Renderer.stage(Renderer.P_PARTICLES, false);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
	private void altrender$weatherStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_WEATHER, true);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V", shift = At.Shift.AFTER))
	private void altrender$weatherEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_WEATHER, false);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderClouds(F)V"))
	private void altrender$cloudsStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_CLOUDS, true);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderClouds(F)V", shift = At.Shift.AFTER))
	private void altrender$cloudsEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_CLOUDS, false);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderItemInHand(FI)V"))
	private void altrender$handStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_HAND, true);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderItemInHand(FI)V", shift = At.Shift.AFTER))
	private void altrender$handEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_HAND, false);
	}

	@Inject(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;render(FZII)V"))
	private void altrender$hudStart(CallbackInfo ci) {
		Renderer.stage(Renderer.P_HUD, true);
	}

	@Inject(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;render(FZII)V", shift = At.Shift.AFTER))
	private void altrender$hudEnd(CallbackInfo ci) {
		Renderer.stage(Renderer.P_HUD, false);
	}

	// Phases without a stage timer (development.md 6.1): the world pipeline binds its programs for them.

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderMiningProgress(Lnet/minecraft/entity/mob/player/PlayerEntity;Lnet/minecraft/world/HitResult;ILnet/minecraft/item/ItemStack;F)V"))
	private void altrender$destroyStart(CallbackInfo ci) {
		Renderer.phase(com.coredex.altrender.api.render.RenderPhase.DESTROY);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderBlockOutline(Lnet/minecraft/entity/mob/player/PlayerEntity;Lnet/minecraft/world/HitResult;ILnet/minecraft/item/ItemStack;F)V"))
	private void altrender$outlineStart(CallbackInfo ci) {
		Renderer.phase(com.coredex.altrender.api.render.RenderPhase.OUTLINE);
	}

	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderBlockOutline(Lnet/minecraft/entity/mob/player/PlayerEntity;Lnet/minecraft/world/HitResult;ILnet/minecraft/item/ItemStack;F)V", shift = At.Shift.AFTER))
	private void altrender$outlineEnd(CallbackInfo ci) {
		Renderer.phase(com.coredex.altrender.api.render.RenderPhase.NONE);
	}

	/** The hand drawn early for a world pipeline (EarlyHand): not again at the end; the screen overlays stay. */
	@org.spongepowered.asm.mixin.injection.Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderHand(F)V"))
	private void altrender$lateHand(net.minecraft.client.render.ItemInHandRenderer hand, float tickDelta) {
		if (!Renderer.skipLateHand()) {
			hand.renderHand(tickDelta);
		}
	}

	/** A world pipeline that keeps the world's depth for the hand (Iris's hand depth) skips the depth clear before it. */
	@org.spongepowered.asm.mixin.injection.Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/opengl/GL11;glClear(I)V"))
	private void altrender$handDepthClear(int mask) {
		if (mask == 0x100 && Renderer.skipHandDepthClear()) { // GL_DEPTH_BUFFER_BIT alone: the one before the hand
			return;
		}
		org.lwjgl.opengl.GL11.glClear(mask);
	}

	@org.spongepowered.asm.mixin.injection.Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/world/WorldRenderer;renderClouds(F)V"))
	private void altrender$clouds(net.minecraft.client.render.world.WorldRenderer renderer, float tickDelta) {
		if (!Renderer.hides(com.coredex.altrender.api.render.VanillaPart.CLOUDS)) {
			renderer.renderClouds(tickDelta);
		}
	}

	@org.spongepowered.asm.mixin.injection.Redirect(method = "renderWorld", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
	private void altrender$weather(GameRenderer renderer, float tickDelta) {
		if (!Renderer.hides(com.coredex.altrender.api.render.VanillaPart.WEATHER)) {
			((GameRendererWeatherAccess) renderer).altrender$renderSnowAndRain(tickDelta);
		}
	}
}
