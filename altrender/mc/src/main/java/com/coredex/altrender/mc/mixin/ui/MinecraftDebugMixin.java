/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.ui;

import java.util.List;

import com.coredex.altrender.debugscreen.DebugScreen;
import com.coredex.altrender.mc.ui.B173UiHost;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.options.OptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.mob.player.PlayerInventory;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The F3 keys with Altrender's debug screen (development.md 5.3): F3+1 / F3+2 toggle its charts (without picking a
 * hotbar slot), F3+F6 opens its options; the game's own frame chart gives way to Altrender's. The game's F3 and F3+S
 * are unchanged.
 */
@Mixin(Minecraft.class)
abstract class MinecraftDebugMixin {

	@Shadow
	public Screen screen;
	@Shadow
	public GameOptions options;

	private static boolean modern() {
		return Renderer.instance() != null && Renderer.config().modernDebugScreen() && Renderer.ui() != null;
	}

	/** The game's own frame chart only with the game's own F3. */
	@Redirect(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;debugEnabled:Z"))
	private boolean altrender$gameChart(GameOptions options) {
		return options.debugEnabled && !modern();
	}

	@Inject(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/entity/mob/player/ClientPlayerEntity;handleKeyEvent(IZ)V"))
	private void altrender$debugKeys(CallbackInfo ci) {
		if (!modern() || this.screen != null || !Keyboard.getEventKeyState() || !Keyboard.isKeyDown(Keyboard.KEY_F3)) {
			return;
		}
		int key = Keyboard.getEventKey();
		if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9) {
			DebugScreen.combo(key - Keyboard.KEY_1 + 1);
		} else if (key == Keyboard.KEY_F6) {
			Minecraft mc = (Minecraft) (Object) this;
			B173UiHost.get(mc).open(new OptionsScreen(List.of(DebugScreen.optionsProvider()), flags -> {
			}));
		}
	}

	/** Number keys with F3 held are chart toggles, not hotbar slots. */
	@Redirect(method = "tick", at = @At(value = "FIELD",
			target = "Lnet/minecraft/entity/mob/player/PlayerInventory;selectedSlot:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
	private void altrender$slot(PlayerInventory inventory, int slot) {
		if (!(modern() && Keyboard.isKeyDown(Keyboard.KEY_F3))) {
			inventory.selectedSlot = slot;
		}
	}
}
