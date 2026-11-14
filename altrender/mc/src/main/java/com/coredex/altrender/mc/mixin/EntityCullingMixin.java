/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.terrain.TerrainRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Entities and block entities in sections the terrain's frustum and visibility walk ruled out aren't drawn
 * (development.md 3.4 block-entity culling, 3.5 occlusion culling). Entities still go through the game's own frustum
 * test first; block entities had none.
 */
@Mixin(WorldRenderer.class)
abstract class EntityCullingMixin {


	@Redirect(method = "renderEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/Culler;isVisible(Lnet/minecraft/util/math/Box;)Z"))
	private boolean altrender$entityVisible(Culler culler, Box box) {
		if (!culler.isVisible(box)) {
			return false;
		}
		TerrainRenderer terrain = Renderer.terrain();
		return Renderer.otherView() || !com.coredex.altrender.render.Renderer.config().entityCulling() || terrain == null || terrain.boxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	@Shadow
	private net.minecraft.client.Minecraft minecraft;

	/** Another view that asks for the player (a pack's shadowPlayer) sees it as in third person. */
	@Redirect(method = "renderEntities", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/options/GameOptions;perspective:Z"))
	private boolean altrender$playerInView(net.minecraft.client.options.GameOptions options) {
		return options.perspective || Renderer.otherView() && Renderer.otherViewPlayer();
	}

	/** Another view that wants the player alone (shadowPlayer without shadowEntities) skips every other entity. */
	@Redirect(method = "renderEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;shouldRender(Lnet/minecraft/util/math/Vec3d;)Z"))
	private boolean altrender$entityInView(net.minecraft.entity.Entity entity, net.minecraft.util.math.Vec3d cameraPos) {
		if (Renderer.otherView() && !Renderer.otherViewEntities() && entity != minecraft.camera) {
			return false;
		}
		return entity.shouldRender(cameraPos);
	}

	@Redirect(method = "renderEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;F)V"))
	private void altrender$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity be, float tickDelta) {
		if (Renderer.otherView() && !Renderer.otherViewEntities()) {
			return;
		}
		TerrainRenderer terrain = Renderer.terrain();
		if (Renderer.otherView() || !com.coredex.altrender.render.Renderer.config().entityCulling() || terrain == null || terrain.boxVisible(be.x, be.y, be.z, be.x + 1, be.y + 1, be.z + 1)) {
			dispatcher.render(be, tickDelta);
		}
	}
}
