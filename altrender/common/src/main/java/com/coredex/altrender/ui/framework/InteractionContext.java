/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/InteractionContext.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; modifier bits.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

/** The host's side of an interaction: sounds, modifier keys, the clipboard. */
public interface InteractionContext {

	default void playClickSound() {
	}

	default boolean isSpecialKeyDown(SpecialKey key) {
		return false;
	}

	String getClipboard();

	void setClipboard(String text);

	enum SpecialKey {
		SHIFT,
		CTRL,
		ALT
	}
}
