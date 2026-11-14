/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import com.coredex.altrender.mc.mixin.ui.TextRendererAccessor;
import com.coredex.altrender.ui.Lang;
import com.coredex.altrender.ui.UiFont;
import com.coredex.altrender.ui.UiHost;
import com.coredex.altrender.ui.UiScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.locale.Language;
import org.lwjgl.input.Keyboard;

/**
 * The UI toolkit's host on b1.7.3 (development.md 5.1): sizes from the game's window and GUI scale, the game's
 * 128 × 128 ASCII font (glyphs in {@code font.txt} order from cell 32, the game's own widths), its texture manager,
 * click sound and clipboard. Render thread only.
 */
public final class B173UiHost implements UiHost, UiFont {

	private static B173UiHost instance;

	private final Minecraft mc;
	private int[] widths;

	private B173UiHost(Minecraft mc) {
		this.mc = mc;
	}

	public static B173UiHost get(Minecraft mc) {
		if (instance == null) {
			instance = new B173UiHost(mc);
		}
		return instance;
	}

	@Override
	public int framebufferWidth() {
		return mc.width;
	}

	@Override
	public int framebufferHeight() {
		return mc.height;
	}

	@Override
	public int guiScale() {
		return new Window(mc.options, mc.width, mc.height).scale;
	}

	@Override
	public UiFont font() {
		return this;
	}

	@Override
	public int texture() {
		return mc.textRenderer.boundTexture;
	}

	@Override
	public int lineHeight() {
		return 9;
	}

	/** Glyph index + 1 of each char below 256 (0: none); the game's own lookup is a search through font.txt. */
	private static final int[] INDEX = new int[256];

	static {
		for (int c = 0; c < 256; c++) {
			INDEX[c] = SharedConstants.VALID_CHAT_CHARACTERS.indexOf((char) c) + 1;
		}
	}

	@Override
	public boolean glyph(char c, Glyph out) {
		int index = c < 256 ? INDEX[c] - 1 : SharedConstants.VALID_CHAT_CHARACTERS.indexOf(c);
		if (index < 0 || index + 32 >= 256) {
			return false;
		}
		if (widths == null) {
			widths = ((TextRendererAccessor) mc.textRenderer).altrender$characterWidths();
		}
		int cell = index + 32;
		float u = cell % 16 * 8 / 128f;
		float v = cell / 16 * 8 / 128f;
		out.u0 = u;
		out.v0 = v;
		out.u1 = u + 7.99f / 128f;
		out.v1 = v + 7.99f / 128f;
		out.width = 7.99f;
		out.height = 7.99f;
		out.advance = widths[cell];
		return true;
	}

	@Override
	public int texture(String path) {
		return mc.textureManager.load(path);
	}

	@Override
	public String translate(String key, Object... args) {
		String s = Lang.get("en_us", key);
		if (s == null) {
			s = Language.getInstance().translate(key);
		}
		if (args.length > 0) {
			try {
				return String.format(s, args);
			} catch (java.util.IllegalFormatException e) {
				return s;
			}
		}
		return s;
	}

	@Override
	public void open(UiScreen screen) {
		mc.openScreen(new HostScreen(screen, mc.screen, this));
	}

	@Override
	public void close(UiScreen screen) {
		if (mc.screen instanceof HostScreen host && host.ui() == screen) {
			mc.openScreen(host.parent());
		}
	}

	@Override
	public void playClickSound() {
		mc.soundEngine.play("random.click", 1.0F, 1.0F);
	}

	@Override
	public boolean isSpecialKeyDown(SpecialKey key) {
		return switch (key) {
			case SHIFT -> Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
			case CTRL -> Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
			case ALT -> Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184);
		};
	}

	@Override
	public String getClipboard() {
		String s = Screen.getClipboard();
		return s != null ? s : "";
	}

	@Override
	public void setClipboard(String text) {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		} catch (RuntimeException | java.awt.AWTError e) {
			// no clipboard (headless): nothing to do
		}
	}
}
