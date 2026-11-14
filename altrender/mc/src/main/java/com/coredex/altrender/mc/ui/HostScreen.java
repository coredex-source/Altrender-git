/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import com.coredex.altrender.debug.harness.Harness;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.UiRenderer;
import com.coredex.altrender.ui.UiScreen;
import com.coredex.altrender.ui.framework.Key;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * A toolkit {@link UiScreen} shown as a b1.7.3 screen: the game's mouse and keyboard events translated (wheel and
 * drags included, which the game's own screens don't get), drawing through Altrender's UI renderer.
 */
public final class HostScreen extends Screen {

	private final UiScreen ui;
	private final Screen parent;
	private final B173UiHost host;
	private double lastX = -1;
	private double lastY = -1;
	private int heldButton = -1;

	public HostScreen(UiScreen ui, Screen parent, B173UiHost host) {
		this.ui = ui;
		this.parent = parent;
		this.host = host;
	}

	public UiScreen ui() {
		return ui;
	}

	public Screen parent() {
		return parent;
	}

	@Override
	public void init() {
		ui.init(host, this.width, this.height);
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		if (this.minecraft.world == null) {
			this.renderBackground(); // the game's backdrop outside a world, as its own menus
		}
		UiRenderer renderer = Renderer.ui();
		if (renderer == null) {
			return;
		}
		if (Harness.active()) {
			// Screenshots mustn't depend on where the real cursor is.
			mouseX = -10000;
			mouseY = -10000;
		}
		renderer.begin(host);
		try {
			ui.frame(renderer, mouseX, mouseY, tickDelta);
		} finally {
			renderer.end();
		}
	}

	@Override
	public void handleMouse() {
		double x = Mouse.getEventX() * (double) this.width / this.minecraft.width;
		double y = this.height - Mouse.getEventY() * (double) this.height / this.minecraft.height - 1;
		int button = Mouse.getEventButton();
		int wheel = Mouse.getEventDWheel();
		if (button >= 0) {
			if (Mouse.getEventButtonState()) {
				heldButton = button;
				ui.onMouseClicked(x, y, button);
			} else {
				heldButton = -1;
				ui.onMouseReleased(x, y, button);
			}
		} else if (wheel != 0) {
			ui.onMouseScrolled(x, y, Math.signum(wheel));
		} else if (heldButton >= 0 && lastX >= 0) {
			ui.onMouseDragged(x, y, heldButton, x - lastX, y - lastY);
		}
		lastX = x;
		lastY = y;
	}

	@Override
	public void handleKeyboard() {
		if (!Keyboard.getEventKeyState()) {
			return;
		}
		int code = Keyboard.getEventKey();
		char c = Keyboard.getEventCharacter();
		if (code == Keyboard.KEY_F11) {
			this.minecraft.toggleFullscreen();
			return;
		}
		Key key = key(code);
		int mods = (host.isSpecialKeyDown(com.coredex.altrender.ui.framework.InteractionContext.SpecialKey.SHIFT)
				? Key.Mods.SHIFT : 0)
				| (host.isSpecialKeyDown(com.coredex.altrender.ui.framework.InteractionContext.SpecialKey.CTRL)
				? Key.Mods.CTRL : 0)
				| (host.isSpecialKeyDown(com.coredex.altrender.ui.framework.InteractionContext.SpecialKey.ALT)
				? Key.Mods.ALT : 0);
		boolean used = key != Key.OTHER && ui.onKey(key, mods);
		if (!used && c >= ' ' && c != 127) {
			ui.onChar(c);
		}
	}

	private static Key key(int code) {
		return switch (code) {
			case Keyboard.KEY_TAB -> Key.TAB;
			case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> Key.ENTER;
			case Keyboard.KEY_ESCAPE -> Key.ESCAPE;
			case Keyboard.KEY_SPACE -> Key.SPACE;
			case Keyboard.KEY_UP -> Key.UP;
			case Keyboard.KEY_DOWN -> Key.DOWN;
			case Keyboard.KEY_LEFT -> Key.LEFT;
			case Keyboard.KEY_RIGHT -> Key.RIGHT;
			case Keyboard.KEY_HOME -> Key.HOME;
			case Keyboard.KEY_END -> Key.END;
			case Keyboard.KEY_PRIOR -> Key.PAGE_UP;
			case Keyboard.KEY_NEXT -> Key.PAGE_DOWN;
			case Keyboard.KEY_BACK -> Key.BACKSPACE;
			case Keyboard.KEY_DELETE -> Key.DELETE;
			case Keyboard.KEY_A -> Key.A;
			case Keyboard.KEY_C -> Key.C;
			case Keyboard.KEY_V -> Key.V;
			case Keyboard.KEY_X -> Key.X;
			case Keyboard.KEY_F -> Key.F;
			default -> Key.OTHER;
		};
	}

	@Override
	public void removed() {
		ui.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
