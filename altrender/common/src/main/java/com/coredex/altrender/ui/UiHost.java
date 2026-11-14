/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

import com.coredex.altrender.ui.framework.InteractionContext;

/**
 * What the UI needs from the game (development.md 0.4's {@code UiHost}): sizes, the font, textures, sounds, the
 * clipboard, translations, and showing screens. One implementation per version node. Render thread only.
 */
public interface UiHost extends InteractionContext {

	int framebufferWidth();

	int framebufferHeight();

	/** The GUI scale in effect (framebuffer pixels per GUI unit). */
	int guiScale();

	UiFont font();

	/** GL name of a texture by the game's path, loaded if needed; 0 if there's none. */
	int texture(String path);

	/** {@code key} translated, or {@code key} itself if there's no translation. */
	String translate(String key, Object... args);

	/** Shows {@code screen} in place of the current one (it returns to the current one when closed). */
	void open(UiScreen screen);

	/** Closes {@code screen}, back to the screen it was opened from. */
	void close(UiScreen screen);
}
