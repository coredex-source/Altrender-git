/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.widgets;

import java.util.function.Consumer;

import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.FontMetricsProvider;
import com.coredex.altrender.ui.framework.InteractionContext;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.framework.TextComponent;

/**
 * One line of editable text, drawn as Sodium's search bar: click to focus and place the cursor; Shift selects, Ctrl
 * moves by words; Ctrl+A/C/X/V; the text scrolls to keep the cursor in view. Written for Altrender.
 */
public class TextField extends Widget {

	private final Consumer<String> onChange;
	private final int maxLength;
	private String text = "";
	private TextComponent placeholder;
	private int cursor;
	private int anchor;
	/** Index of the first visible character. */
	private int scroll;
	/** The font the text was last laid out with (set while rendering). */
	private FontMetricsProvider font;

	public TextField(Dim2i dim, int maxLength, Consumer<String> onChange) {
		super(dim);
		this.maxLength = maxLength;
		this.onChange = onChange;
	}

	public TextField placeholder(TextComponent placeholder) {
		this.placeholder = placeholder;
		return this;
	}

	public String text() {
		return text;
	}

	public void setText(String value) {
		text = filter(value);
		if (text.length() > maxLength) {
			text = text.substring(0, maxLength);
		}
		cursor = anchor = text.length();
		scroll = 0;
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			anchor = cursor;
		}
	}

	private int innerX() {
		return dim.x() + 6;
	}

	private int innerWidth() {
		return dim.width() - 12;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Sodium's search bar: a flat box, darker while focused; a blue selection, a light-grey cursor.
		font = ctx;
		ctx.fill(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), focused ? Theme.BG_FOCUSED : Theme.BG);
		int textX = innerX();
		int textY = dim.getCenterY() - 4;
		if (text.isEmpty() && placeholder != null) {
			ctx.drawString(placeholder, textX, textY, Theme.TEXT_GRAY);
		}
		keepCursorVisible(ctx);
		String visible = ctx.substrByWidth(text.substring(scroll), innerWidth());
		int lineTop = textY - 1;
		int lineBottom = textY + 1 + ctx.lineHeight();
		int cursorX = textX + ctx.getStringWidth(text.substring(scroll, Math.max(scroll, Math.min(cursor,
				scroll + visible.length()))));
		if (focused && anchor != cursor) {
			int a = Math.max(scroll, Math.min(anchor, cursor));
			int b = Math.min(scroll + visible.length(), Math.max(anchor, cursor));
			if (b > a) {
				int sx = textX + ctx.getStringWidth(text.substring(scroll, a));
				int ex = textX + ctx.getStringWidth(text.substring(scroll, b));
				ctx.fill(sx, lineTop, Math.min(ex, dim.getLimitX()), lineBottom, 0xFF0000FF);
			}
		}
		if (!visible.isEmpty()) {
			ctx.drawString(TextComponent.literal(visible), textX, textY, enabled ? Theme.TEXT : Theme.TEXT_DISABLED);
		}
		if (focused) {
			ctx.fill(cursorX, lineTop, cursorX + 1, lineBottom, 0xFFD0D0D0);
		}
	}

	private void keepCursorVisible(FontMetricsProvider f) {
		if (cursor < scroll) {
			scroll = cursor;
		}
		while (scroll < cursor && f.getStringWidth(text.substring(scroll, cursor)) > innerWidth()) {
			scroll++;
		}
	}

	@Override
	public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !dim.containsCursor(mouseX, mouseY)) {
			return false;
		}
		if (font != null) {
			int target = (int) mouseX - innerX();
			int i = scroll;
			while (i < text.length()) {
				int before = font.getStringWidth(text.substring(scroll, i));
				int after = font.getStringWidth(text.substring(scroll, i + 1));
				if (target < (before + after) / 2) {
					break;
				}
				i++;
			}
			cursor = anchor = i;
		}
		com.coredex.altrender.ui.UiScreen screen = com.coredex.altrender.ui.UiScreen.current();
		if (screen != null) {
			screen.requestFocus(this);
		}
		return true;
	}

	@Override
	public boolean keyPressed(InteractionContext context, Key key, int mods) {
		if (!enabled) {
			return false;
		}
		boolean shift = (mods & Key.Mods.SHIFT) != 0;
		boolean ctrl = (mods & Key.Mods.CTRL) != 0;
		switch (key) {
			case LEFT -> move(ctrl ? wordBoundary(-1) : cursor - 1, shift);
			case RIGHT -> move(ctrl ? wordBoundary(1) : cursor + 1, shift);
			case HOME -> move(0, shift);
			case END -> move(text.length(), shift);
			case BACKSPACE -> {
				if (anchor == cursor) {
					anchor = ctrl ? wordBoundary(-1) : Math.max(0, cursor - 1);
				}
				insert("");
			}
			case DELETE -> {
				if (anchor == cursor) {
					anchor = ctrl ? wordBoundary(1) : Math.min(text.length(), cursor + 1);
				}
				insert("");
			}
			case A -> {
				if (!ctrl) {
					return false;
				}
				anchor = 0;
				cursor = text.length();
			}
			case C, X -> {
				if (!ctrl) {
					return false;
				}
				if (anchor != cursor) {
					context.setClipboard(text.substring(Math.min(anchor, cursor), Math.max(anchor, cursor)));
					if (key == Key.X) {
						insert("");
					}
				}
			}
			case V -> {
				if (!ctrl) {
					return false;
				}
				insert(context.getClipboard());
			}
			case ENTER, ESCAPE, TAB -> {
				return false;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(InteractionContext context, char chr) {
		if (!enabled || !allowed(chr)) {
			return false;
		}
		insert(String.valueOf(chr));
		return true;
	}

	private void move(int to, boolean select) {
		cursor = Math.max(0, Math.min(text.length(), to));
		if (!select) {
			anchor = cursor;
		}
	}

	/** Replaces the selection (or inserts at the cursor) with {@code s}. */
	private void insert(String s) {
		int a = Math.min(anchor, cursor);
		int b = Math.max(anchor, cursor);
		String add = filter(s);
		int room = maxLength - (text.length() - (b - a));
		if (add.length() > room) {
			add = add.substring(0, Math.max(0, room));
		}
		String next = text.substring(0, a) + add + text.substring(b);
		cursor = anchor = a + add.length();
		if (!next.equals(text)) {
			text = next;
			onChange.accept(text);
		}
	}

	/** The next word start in {@code direction} from the cursor (spaces skipped). */
	private int wordBoundary(int direction) {
		int i = cursor;
		if (direction < 0) {
			while (i > 0 && text.charAt(i - 1) == ' ') {
				i--;
			}
			while (i > 0 && text.charAt(i - 1) != ' ') {
				i--;
			}
		} else {
			while (i < text.length() && text.charAt(i) != ' ') {
				i++;
			}
			while (i < text.length() && text.charAt(i) == ' ') {
				i++;
			}
		}
		return i;
	}

	private static boolean allowed(char c) {
		return c >= ' ' && c != 127 && c != '§';
	}

	private static String filter(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			if (allowed(s.charAt(i))) {
				b.append(s.charAt(i));
			}
		}
		return b.toString();
	}
}
