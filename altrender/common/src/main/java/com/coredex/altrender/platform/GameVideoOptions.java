/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.platform;

import java.util.List;
import java.util.Set;

import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionFlag;

/**
 * The game's own video options, for Altrender's settings pages (development.md 5.2), and what applying them does.
 * One implementation per version node; values stay in the game's options file.
 */
public interface GameVideoOptions {

	/** For the General page: render distance, frame limit, GUI scale, fullscreen, view bobbing, ... */
	List<Option<?>> general();

	/** For the Quality page: graphics, smooth lighting, ... */
	List<Option<?>> quality();

	/** Does what the applied options' flags ask (rebuild terrain, reload textures, lay the screen out again). */
	void applyEffects(Set<OptionFlag> flags);

	/** Vsync on or off now. */
	void setVsync(boolean on);
}
