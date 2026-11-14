/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/taumc/celeritas/api/options/control/{TickBoxControl,SliderControl,CyclingControl}.java at
 * commit 38ccea6c, licensed under LGPL-3.0; Celeritas's options GUI derives from Reese's Sodium Options (Copyright (c)
 * 2021 FlashyReese, MIT; the notice is in META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: the three control elements on Altrender's option API (controls are records there,
 * elements made here); keyboard (Space/Enter toggles and cycles, Left/Right steps, Home/End); Cycling skips values its
 * control doesn't allow.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.options;

import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.InteractionContext;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;

/** The row element for each kind of {@link Control}. */
public final class ControlElements {

	private ControlElements() {
	}

	/** The widest a control's part of the row gets (names are cut before it). */
	public static int maxWidth(Control<?> control) {
		return switch (control) {
			case Control.TickBox t -> 30;
			case Control.Slider s -> 130;
			case Control.Cycling<?> c -> 70;
			case Control.Action a -> 90;
		};
	}

	@SuppressWarnings("unchecked")
	public static ControlElement<?> create(Option<?> option, Dim2i dim) {
		return switch (option.control()) {
			case Control.TickBox t -> new TickBox((Option<Boolean>) option, dim);
			case Control.Slider s -> new Slider((Option<Integer>) option, dim, s);
			case Control.Cycling<?> c -> cycling(option, dim, c);
			case Control.Action a -> new Action((Option<Boolean>) option, dim, a);
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> ControlElement<T> cycling(Option<?> option, Dim2i dim, Control.Cycling<T> control) {
		return new Cycling<>((Option<T>) option, dim, control);
	}

	static final class TickBox extends ControlElement<Boolean> {
		private final Dim2i button;

		TickBox(Option<Boolean> option, Dim2i dim) {
			super(option, dim, 30);
			button = new Dim2i(dim.getLimitX() - 16, dim.getCenterY() - 5, 10, 10);
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			int x = button.x();
			int y = button.y();
			int w = x + button.width();
			int h = y + button.height();
			boolean enabled = option.isAvailable();
			boolean ticked = option.value();
			int color = enabled ? (ticked ? Theme.ACTIVATED : 0xFFFFFFFF) : 0xFFAAAAAA;
			if (ticked) {
				ctx.fill(x + 2, y + 2, w - 2, h - 2, color);
			}
			ctx.drawBorder(x, y, w, h, color);
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int b) {
			if (option.isAvailable() && b == 0 && dim.containsCursor(mouseX, mouseY)) {
				toggle();
				context.playClickSound();
				return true;
			}
			return false;
		}

		@Override
		public boolean keyPressed(InteractionContext context, Key key, int mods) {
			if (option.isAvailable() && (key == Key.SPACE || key == Key.ENTER)) {
				toggle();
				context.playClickSound();
				return true;
			}
			return false;
		}

		private void toggle() {
			option.setValue(!option.value());
		}
	}

	/** A button at the row's right end that runs the action. */
	static final class Action extends ControlElement<Boolean> {
		private final Control.Action action;
		private final Dim2i button;

		Action(Option<Boolean> option, Dim2i dim, Control.Action action) {
			super(option, dim, 90);
			this.action = action;
			button = new Dim2i(dim.getLimitX() - 86, dim.y() + 2, 82, dim.height() - 4);
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			boolean enabled = option.isAvailable();
			boolean over = enabled && button.containsCursor(mouseX, mouseY);
			int color = enabled ? (over ? Theme.ACTIVATED : 0xFFFFFFFF) : 0xFFAAAAAA;
			ctx.drawBorder(button.x(), button.y(), button.getLimitX(), button.getLimitY(), color);
			String label = ctx.extractString(TextComponent.translatable(action.label()));
			ctx.drawString(TextComponent.literal(label), button.getCenterX() - ctx.getStringWidth(label) / 2,
					button.getCenterY() - 4, color);
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int b) {
			if (option.isAvailable() && b == 0 && button.containsCursor(mouseX, mouseY)) {
				action.run().run();
				context.playClickSound();
				return true;
			}
			return false;
		}

		@Override
		public boolean keyPressed(InteractionContext context, Key key, int mods) {
			if (option.isAvailable() && (key == Key.SPACE || key == Key.ENTER)) {
				action.run().run();
				context.playClickSound();
				return true;
			}
			return false;
		}
	}

	static final class Slider extends ControlElement<Integer> {
		private static final int THUMB_WIDTH = 2;
		private static final int TRACK_HEIGHT = 1;
		private final Dim2i sliderBounds;
		private final Control.Slider control;
		private final int range;
		private double thumbPosition;
		private boolean sliderHeld;

		Slider(Option<Integer> option, Dim2i dim, Control.Slider control) {
			super(option, dim, 130);
			this.control = control;
			range = control.max() - control.min();
			thumbPosition = thumbPositionFor(option.value());
			sliderBounds = new Dim2i(dim.getLimitX() - 96, dim.getCenterY() - 5, 90, 10);
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			if (option.isAvailable() && (sliderHeld || isMouseOver(mouseX, mouseY) || focused)) {
				renderSlider(ctx);
			} else {
				renderStandaloneValue(ctx);
			}
		}

		private TextComponent label(int value) {
			TextComponent t = TextComponent.translatable(control.format().apply(value));
			return option.isAvailable() ? t : t.withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
		}

		private void renderStandaloneValue(DrawContext ctx) {
			TextComponent label = label(option.value());
			int labelWidth = ctx.getStringWidth(label);
			ctx.drawString(label, sliderBounds.x() + sliderBounds.width() - labelWidth,
					sliderBounds.y() + sliderBounds.height() / 2 - 4, 0xFFFFFFFF);
		}

		private void renderSlider(DrawContext ctx) {
			int sliderX = sliderBounds.x();
			int sliderY = sliderBounds.y();
			int sliderWidth = sliderBounds.width();
			int sliderHeight = sliderBounds.height();
			thumbPosition = thumbPositionFor(option.value());
			double thumbOffset = Math.max(0, Math.min(sliderWidth,
					(double) (intValue() - control.min()) / range * sliderWidth));
			int thumbX = (int) (sliderX + thumbOffset - THUMB_WIDTH);
			int trackY = (int) (sliderY + sliderHeight / 2f - (double) TRACK_HEIGHT / 2);
			ctx.fill(thumbX, sliderY, thumbX + THUMB_WIDTH * 2, sliderY + sliderHeight, 0xFFFFFFFF);
			ctx.fill(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, 0xFFFFFFFF);
			TextComponent label = label(intValue());
			int labelWidth = ctx.getStringWidth(label);
			ctx.drawString(label, sliderX - labelWidth - 6, sliderY + sliderHeight / 2 - 4, 0xFFFFFFFF);
		}

		private int intValue() {
			return control.min() + control.interval() * (int) Math.round(thumbPosition * range / control.interval());
		}

		private double thumbPositionFor(int value) {
			return (value - control.min()) / (double) range;
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int b) {
			sliderHeld = false;
			if (option.isAvailable() && b == 0 && dim.containsCursor(mouseX, mouseY)) {
				if (sliderBounds.containsCursor((int) mouseX, (int) mouseY)) {
					setValueFromMouse(mouseX);
					sliderHeld = true;
				}
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int b, double dx,
				double dy) {
			if (option.isAvailable() && b == 0 && sliderHeld) {
				setValueFromMouse(mouseX);
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int b) {
			sliderHeld = false;
			return false;
		}

		@Override
		public boolean keyPressed(InteractionContext context, Key key, int mods) {
			if (!option.isAvailable()) {
				return false;
			}
			int v = option.value();
			int next = switch (key) {
				case LEFT -> v - control.interval();
				case RIGHT -> v + control.interval();
				case HOME -> control.min();
				case END -> control.max();
				default -> Integer.MIN_VALUE;
			};
			if (next == Integer.MIN_VALUE) {
				return false;
			}
			setValue((Math.max(control.min(), Math.min(control.max(), next)) - control.min()) / (double) range);
			return true;
		}

		private void setValueFromMouse(double mouseX) {
			setValue((mouseX - sliderBounds.x()) / (sliderBounds.width() - 1));
		}

		private void setValue(double position) {
			thumbPosition = Math.max(0.0, Math.min(1.0, position));
			int value = intValue();
			if (!option.value().equals(value)) {
				option.setValue(value);
			}
		}
	}

	static final class Cycling<T> extends ControlElement<T> {
		private final Control.Cycling<T> control;

		Cycling(Option<T> option, Dim2i dim, Control.Cycling<T> control) {
			super(option, dim, 70);
			this.control = control;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			TextComponent name = TextComponent.translatable(control.names().apply(option.value()));
			if (!option.isAvailable()) {
				name = name.withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
			}
			int strWidth = ctx.getStringWidth(name);
			ctx.drawString(name, dim.getLimitX() - strWidth - 6, dim.getCenterY() - 4, 0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int b) {
			if (option.isAvailable() && b == 0 && dim.containsCursor(mouseX, mouseY)) {
				cycle(context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT));
				context.playClickSound();
				return true;
			}
			return false;
		}

		@Override
		public boolean keyPressed(InteractionContext context, Key key, int mods) {
			if (!option.isAvailable()) {
				return false;
			}
			if (key == Key.LEFT || key == Key.RIGHT || key == Key.ENTER || key == Key.SPACE) {
				cycle(key == Key.LEFT || (mods & Key.Mods.SHIFT) != 0);
				context.playClickSound();
				return true;
			}
			return false;
		}

		private void cycle(boolean reverse) {
			int n = control.values().size();
			int index = Math.max(0, control.values().indexOf(option.value()));
			for (int step = 1; step <= n; step++) {
				T next = control.values().get(Math.floorMod(index + (reverse ? -step : step), n));
				if (control.allowed().test(next)) {
					option.setValue(next);
					return;
				}
			}
		}
	}
}
