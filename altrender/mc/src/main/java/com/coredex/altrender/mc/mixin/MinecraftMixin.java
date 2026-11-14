/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin;

import java.awt.Canvas;

import com.coredex.altrender.mc.boot.Boot;
import com.coredex.altrender.mc.boot.StandaloneMinecraft;
import com.coredex.altrender.render.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {

	@Shadow
	public Canvas canvas;
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Shadow
	private void resize(int width, int height) {
		throw new AssertionError();
	}

	/** Frameless start: the LWJGL2 layer's SDL3 window instead of the AWT frame (StandaloneMinecraft). */
	@Inject(method = "startAndConnect", at = @At("HEAD"), cancellable = true)
	private static void altrender$startFrameless(String username, String sessionId, String server, CallbackInfo ci) {
		StandaloneMinecraft.start(username, sessionId, server);
		ci.cancel();
	}

	/** Start of the game's own init, before it creates the window. */
	@Inject(method = "init", at = @At("HEAD"))
	private void altrender$onInit(CallbackInfo ci) {
		Boot.onGameStart();
		if (this.canvas == null) {
			// Vanilla could only resize through its AWT canvas; the SDL3 window resizes itself (see below).
			Display.setResizable(true);
		}
	}

	/** Without a canvas vanilla never resizes: follow the window after each Display.update(). */
	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", shift = At.Shift.AFTER))
	private void altrender$followWindowSize(CallbackInfo ci) {
		// The harness runs unpaced, unless it's measuring the minimised pacing (-Daltrender.debug.forceIdle).
		boolean forceIdle = Boolean.getBoolean("altrender.debug.forceIdle");
		if (forceIdle || !com.coredex.altrender.debug.harness.Harness.active()) {
			Renderer.idlePacing(Display.isVisible() && !forceIdle, Display.isActive());
		}
		// The scene harness pins the size to its own capture framebuffer.
		if (this.canvas == null && Display.wasResized() && !com.coredex.altrender.debug.harness.Harness.active()) {
			int w = Math.max(1, Display.getWidth());
			int h = Math.max(1, Display.getHeight());
			if (w != this.width || h != this.height) {
				this.width = w;
				this.height = h;
				this.resize(w, h);
			}
		}
	}

	/** The window and its context exist: install the renderer (after the retry too, if the first create threw). */
	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;create()V", shift = At.Shift.AFTER))
	private void altrender$installRenderer(CallbackInfo ci) {
		com.coredex.altrender.api.AltrenderApi.Host.blockNames(new com.coredex.altrender.mc.state.B173BlockNames());
		Minecraft self = (Minecraft) (Object) this;
		com.coredex.altrender.api.AltrenderApi.Host.shaderPackScreenOpener(
				() -> com.coredex.altrender.mc.ui.B173Settings.openShaderPacks(self));
		com.coredex.altrender.api.AltrenderApi.Host.gameState(
				new com.coredex.altrender.mc.state.B173GameState((net.minecraft.client.Minecraft) (Object) this));
		Renderer.install("sdl3", FabricLoader.getInstance().getGameDir());
		Renderer.enableTerrain(new com.coredex.altrender.mc.adapter.B173BlockMesher());
		Renderer.gameTextures(new com.coredex.altrender.mc.state.B173GameTextures((net.minecraft.client.Minecraft) (Object) this));
		Renderer.entityDrawer(new com.coredex.altrender.mc.state.B173EntityDrawer((net.minecraft.client.Minecraft) (Object) this));
		// The vsync setting (development.md 5.2; the harness runs unsynced).
		if (Renderer.instance() != null && !com.coredex.altrender.debug.harness.Harness.active()) {
			org.lwjgl.opengl.Display.setVSyncEnabled(Renderer.config().vsync());
		}
		if (Renderer.instance() != null) {
			com.coredex.altrender.debugscreen.DebugScreen.load(FabricLoader.getInstance().getGameDir());
		}
	}

	@Inject(method = "resize", at = @At("TAIL"))
	private void altrender$resized(int width, int height, CallbackInfo ci) {
		Renderer.hooks().resized(this.width, this.height);
	}

	@Inject(method = "shutdown", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;destroy()V"))
	private void altrender$shutdown(CallbackInfo ci) {
		Renderer.hooks().shutdown();
	}

	private boolean altrender$shaderKeyDown;

	/** O in the world (no screen open) opens the shader pack screen, as Iris's key. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void altrender$shaderPackKey(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		boolean down = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_O);
		if (down && !altrender$shaderKeyDown && mc.world != null && mc.screen == null) {
			com.coredex.altrender.api.AltrenderApi.openShaderPackScreen();
		}
		altrender$shaderKeyDown = down;
	}
}
