/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.terrain.TerrainRenderer;
import net.minecraft.client.render.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A texture-pack switch reloads the textures, grass and foliage colour maps included: the terrain rebuilds with the new
 * colours (development.md 2.7; the game itself keeps its old section lists until something rebuilds them). The atlas
 * needs nothing: the terrain samples whatever texture the game has bound when it draws.
 *
 * <p>The game copies every texture through one 1 MB buffer, so a texture over 512×512 (an HD texture pack's) crashed it
 * (b1.7.3 needed MCPatcher for those): the buffer grows to fit.
 */
@Mixin(TextureManager.class)
abstract class TextureManagerMixin {

	@Shadow
	private ByteBuffer imageBuffer;

	@Inject(method = "load(Ljava/awt/image/BufferedImage;I)V", at = @At("HEAD"))
	private void altrender$roomForImage(BufferedImage image, int id, CallbackInfo ci) {
		altrender$room((long) image.getWidth() * image.getHeight() * 4);
	}

	@Inject(method = "bind([IIII)V", at = @At("HEAD"))
	private void altrender$roomForPixels(int[] pixels, int a, int b, int c, CallbackInfo ci) {
		altrender$room((long) pixels.length * 4);
	}

	@org.spongepowered.asm.mixin.Unique
	private void altrender$room(long bytes) {
		if (imageBuffer.capacity() < bytes && bytes <= Integer.MAX_VALUE) {
			imageBuffer = ByteBuffer.allocateDirect((int) bytes).order(ByteOrder.nativeOrder());
		}
	}

	@Inject(method = "reload", at = @At("TAIL"))
	private void altrender$reloaded(CallbackInfo ci) {
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.markAllDirty();
		}
	}
}
