/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.WorldPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;

/**
 * The hand as an entity (development.md 6.8): under a pipeline that takes the light, the held item's colour keeps its
 * tint without the game's brightness, and the pipeline gets the player's light (as {@link EntityLightMixin}). The
 * screen effects after it (in a wall, under water, on fire) carry their own brightness: full light, as Iris gives
 * draws without light coordinates.
 */
@Mixin(ItemInHandRenderer.class)
abstract class HandLightMixin {

	@Shadow
	private Minecraft minecraft;

	@Redirect(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V",
			ordinal = 0, remap = false))
	private void altrender$itemLight(float r, float g, float b, float a) {
		color(r, g, b, a);
	}

	@Redirect(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V",
			ordinal = 1, remap = false))
	private void altrender$armLight(float r, float g, float b, float a) {
		color(r, g, b, a);
	}

	@Inject(method = "renderScreenEffects", at = @At("HEAD"))
	private void altrender$effectsLight(float tickDelta, CallbackInfo ci) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null) {
			p.entity(null, 15, 15);
		}
	}

	private void color(float r, float g, float b, float a) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		PlayerEntity player = minecraft.player;
		if (p == null || player == null || player.world == null) {
			GL11.glColor4f(r, g, b, a);
			return;
		}
		int x = MathHelper.floor(player.x);
		int y = MathHelper.floor(player.y);
		int z = MathHelper.floor(player.z);
		float brightness = player.world.getBrightness(x, y, z);
		if (p.entity("minecraft:player", player.world.getLight(LightType.BLOCK, x, y, z),
				player.world.getLight(LightType.SKY, x, y, z)) && brightness > 0) {
			GL11.glColor4f(r / brightness, g / brightness, b / brightness, a); // the tint alone
		} else {
			GL11.glColor4f(r, g, b, a);
		}
	}
}
