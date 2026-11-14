/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/Interactable.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; keyboard input and focus (development.md 5.1).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

/** Something that takes mouse (and, once focused, keyboard) input. */
public interface Interactable {

	boolean isMouseOver(double mouseX, double mouseY);

	default boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX,
			double deltaY) {
		return false;
	}

	default boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX,
			double deltaY) {
		return false;
	}

	/** A key went down while this has focus ({@link Key.Mods} bits in {@code mods}). */
	default boolean keyPressed(InteractionContext context, Key key, int mods) {
		return false;
	}

	/** A character was typed while this has focus. */
	default boolean charTyped(InteractionContext context, char chr) {
		return false;
	}

	/** Whether Tab reaches it. */
	default boolean isFocusable() {
		return false;
	}

	default void setFocused(boolean focused) {
	}

	/** Where it is, in its parent's (scrolled) coordinates, so a scroll pane can bring it into view. */
	default Dim2i getFocusBounds() {
		return null;
	}
}
