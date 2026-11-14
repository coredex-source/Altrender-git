/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/InteractableContainer.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; lists instead of streams (no allocation per event); focus traversal order.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

import java.util.ArrayList;
import java.util.List;

/** An {@link Interactable} made of others: input goes to the first child that takes it. */
public interface InteractableContainer extends Interactable {

	List<? extends Interactable> interactableChildren();

	/**
	 * The child that most recently took a mouse click. While set, drags go straight to it instead of being checked
	 * against the cursor again, so a drag started on a slider isn't dropped when the cursor leaves its narrow
	 * bounds. Cleared on release.
	 */
	Interactable getCapturedChild();

	void setCapturedChild(Interactable child);

	@Override
	default boolean isMouseOver(double mouseX, double mouseY) {
		for (Interactable i : interactableChildren()) {
			if (i.isMouseOver(mouseX, mouseY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	default boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
		setCapturedChild(null);
		for (Interactable i : new ArrayList<>(interactableChildren())) {
			if (i.mouseClicked(context, mouseX, mouseY, button)) {
				setCapturedChild(i);
				return true;
			}
		}
		return false;
	}

	@Override
	default boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
		Interactable captured = getCapturedChild();
		setCapturedChild(null);
		if (captured != null) {
			captured.mouseReleased(context, mouseX, mouseY, button);
			return true;
		}
		for (Interactable i : new ArrayList<>(interactableChildren())) {
			if (i.mouseReleased(context, mouseX, mouseY, button)) {
				return true;
			}
		}
		return false;
	}

	@Override
	default boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX,
			double deltaY) {
		Interactable captured = getCapturedChild();
		if (captured != null) {
			return captured.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY);
		}
		for (Interactable i : new ArrayList<>(interactableChildren())) {
			if (i.isMouseOver(mouseX, mouseY) && i.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	default boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX,
			double deltaY) {
		for (Interactable i : new ArrayList<>(interactableChildren())) {
			if (i.isMouseOver(mouseX, mouseY) && i.mouseScrolled(context, mouseX, mouseY, deltaX, deltaY)) {
				return true;
			}
		}
		return false;
	}

	/** Every focusable descendant, in tree order (Tab order). */
	default void collectFocusables(List<Interactable> out) {
		for (Interactable i : interactableChildren()) {
			if (i.isFocusable()) {
				out.add(i);
			}
			if (i instanceof InteractableContainer c) {
				c.collectFocusables(out);
			}
		}
	}
}
