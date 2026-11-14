/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.widgets;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.Interactable;
import com.coredex.altrender.ui.framework.InteractableContainer;
import com.coredex.altrender.ui.framework.Renderable;

/** Widgets at fixed positions, drawn in order; the content of a {@link ScrollPane} or a group. */
public class Panel implements InteractableContainer, Renderable {

	private final List<Interactable> children = new ArrayList<>();
	private final List<Renderable> drawables = new ArrayList<>();
	private Interactable captured;
	/** Height of the content (for scrolling). */
	protected int contentHeight;

	public <T> T add(T child) {
		if (child instanceof Interactable i) {
			children.add(i);
		}
		if (child instanceof Renderable r) {
			drawables.add(r);
		}
		return child;
	}

	public void clear() {
		children.clear();
		drawables.clear();
		captured = null;
		contentHeight = 0;
	}

	public int contentHeight() {
		return contentHeight;
	}

	public void setContentHeight(int height) {
		contentHeight = height;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		for (Renderable r : drawables) {
			boolean other = captured != null && r != captured;
			r.render(ctx, other ? -1 : mouseX, other ? -1 : mouseY, delta);
		}
	}

	@Override
	public List<? extends Interactable> interactableChildren() {
		return children;
	}

	@Override
	public Interactable getCapturedChild() {
		return captured;
	}

	@Override
	public void setCapturedChild(Interactable child) {
		captured = child;
	}
}
