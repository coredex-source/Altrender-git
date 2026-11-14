/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.Interactable;
import com.coredex.altrender.ui.framework.InteractableContainer;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.framework.Renderable;
import com.coredex.altrender.ui.widgets.Tooltip;

/**
 * A screen of the UI toolkit (development.md 5.1), shown by the version's host. It owns keyboard focus (Tab /
 * Shift+Tab move it; the focused widget gets keys and typed characters; Esc closes), the frame clock for smooth
 * animation, and one tooltip drawn above everything. Subclasses add their widgets in {@link #build}, which runs on
 * open and on every resize (GUI scale changes included).
 */
public abstract class UiScreen implements InteractableContainer, Renderable {

	protected UiHost host;
	protected int width;
	protected int height;
	private final List<Interactable> children = new ArrayList<>();
	private final List<Renderable> drawables = new ArrayList<>();
	private Interactable captured;
	private Interactable focused;
	private Tooltip.Request tooltip;
	private long lastFrame;
	/** Seconds since the previous frame (animation), capped. */
	protected float frameSeconds;

	/** Lays the screen out for a size in GUI units; called by the host on open and on resize. */
	public final void init(UiHost host, int width, int height) {
		this.host = host;
		this.width = width;
		this.height = height;
		children.clear();
		drawables.clear();
		captured = null;
		Interactable wasFocused = focused;
		focused = null;
		build();
		if (wasFocused != null) {
			setFocus(null);
		}
	}

	protected abstract void build();

	/** Adds a widget or anything drawable: drawn in order, input in reverse (the last added is on top). */
	protected <T> T add(T child) {
		if (child instanceof Interactable i) {
			children.add(0, i);
		}
		if (child instanceof Renderable r) {
			drawables.add(r);
		}
		return child;
	}

	public UiHost host() {
		return host;
	}

	/** The title for the host (window title, narration). */
	public String title() {
		return "";
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float partialTicks) {
		long now = System.nanoTime();
		frameSeconds = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		tooltip = null;
		renderBackground(ctx);
		boolean capturing = captured != null;
		for (Renderable r : drawables) {
			// While a drag is captured, the cursor doesn't hover anything else.
			boolean other = capturing && r != captured;
			r.render(ctx, other ? -1 : mouseX, other ? -1 : mouseY, partialTicks);
		}
		if (tooltip != null) {
			Tooltip.draw(ctx, tooltip);
		}
	}

	/** Behind the widgets: by default the game's own darkening over the world (its menus' gradient). */
	protected void renderBackground(DrawContext ctx) {
		ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
	}

	/** Shows a tooltip this frame (widgets call this while rendering; the last call wins). */
	public void tooltip(Tooltip.Request request) {
		tooltip = request;
	}

	/** The screen being drawn or receiving input (widgets reach it here). */
	private static UiScreen current;

	public static UiScreen current() {
		return current;
	}

	/** The host's frame: makes this the current screen, then renders. */
	public final void frame(UiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
		current = this;
		render(renderer, mouseX, mouseY, partialTicks);
	}

	public float frameSeconds() {
		return frameSeconds;
	}

	// Input from the host, in GUI units.

	public void onMouseClicked(double x, double y, int button) {
		current = this;
		focusClaimed = false;
		mouseClicked(host, x, y, button);
		// A widget that wants focus from a click (a text field) claims it while handling the click; any other click
		// lets go of the focus. (Hit-testing here would miss widgets inside scrolled panes.)
		if (!focusClaimed) {
			setFocus(null);
		}
	}

	private boolean focusClaimed;

	/** Called by a widget handling a click that should give it keyboard focus. */
	public void requestFocus(Interactable widget) {
		focusClaimed = true;
		setFocus(widget);
	}

	public void onMouseReleased(double x, double y, int button) {
		current = this;
		mouseReleased(host, x, y, button);
	}

	public void onMouseDragged(double x, double y, int button, double dx, double dy) {
		current = this;
		mouseDragged(host, x, y, button, dx, dy);
	}

	public void onMouseScrolled(double x, double y, double amount) {
		current = this;
		mouseScrolled(host, x, y, 0, amount);
	}

	/** A key went down; returns whether the screen used it. */
	public boolean onKey(Key key, int mods) {
		current = this;
		if (focused != null && focused.keyPressed(host, key, mods)) {
			return true;
		}
		switch (key) {
			case TAB -> {
				moveFocus((mods & Key.Mods.SHIFT) != 0 ? -1 : 1);
				return true;
			}
			case ESCAPE -> {
				if (focused != null) {
					setFocus(null);
				} else {
					onClose();
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	public void onChar(char c) {
		current = this;
		if (focused != null) {
			focused.charTyped(host, c);
		}
	}

	/** Esc with nothing focused: back to where the screen was opened from. */
	public void onClose() {
		host.close(this);
	}

	/** The host is about to hide this screen. */
	public void removed() {
		setFocus(null);
		if (current == this) {
			current = null;
		}
	}

	public Interactable focused() {
		return focused;
	}

	public void setFocus(Interactable target) {
		if (focused == target) {
			return;
		}
		if (focused != null) {
			focused.setFocused(false);
		}
		focused = target;
		if (target != null) {
			target.setFocused(true);
			reveal(target, children);
		}
	}

	private void moveFocus(int direction) {
		List<Interactable> focusables = new ArrayList<>();
		collectFocusables(focusables);
		if (focusables.isEmpty()) {
			return;
		}
		int i = focusables.indexOf(focused);
		int next = i < 0 ? (direction > 0 ? 0 : focusables.size() - 1)
				: Math.floorMod(i + direction, focusables.size());
		setFocus(focusables.get(next));
	}

	/** Scrolls every pane containing {@code target} so it's in view. */
	private static void reveal(Interactable target, List<? extends Interactable> in) {
		for (Interactable i : in) {
			if (i instanceof Revealer r) {
				r.reveal(target);
			}
			if (i instanceof InteractableContainer c) {
				reveal(target, c.interactableChildren());
			}
		}
	}

	/** A container that can scroll a descendant into view (scroll panes). */
	public interface Revealer {
		void reveal(Interactable target);
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

	/** The rectangle of the whole screen. */
	protected Dim2i bounds() {
		return new Dim2i(0, 0, width, height);
	}
}
