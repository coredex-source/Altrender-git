/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.immediate.ImmediateStream;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The tessellator's draw from Altrender's stream buffer instead of client arrays (development.md 3.2): the same
 * vertex bytes, pointers, client states and draw call as the game's {@code end()}, only sourced from a buffer. While
 * a display list is being compiled the game's own path runs.
 */
@Mixin(Tesselator.class)
abstract class TesselatorStreamMixin {

	@Shadow
	private static boolean TRIANGLE_MODE;
	@Shadow
	private ByteBuffer byteBuffer;
	@Shadow
	private IntBuffer intBuffer;
	@Shadow
	private int[] vertexBuffer;
	@Shadow
	private int vertexCount;
	@Shadow
	private boolean colored;
	@Shadow
	private boolean textured;
	@Shadow
	private boolean hasNormals;
	@Shadow
	private int index;
	@Shadow
	private int drawMode;
	@Shadow
	private boolean tesselating;
	@Shadow
	private boolean useVbo;

	@Shadow
	private void clear() {
		throw new AssertionError();
	}

	@Inject(method = "end", at = @At("HEAD"), cancellable = true)
	private void altrender$streamDraw(CallbackInfo ci) {
		ImmediateStream stream = ImmediateStream.get();
		if (stream == null || this.useVbo || !this.tesselating) {
			return;
		}
		if (GlStateTracker.compiling()) {
			// The game's path compiles the list; a chosen static list also keeps a copy (ListCapture, 3.3).
			if (this.vertexCount > 0) {
				((Buffer) this.intBuffer).clear();
				this.intBuffer.put(this.vertexBuffer, 0, this.index);
				((Buffer) this.byteBuffer).position(0);
				((Buffer) this.byteBuffer).limit(this.index * 4);
				com.coredex.altrender.immediate.ListCapture.onTesselatorDraw(this.byteBuffer, this.textured, this.colored,
						this.hasNormals, this.drawMode == 7 && TRIANGLE_MODE ? GL11.GL_TRIANGLES : this.drawMode,
						this.vertexCount);
			}
			return;
		}
		long at = 0;
		if (this.vertexCount > 0) {
			((Buffer) this.intBuffer).clear();
			this.intBuffer.put(this.vertexBuffer, 0, this.index);
			((Buffer) this.byteBuffer).position(0);
			((Buffer) this.byteBuffer).limit(this.index * 4);
			at = stream.upload(this.byteBuffer, 32);
			if (at < 0) {
				return; // too large for the stream: the game's client arrays draw it
			}
		}
		ci.cancel();
		this.tesselating = false;
		if (this.vertexCount > 0) {
			if (this.textured) {
				GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 32, at + 12);
				GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			}
			if (this.colored) {
				GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 32, at + 20);
				GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
			}
			if (this.hasNormals) {
				GL11.glNormalPointer(GL11.GL_BYTE, 32, at + 24);
				GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
			}
			GL11.glVertexPointer(3, GL11.GL_FLOAT, 32, at);
			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
			GL11.glDrawArrays(this.drawMode == 7 && TRIANGLE_MODE ? GL11.GL_TRIANGLES : this.drawMode, 0, this.vertexCount);
			GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
			if (this.textured) {
				GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			}
			if (this.colored) {
				GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
			}
			if (this.hasNormals) {
				GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
			}
			stream.unbind();
		}
		this.clear();
	}
}
