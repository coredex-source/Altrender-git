/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.widgets;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.UiScreen;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.Interactable;
import com.coredex.altrender.ui.framework.InteractableContainer;
import com.coredex.altrender.ui.framework.InteractionContext;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.framework.Renderable;

/**
 * A vertically scrolling view of a {@link Panel} (whose coordinates start at the pane's top left), with Sodium's scroll
 * bar: smooth scrolling
 * (the wheel sets a target the view eases to, snapped to whole framebuffer pixels so text stays sharp), a draggable
 * thumb, Page Up/Down while something inside has focus, and scrolling a focused widget into view.
 */
public class ScrollPane implements InteractableContainer, Renderable, UiScreen.Revealer {

	private static final int BAR = 10;
	private static final int WHEEL_STEP = 24;

	private final Dim2i dim;
	private final Panel content;
	private final List<Interactable> self;
	private double target;
	private double offset;
	private boolean dragging;
	private double dragStart;
	private double dragOffset;
	private Interactable captured;

	public ScrollPane(Dim2i dim, Panel content) {
		this.dim = dim;
		this.content = content;
		this.self = List.of(content);
	}

	public Dim2i dim() {
		return dim;
	}

	private int maxOffset() {
		return Math.max(0, content.contentHeight() - dim.height());
	}

	public double offset() {
		return offset;
	}

	/** Whether the content is taller than the view. */
	public boolean canScroll() {
		return maxOffset() > 0;
	}

	/** Where the view is easing to (the wheel moves this first). */
	public double target() {
		return target;
	}

	/** Jumps to {@code value} without easing (restoring a page's position). */
	public void setOffset(double value) {
		target = offset = clamp(value);
	}

	private double clamp(double v) {
		return Math.max(0, Math.min(maxOffset(), v));
	}

	private int viewWidth() {
		return maxOffset() > 0 ? dim.width() - BAR - 1 : dim.width();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		UiScreen screen = UiScreen.current();
		float dt = screen != null ? screen.frameSeconds() : 1f;
		target = clamp(target);
		offset += (target - offset) * Math.min(1.0, dt * 16.0);
		if (Math.abs(target - offset) < 0.05) {
			offset = target;
		}
		int scale = screen != null ? Math.max(1, screen.host().guiScale()) : 1;
		double shown = Math.round(offset * scale) / (double) scale;
		boolean inside = dim.containsCursor(mouseX, mouseY) && mouseX < dim.x() + viewWidth();
		ctx.enableScissor(dim.x(), dim.y(), dim.x() + viewWidth(), dim.getLimitY());
		ctx.pushMatrix();
		ctx.translate(dim.x(), dim.y() - shown, 0);
		content.render(ctx, inside ? mouseX - dim.x() : -1, inside ? (int) Math.floor(mouseY - dim.y() + shown) : -1,
				delta);
		ctx.popMatrix();
		ctx.disableScissor();
		if (maxOffset() > 0) {
			// Sodium's scroll bar: an outlined track, a solid thumb inset by 2.
			Dim2i thumb = thumb();
			ctx.drawBorder(dim.getLimitX() - BAR, dim.y(), dim.getLimitX(), dim.getLimitY(), Theme.OUTLINE);
			ctx.fill(thumb.x() + 2, thumb.y() + 2, thumb.getLimitX() - 2, thumb.getLimitY() - 2, Theme.OUTLINE);
		}
	}

	private Dim2i thumb() {
		int track = dim.height();
		int length = Math.max(16, track * dim.height() / Math.max(1, content.contentHeight()));
		int y = dim.y() + (int) Math.round((track - length) * (offset / Math.max(1, maxOffset())));
		return new Dim2i(dim.getLimitX() - BAR, y, BAR, length);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return dim.containsCursor(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
		if (!dim.containsCursor(mouseX, mouseY)) {
			return false;
		}
		if (maxOffset() > 0 && mouseX >= dim.getLimitX() - BAR) {
			Dim2i thumb = thumb();
			if (!thumb.containsCursor(mouseX, mouseY)) {
				// Track click: jump so the thumb centres on the cursor.
				double t = (mouseY - dim.y() - thumb.height() / 2.0) / Math.max(1, dim.height() - thumb.height());
				target = clamp(t * maxOffset());
			}
			dragging = true;
			dragStart = mouseY;
			dragOffset = target;
			captured = null;
			return true;
		}
		return InteractableContainer.super.mouseClicked(context, mouseX - dim.x(), mouseY - dim.y() + offset, button);
	}

	@Override
	public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double dx,
			double dy) {
		if (dragging) {
			Dim2i thumb = thumb();
			double perPixel = maxOffset() / (double) Math.max(1, dim.height() - thumb.height());
			target = offset = clamp(dragOffset + (mouseY - dragStart) * perPixel);
			return true;
		}
		return InteractableContainer.super.mouseDragged(context, mouseX - dim.x(), mouseY - dim.y() + offset, button,
				dx, dy);
	}

	@Override
	public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return InteractableContainer.super.mouseReleased(context, mouseX - dim.x(), mouseY - dim.y() + offset, button);
	}

	@Override
	public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double dx, double dy) {
		if (!dim.containsCursor(mouseX, mouseY)) {
			return false;
		}
		if (InteractableContainer.super.mouseScrolled(context, mouseX - dim.x(), mouseY - dim.y() + offset, dx, dy)) {
			return true;
		}
		if (maxOffset() == 0) {
			return false;
		}
		target = clamp(target - dy * WHEEL_STEP);
		return true;
	}

	@Override
	public boolean keyPressed(InteractionContext context, Key key, int mods) {
		return false;
	}

	/** Page Up / Page Down / Home / End for the screen to pass on while focus is inside. */
	public boolean page(Key key) {
		switch (key) {
			case PAGE_UP -> target = clamp(target - dim.height() * 0.9);
			case PAGE_DOWN -> target = clamp(target + dim.height() * 0.9);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public void reveal(Interactable target) {
		List<Interactable> inside = new ArrayList<>();
		content.collectFocusables(inside);
		if (!inside.contains(target) || target.getFocusBounds() == null) {
			return;
		}
		Dim2i b = target.getFocusBounds();
		if (b.y() - 4 < this.target) {
			this.target = clamp(b.y() - 4);
		} else if (b.getLimitY() + 4 > this.target + dim.height()) {
			this.target = clamp(b.getLimitY() + 4 - dim.height());
		}
	}

	@Override
	public List<? extends Interactable> interactableChildren() {
		return self;
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
