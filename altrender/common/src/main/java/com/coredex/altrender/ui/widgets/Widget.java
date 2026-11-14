/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.widgets;

import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.Interactable;
import com.coredex.altrender.ui.framework.Renderable;

/** A widget with a fixed rectangle, an enabled flag and keyboard focus. */
public abstract class Widget implements Renderable, Interactable {

	protected Dim2i dim;
	protected boolean enabled = true;
	protected boolean focused;

	protected Widget(Dim2i dim) {
		this.dim = dim;
	}

	public Dim2i dim() {
		return dim;
	}

	public void setDim(Dim2i dim) {
		this.dim = dim;
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return dim.containsCursor(mouseX, mouseY);
	}

	@Override
	public boolean isFocusable() {
		return enabled;
	}

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	public boolean focused() {
		return focused;
	}

	@Override
	public Dim2i getFocusBounds() {
		return dim;
	}

}
