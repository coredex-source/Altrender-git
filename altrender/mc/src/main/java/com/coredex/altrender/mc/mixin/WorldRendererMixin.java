/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.mc.adapter.B173WorldView;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.terrain.TerrainRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain hooks (FrameHooks, docs/mc-render-map.md §4) and Altrender's terrain in place of the game's (development.md
 * 2.4): world changes and dirty ranges are forwarded, the game's section compiling is replaced by Altrender's
 * per-frame update, and each layer draw by Altrender's. The game's section display lists are never compiled.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {

	@Shadow
	private Minecraft minecraft;

	/** The sky's static lists are compiled here (stars, light and dark sky): captured as buffers (3.3). */
	@Inject(method = "<init>", at = @At("HEAD"))
	private static void altrender$captureSkyStart(Minecraft minecraft, net.minecraft.client.render.texture.TextureManager textures,
			CallbackInfo ci) {
		com.coredex.altrender.immediate.ListCapture.captureCompiles(true);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private static void altrender$captureSkyEnd(Minecraft minecraft, net.minecraft.client.render.texture.TextureManager textures,
			CallbackInfo ci) {
		com.coredex.altrender.immediate.ListCapture.captureCompiles(false);
	}

	@Inject(method = "setWorld", at = @At("TAIL"))
	private void altrender$setWorld(World world, CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.setWorld(world != null ? new B173WorldView(world, (WorldRenderer) (Object) this) : null);
		}
	}

	@Inject(method = "markDirty", at = @At("HEAD"))
	private void altrender$markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.markDirty(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}

	@Inject(method = "notifyBlockChanged", at = @At("HEAD"))
	private void altrender$blockChanged(int x, int y, int z, CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.blockChanged(x, y, z);
		}
	}

	/** Graphics, smooth lighting, advanced OpenGL and view-distance changes: the game rebuilds everything, so do we. */
	@Inject(method = "reload", at = @At("HEAD"))
	private void altrender$reload(CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.markAllDirty();
		}
	}

	@Inject(method = "notifyAmbientDarknessChanged", at = @At("HEAD"))
	private void altrender$skyChanged(CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.skyChanged();
		}
	}

	/** The game frustum-tests its own sections every frame; with Altrender's terrain nobody uses the result. */
	@Inject(method = "cullChunks", at = @At("HEAD"), cancellable = true)
	private void altrender$skipCull(net.minecraft.client.render.Culler culler, float tickDelta, CallbackInfo ci) {
		if (Renderer.terrain() != null) {
			ci.cancel();
		}
	}

	/** The game's per-frame compile loop: Altrender's update instead (schedule, collect, upload). */
	@Inject(method = "compileChunks", at = @At("HEAD"), cancellable = true)
	private void altrender$update(MobEntity camera, boolean force, CallbackInfoReturnable<Boolean> cir) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			// The game's grid radius for its step, or Altrender's render distance (development.md 5.2).
			terrain.update(camera.x, camera.y, camera.z,
					com.coredex.altrender.mc.ui.RenderDistance.chunks(this.minecraft.options));
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "render(Lnet/minecraft/entity/mob/MobEntity;ID)I", at = @At("HEAD"), cancellable = true)
	private void altrender$terrainLayer(MobEntity camera, int layer, double tickDelta, CallbackInfoReturnable<Integer> cir) {
		if (layer == 1) {
			// Before the translucent terrain (and a pipeline's deferred passes): the solid hand, if the pipeline asks.
			com.coredex.altrender.mc.render.EarlyHand.draw(this.minecraft, (float) tickDelta);
		}
		Renderer.hooks().terrainLayer(layer);
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			Lighting.turnOff(); // as the game's render does
			double x = camera.prevX + (camera.x - camera.prevX) * tickDelta;
			double y = camera.prevY + (camera.y - camera.prevY) * tickDelta;
			double z = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
			int drawn = terrain.draw(layer, x, y, z);
			Renderer.hooks().terrainLayerEnd(layer);
			cir.setReturnValue(drawn);
		}
	}

	/** Fancy graphics' translucent colour pass (after a depth-only {@code render}): Altrender's layer again. */
	@Inject(method = "renderLastChunks", at = @At("HEAD"), cancellable = true)
	private void altrender$renderLast(int layer, double tickDelta, CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			// The same phase as the depth-only draw: a pipeline draws it with that layer's program and buffers.
			Renderer.hooks().terrainLayer(layer);
			terrain.drawAgain(layer);
			Renderer.hooks().terrainLayerEnd(layer);
			ci.cancel();
		}
	}

	@Inject(method = "render(Lnet/minecraft/entity/mob/MobEntity;ID)I", at = @At("RETURN"))
	private void altrender$terrainLayerEnd(MobEntity camera, int layer, double tickDelta, CallbackInfoReturnable<Integer> cir) {
		Renderer.hooks().terrainLayerEnd(layer);
	}
}
