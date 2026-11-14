/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import com.coredex.altrender.api.render.VanillaPart;
import com.coredex.altrender.mc.mixin.mesh.TesselatorAccessor;
import com.coredex.altrender.render.Renderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The sun, moon and stars a world pipeline hides (development.md 6.8: a pack's {@code sun=false}, {@code moon=false},
 * {@code stars=false}). In {@code renderSky} the tessellator draws the sunrise fan, the sun, then the moon; the stars
 * are the second display list it calls. A hidden quad is dropped from the tessellator instead of drawn.
 */
@Mixin(WorldRenderer.class)
abstract class SkyPartsMixin {

	@Redirect(method = "renderSky", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
	private void altrender$sun(Tesselator t) {
		end(t, VanillaPart.SUN);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 2))
	private void altrender$moon(Tesselator t) {
		end(t, VanillaPart.MOON);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V",
			ordinal = 1))
	private void altrender$stars(int list) {
		if (!Renderer.hides(VanillaPart.STARS)) {
			org.lwjgl.opengl.GL11.glCallList(list);
		}
	}

	private static void end(Tesselator t, VanillaPart part) {
		if (!Renderer.hides(part)) {
			t.end();
			return;
		}
		TesselatorAccessor a = (TesselatorAccessor) t;
		a.altrender$setTesselating(false);
		a.altrender$setVertexCount(0);
		a.altrender$setIndex(0);
		a.altrender$setNextVertexCount(0);
	}
}
