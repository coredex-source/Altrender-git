/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.render;

import org.lwjgl.opengl.GL11;

import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.mc.mixin.GameRendererHandInvoker;
import com.coredex.altrender.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.Lighting;

/**
 * The solid hand drawn before the translucent terrain when the world pipeline asks (Iris: before the deferred passes,
 * which light it; development.md 6.8): what the game's {@code renderItemInHand} does for the hand (identity
 * modelview, hurt shake, bobbing, the held item or arm), with the world's projection as the game's own hand has. The
 * hand at the end of the world is then skipped; the screen overlays (fire, water) stay there.
 */
public final class EarlyHand {

	private EarlyHand() {
	}

	public static void draw(Minecraft mc, float tickDelta) {
		if (!Renderer.handFirstDue()) {
			return;
		}
		Renderer.handDrawnFirst();
		if (mc.options.perspective || mc.camera == null || mc.camera.isSleeping() || mc.options.hideGui) {
			return; // no hand this frame (third person, asleep, hidden), as the game's own check
		}
		int atlas = GlStateTracker.boundTexture2D(0);
		int mode = GlStateTracker.matrixMode();
		// The game is set for its translucent pass here (colour off for the depth pre-pass, blend on, no culling):
		// the hand gets the state it has at the end of the world, and the game's comes back after.
		boolean r = GlStateTracker.colorMask(0), g = GlStateTracker.colorMask(1), b = GlStateTracker.colorMask(2),
				a = GlStateTracker.colorMask(3);
		boolean depthMask = GlStateTracker.depthMask();
		boolean blend = GlStateTracker.isEnabled(GL11.GL_BLEND);
		boolean cull = GlStateTracker.isEnabled(GL11.GL_CULL_FACE);
		GL11.glColorMask(true, true, true, true);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		Renderer.stage(Renderer.P_HAND, true);
		GameRendererHandInvoker gr = (GameRendererHandInvoker) mc.gameRenderer;
		GL11.glPushMatrix();
		gr.altrender$applyHurtCam(tickDelta);
		if (mc.options.viewBobbing) {
			gr.altrender$applyViewBobbing(tickDelta);
		}
		mc.gameRenderer.itemInHandRenderer.renderHand(tickDelta);
		GL11.glPopMatrix();
		Renderer.stage(Renderer.P_HAND, false);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
		GL11.glMatrixMode(mode);
		// The terrain draws with the texture bound when it starts (the atlas): the hand bound its own.
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
		Lighting.turnOff();
		GL11.glColorMask(r, g, b, a);
		GL11.glDepthMask(depthMask);
		if (blend) {
			GL11.glEnable(GL11.GL_BLEND);
		}
		if (!cull) {
			GL11.glDisable(GL11.GL_CULL_FACE);
		}
	}
}
