/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/frame/OptionPageFrame.java at commit 38ccea6c, licensed under
 * LGPL-3.0; Celeritas's options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the
 * notice is in META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: a panel inside Altrender's scroll pane (the tooltip goes to the screen's top layer,
 * in screen coordinates); the tooltip also for the keyboard-focused row; unavailable reasons and restart notes.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.options;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionFlag;
import com.coredex.altrender.api.options.OptionGroup;
import com.coredex.altrender.api.options.OptionImpact;
import com.coredex.altrender.api.options.OptionPage;
import com.coredex.altrender.ui.UiRenderer;
import com.coredex.altrender.ui.UiScreen;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;
import com.coredex.altrender.ui.widgets.Panel;
import com.coredex.altrender.ui.widgets.Tooltip;

/** A page's option rows, 18 high, a 4-unit gap after each group; a row's tooltip after half a second's hover. */
public class OptionPagePanel extends Panel {

	private static final int ROW = 18;

	private final List<ControlElement<?>> elements = new ArrayList<>();
	private long lastTime;
	private ControlElement<?> lastHovered;
	private int tooltipLimit = Integer.MAX_VALUE;

	public OptionPagePanel(int width, OptionPage page, Predicate<Option<?>> filter) {
		int y = 0;
		for (OptionGroup group : page.groups()) {
			boolean needPadding = false;
			for (Option<?> option : group.options()) {
				if (!filter.test(option)) {
					continue;
				}
				ControlElement<?> element = ControlElements.create(option, new Dim2i(0, y, width, ROW));
				elements.add(element);
				add(element);
				y += ROW;
				needPadding = true;
			}
			if (needPadding) {
				y += 4;
			}
		}
		setContentHeight(y);
	}

	public List<ControlElement<?>> elements() {
		return elements;
	}

	/** Where tooltips must stay above (the bottom of the visible page, in screen coordinates). */
	public void setTooltipLimit(int screenY) {
		tooltipLimit = screenY;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ControlElement<?> hovered = null;
		if (getCapturedChild() == null) {
			for (ControlElement<?> e : elements) {
				if (e.isMouseOver(mouseX, mouseY)) {
					hovered = e;
					break;
				}
			}
		}
		super.render(ctx, mouseX, mouseY, delta);
		ControlElement<?> focusedElement = null;
		for (ControlElement<?> e : elements) {
			if (e.focused()) {
				focusedElement = e;
			}
		}
		if (hovered != null && lastHovered == hovered) {
			if (lastTime == 0) {
				lastTime = System.currentTimeMillis();
			}
			if (lastTime + 500 <= System.currentTimeMillis()) {
				showTooltip(ctx, hovered);
			}
		} else {
			lastTime = 0;
			lastHovered = hovered;
			if (hovered == null && focusedElement != null) {
				showTooltip(ctx, focusedElement);
			}
		}
	}

	private void showTooltip(DrawContext ctx, ControlElement<?> element) {
		UiScreen screen = UiScreen.current();
		if (screen == null) {
			return;
		}
		Option<?> option = element.option();
		int width = Tooltip.textWidth(element.dim().width());
		List<TextComponent> lines = new ArrayList<>();
		if (option.tooltip() != null) {
			for (String l : ctx.wrap(ctx.extractString(TextComponent.translatable(option.tooltip())), width)) {
				lines.add(TextComponent.literal(l));
			}
		}
		if (option.impact() != null) {
			lines.add(TextComponent.translatable("altrender.options.performance_impact", impactName(option.impact()))
					.withStyle(TextFormattingStyle.GRAY));
		}
		if (option.flags().contains(OptionFlag.REQUIRES_GAME_RESTART)) {
			lines.add(TextComponent.translatable("altrender.options.requires_restart").withStyle(TextFormattingStyle.YELLOW));
		}
		String reason = option.unavailableReason();
		if (reason != null) {
			for (String l : ctx.wrap(ctx.extractString(TextComponent.translatable(reason)), width)) {
				lines.add(TextComponent.literal(l).withStyle(TextFormattingStyle.RED));
			}
		}
		if (lines.isEmpty()) {
			return;
		}
		Dim2i anchor = ctx instanceof UiRenderer r ? r.toScreen(element.dim()) : element.dim();
		screen.tooltip(new Tooltip.Request(lines, anchor, tooltipLimit));
	}

	private static TextComponent impactName(OptionImpact impact) {
		TextComponent name = TextComponent.translatable("altrender.options.impact." + impact.name().toLowerCase());
		return switch (impact) {
			case LOW -> name.withStyle(TextFormattingStyle.GREEN);
			case MEDIUM -> name.withStyle(TextFormattingStyle.YELLOW);
			case HIGH -> name.withStyle(TextFormattingStyle.GOLD);
			case VARIES -> name.withStyle(TextFormattingStyle.WHITE);
		};
	}
}
