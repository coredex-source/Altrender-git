/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.shaderpacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.coredex.altrender.api.shaderpacks.ShaderPackMenu;
import com.coredex.altrender.ui.Theme;
import com.coredex.altrender.ui.UiScreen;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.InteractionContext;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;
import com.coredex.altrender.ui.widgets.Button;
import com.coredex.altrender.ui.widgets.Panel;
import com.coredex.altrender.ui.widgets.ScrollPane;
import com.coredex.altrender.ui.widgets.Tooltip;
import com.coredex.altrender.ui.widgets.Widget;

/**
 * The shader pack screen (development.md 6.3), laid out as Iris's: a title and a line under it; a list in the middle,
 * either the packs ("Shaders: ON/OFF" on top, then the packs, then "Add packs") or the chosen pack's option screens
 * (a header with Back, the screen's name and Reset; the options in the pack's columns: click for the next value,
 * right-click for the one before, Shift+click for the default; links to sub-screens; the profile); and below it
 * "Open Shader Pack Folder" with "Shader Pack Settings..."/"Shader Pack List", then Cancel, Apply and Done. The
 * packs and options are a {@link ShaderPackMenu} from the shader pack addon; nothing is applied before Apply or Done.
 */
public final class ShaderPackScreen extends UiScreen {

	private static final int ROW = 20;
	private static final int GAP = 4;

	private final ShaderPackMenu menu;
	private boolean optionsView;
	/** Sub-screens entered, innermost last; empty: the pack's main screen. */
	private final Deque<String> screens = new ArrayDeque<>();
	private ScrollPane list;
	private double listOffset;

	public ShaderPackScreen(ShaderPackMenu menu) {
		this.menu = menu;
	}

	@Override
	public String title() {
		return "Shader Packs";
	}

	/** Opens on the pack's options ({@code screen}: "main" or a sub-screen's id), for the harness's UI shots. */
	public void showOptions(String screen) {
		optionsView = true;
		screens.clear();
		if (!screen.isEmpty() && !screen.equals("main")) {
			screens.addLast(screen);
		}
	}

	@Override
	protected void build() {
		int rowWidth = Math.min(400, width - 12);
		int x0 = (width - rowWidth) / 2;
		int top = 32;
		int bottom = height - 58;
		Panel content = new Panel();
		ShaderPackMenu.Options options = menu.options();
		if (optionsView && options == null) {
			optionsView = false;
		}
		int h = optionsView ? buildOptions(content, options, rowWidth) : buildList(content, rowWidth);
		content.setContentHeight(h);
		list = add(new ScrollPane(new Dim2i(x0, top, rowWidth, bottom - top), content));
		list.setOffset(listOffset);

		int bottomCenter = width / 2 - 50;
		int topCenter = width / 2 - 76;
		add(new Button(new Dim2i(bottomCenter - 104, height - 27, 100, ROW), TextComponent.literal("Cancel"), () -> {
			menu.cancel();
			host.close(this);
		}));
		Button apply = add(new Button(new Dim2i(bottomCenter, height - 27, 100, ROW), TextComponent.literal("Apply"),
				this::apply));
		apply.setEnabled(menu.changed());
		add(new Button(new Dim2i(bottomCenter + 104, height - 27, 100, ROW), TextComponent.literal("Done"), () -> {
			if (menu.changed()) {
				menu.apply();
			}
			host.close(this);
		}));
		add(new Button(new Dim2i(topCenter - 78, height - 51, 152, ROW), TextComponent.literal("Open Shader Pack Folder"),
				menu::openFolder));
		Button switchView = add(new Button(new Dim2i(topCenter + 78, height - 51, 152, ROW),
				TextComponent.literal(optionsView ? "Shader Pack List" : "Shader Pack Settings..."), () -> {
					optionsView = !optionsView;
					screens.clear();
					go(() -> {
					}, false);
				}));
		switchView.setEnabled(optionsView || options != null);
	}

	private void apply() {
		menu.apply();
		rebuild();
	}

	private void rebuild() {
		listOffset = list != null ? list.offset() : 0;
		init(host, width, height);
	}

	private void go(Runnable change, boolean keepScroll) {
		change.run();
		listOffset = keepScroll && list != null ? list.offset() : 0;
		init(host, width, height);
	}

	// ---- the pack list ------------------------------------------------------------------------------------------

	private int buildList(Panel content, int w) {
		int y = 0;
		boolean on = menu.enabled();
		content.add(new Button(new Dim2i(0, y, w, ROW), TextComponent.literal("Shaders: " + (on ? "ON" : "OFF"))
				.withStyle(on ? TextFormattingStyle.GREEN : TextFormattingStyle.RED),
				() -> go(() -> menu.setEnabled(!menu.enabled()), true)));
		y += ROW + GAP;
		String chosen = menu.pack();
		for (String pack : menu.packs()) {
			boolean isChosen = on && pack.equals(chosen);
			Button b = content.add(new Button(new Dim2i(0, y, w, ROW), TextComponent.literal(pack),
					() -> go(() -> menu.setPack(pack), true)) {
				@Override
				public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
					super.render(ctx, mouseX, mouseY, delta);
					if (isChosen) { // Iris's outline round the chosen pack
						ctx.drawBorder(dim().x(), dim().y(), dim().getLimitX(), dim().getLimitY(), Theme.TEXT);
					}
				}
			});
			if (!on) {
				b.setEnabled(false);
			}
			y += ROW;
		}
		if (menu.packs().isEmpty()) {
			content.add(new Button(new Dim2i(0, y, w, ROW),
					TextComponent.literal("No shader packs: open the folder or add some").withStyle(TextFormattingStyle.GRAY),
					() -> {
					})).setEnabled(false);
			y += ROW;
		}
		y += GAP;
		content.add(new Button(new Dim2i(0, y, w, ROW), TextComponent.literal("+ Add Shader Packs..."), menu::addPacks));
		return y + ROW;
	}

	// ---- the pack's options -------------------------------------------------------------------------------------

	private int buildOptions(Panel content, ShaderPackMenu.Options options, int w) {
		ShaderPackMenu.Screen screen = screens.isEmpty() ? options.main() : options.screen(screens.peekLast());
		if (screen == null) {
			screens.clear();
			screen = options.main();
		}
		int y = 0;
		// Header: Back (in a sub-screen), the name, Reset (Shift).
		if (!screens.isEmpty()) {
			content.add(new Button(new Dim2i(0, y, 60, ROW),
					TextComponent.literal("< Back").withStyle(TextFormattingStyle.ITALIC),
					() -> go(screens::removeLast, false)));
		}
		content.add(new Label(new Dim2i(64, y, w - 128, ROW), screens.isEmpty() ? options.title() : screen.title()));
		Button reset = content.add(new ResetButton(new Dim2i(w - 60, y, 60, ROW), options));
		reset.setEnabled(options.anyChanged());
		y += ROW + GAP;
		int columns = Math.max(1, screen.columns());
		int cellWidth = (w - GAP * (columns - 1)) / columns;
		int col = 0;
		for (ShaderPackMenu.Element e : screen.elements()) {
			int x = col * (cellWidth + GAP);
			Dim2i dim = new Dim2i(x, y, cellWidth, ROW);
			switch (e) {
				case ShaderPackMenu.Empty empty -> {
				}
				case ShaderPackMenu.Link link -> content.add(new Button(dim, TextComponent.literal(link.label() + " >"),
						() -> go(() -> screens.addLast(link.screen()), false)) {
					@Override
					public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
						super.render(ctx, mouseX, mouseY, delta);
						tooltip(ctx, this.dim(), mouseX, mouseY, link.comment(), null);
					}
				});
				case ShaderPackMenu.Choice c when c.slider() && c.values().size() > 1 -> content.add(new SliderCell(dim, c));
				default -> content.add(new OptionCell(dim, e));
			}
			col++;
			if (col == columns) {
				col = 0;
				y += ROW + GAP;
			}
		}
		if (col != 0) {
			y += ROW + GAP;
		}
		return y;
	}

	private int shownRevision = Integer.MIN_VALUE;

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float partialTicks) {
		int revision = menu.revision();
		if (shownRevision != Integer.MIN_VALUE && revision != shownRevision) {
			shownRevision = revision;
			rebuild(); // the pack reloaded (after Apply): its values and screens anew
		}
		shownRevision = revision;
		super.render(ctx, mouseX, mouseY, partialTicks);
		center(ctx, TextComponent.literal("Shader Packs"), 8, Theme.TEXT);
		String problem = menu.problem();
		if (!optionsView && problem != null) {
			String line = problem.length() > 90 ? problem.substring(0, 87) + "..." : problem;
			center(ctx, TextComponent.literal(line).withStyle(TextFormattingStyle.RED), 21, Theme.TEXT);
		} else {
			center(ctx, TextComponent.literal(optionsView ? "Configure Shader Pack" : "Select a Shader Pack")
					.withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.ITALIC), 21, Theme.TEXT);
		}
	}

	private void center(DrawContext ctx, TextComponent text, int y, int color) {
		ctx.drawString(text, width / 2 - ctx.getStringWidth(text) / 2, y, color);
	}

	/** The comment (and how to change it) as a tooltip while the mouse is over {@code dim} (in the list). */
	private void tooltip(DrawContext ctx, Dim2i dim, int mouseX, int mouseY, String comment, String extra) {
		if (!dim.containsCursor(mouseX, mouseY) || (comment == null || comment.isBlank()) && extra == null) {
			return;
		}
		List<TextComponent> lines = new ArrayList<>();
		int wrap = Tooltip.textWidth(dim.width());
		if (comment != null && !comment.isBlank()) {
			for (String para : comment.split("\\\\n|\n")) {
				for (String l : ctx.wrap(para, wrap)) {
					lines.add(TextComponent.literal(l));
				}
			}
		}
		if (extra != null) {
			lines.add(TextComponent.literal(extra).withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.ITALIC));
		}
		Dim2i anchor = ctx instanceof com.coredex.altrender.ui.UiRenderer r ? r.toScreen(dim) : dim;
		tooltip(new Tooltip.Request(lines, anchor, height - 58));
	}

	// ---- cells --------------------------------------------------------------------------------------------------

	/** The header's name. */
	private static final class Label extends Widget {
		private final String text;

		Label(Dim2i dim, String text) {
			super(dim);
			this.text = text;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			TextComponent t = TextComponent.literal(text).withStyle(TextFormattingStyle.UNDERLINE);
			ctx.drawString(t, dim.getCenterX() - ctx.getStringWidth(t) / 2, dim.getCenterY() - 4, Theme.TEXT);
		}

		@Override
		public boolean isFocusable() {
			return false;
		}
	}

	/** Reset: every option to its default, with Shift held (as Iris, so a stray click doesn't). */
	private final class ResetButton extends Button {
		ResetButton(Dim2i dim, ShaderPackMenu.Options options) {
			super(dim, TextComponent.literal("Reset"), () -> {
			});
			this.options = options;
		}

		private final ShaderPackMenu.Options options;

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
			if (enabled() && button == 0 && dim().containsCursor(mouseX, mouseY)) {
				if (context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT)) {
					context.playClickSound();
					go(options::resetAll, true);
				}
				return true;
			}
			return false;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			setLabel(TextComponent.literal("Reset")
					.withStyle(enabled() ? TextFormattingStyle.YELLOW : TextFormattingStyle.GRAY));
			super.render(ctx, mouseX, mouseY, delta);
			tooltip(ctx, dim(), mouseX, mouseY, null, enabled() ? "Shift+click: every option back to its default" : null);
		}
	}

	/** An option or the profile: "Name: Value"; click next, right-click before, Shift+click default. */
	private final class OptionCell extends Widget {
		private final ShaderPackMenu.Element element;
		private final Button.Style style = Button.Style.defaults();

		OptionCell(Dim2i dim, ShaderPackMenu.Element element) {
			super(dim);
			this.element = element;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			boolean hovered = dim.containsCursor(mouseX, mouseY);
			ctx.fill(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), hovered ? style.bgHovered : style.bgDefault);
			String name;
			TextComponent value;
			String comment = null;
			switch (element) {
				case ShaderPackMenu.Toggle t -> {
					name = t.label();
					boolean v = t.value().get();
					TextComponent text = TextComponent.literal(v ? "ON" : "OFF");
					value = v == t.defaultValue() ? text
							: text.withStyle(v ? TextFormattingStyle.GREEN : TextFormattingStyle.RED);
					comment = t.comment();
				}
				case ShaderPackMenu.Choice c -> {
					name = c.label();
					String v = c.value().get();
					TextComponent text = TextComponent.literal(c.valueLabel().apply(v));
					value = v.equals(c.defaultValue()) ? text : text.withStyle(TextFormattingStyle.YELLOW);
					comment = c.comment();
				}
				case ShaderPackMenu.Profile p -> {
					name = "Profile";
					String current = p.current().get();
					value = current == null ? TextComponent.literal("Custom").withStyle(TextFormattingStyle.YELLOW)
							: TextComponent.literal(p.label().apply(current));
				}
				default -> {
					name = "";
					value = TextComponent.literal("");
				}
			}
			int valueWidth = ctx.getStringWidth(value);
			String label = name + ": ";
			int room = dim.width() - 8 - valueWidth;
			while (label.length() > 3 && ctx.getStringWidth(label) > room) {
				label = label.substring(0, label.length() - 4) + "…: ";
			}
			TextComponent labelText = TextComponent.literal(label);
			int labelWidth = ctx.getStringWidth(labelText);
			int x = dim.getCenterX() - (labelWidth + valueWidth) / 2;
			ctx.drawString(labelText, x, dim.getCenterY() - 4, style.textDefault);
			ctx.drawString(value, x + labelWidth, dim.getCenterY() - 4, style.textDefault);
			if (focused) {
				ctx.drawBorder(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), Theme.TEXT);
			}
			tooltip(ctx, dim, mouseX, mouseY, comment, element instanceof ShaderPackMenu.Profile ? null
					: "Click: next value; right-click: the one before; Shift+click: default");
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
			if (!dim.containsCursor(mouseX, mouseY) || button > 1) {
				return false;
			}
			boolean back = button == 1;
			boolean reset = context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT);
			switch (element) {
				case ShaderPackMenu.Toggle t -> t.set().accept(reset ? t.defaultValue() : !t.value().get());
				case ShaderPackMenu.Choice c -> c.set().accept(reset ? c.defaultValue()
						: step(c.values(), c.value().get(), back));
				case ShaderPackMenu.Profile p -> {
					if (!p.names().isEmpty()) {
						String current = p.current().get();
						p.select().accept(current == null ? p.names().get(back ? p.names().size() - 1 : 0)
								: step(p.names(), current, back));
					}
				}
				default -> {
					return false;
				}
			}
			context.playClickSound();
			go(() -> {
			}, true);
			return true;
		}

		@Override
		public boolean isFocusable() {
			return true;
		}
	}

	/** A slider option (the pack's {@code sliders}): drag or click along it; Shift+click for the default. */
	private final class SliderCell extends Widget {
		private final ShaderPackMenu.Choice choice;
		private final Button.Style style = Button.Style.defaults();
		private boolean dragging;

		SliderCell(Dim2i dim, ShaderPackMenu.Choice choice) {
			super(dim);
			this.choice = choice;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			boolean hovered = dim.containsCursor(mouseX, mouseY);
			ctx.fill(dim.x(), dim.y(), dim.getLimitX(), dim.getLimitY(), hovered ? style.bgHovered : style.bgDefault);
			List<String> values = choice.values();
			String v = choice.value().get();
			int i = Math.max(0, values.indexOf(v));
			int knob = 6;
			int x = dim.x() + (int) Math.round((dim.width() - knob) * (i / (double) (values.size() - 1)));
			ctx.fill(x, dim.y(), x + knob, dim.getLimitY(), hovered || dragging ? Theme.TEXT : Theme.TEXT_GRAY);
			TextComponent value = TextComponent.literal(choice.valueLabel().apply(v));
			if (!v.equals(choice.defaultValue())) {
				value = value.withStyle(TextFormattingStyle.YELLOW);
			}
			TextComponent label = TextComponent.literal(choice.label() + ": ");
			int lw = ctx.getStringWidth(label);
			int vw = ctx.getStringWidth(value);
			int tx = dim.getCenterX() - (lw + vw) / 2;
			ctx.drawString(label, tx, dim.getCenterY() - 4, style.textDefault);
			ctx.drawString(value, tx + lw, dim.getCenterY() - 4, style.textDefault);
			tooltip(ctx, dim, mouseX, mouseY, choice.comment(), "Drag or click; Shift+click: default");
		}

		private void pick(double mouseX) {
			List<String> values = choice.values();
			double t = (mouseX - dim.x()) / Math.max(1, dim.width());
			int i = (int) Math.round(Math.max(0, Math.min(1, t)) * (values.size() - 1));
			if (!values.get(i).equals(choice.value().get())) {
				choice.set().accept(values.get(i));
			}
		}

		@Override
		public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
			if (button != 0 || !dim.containsCursor(mouseX, mouseY)) {
				return false;
			}
			if (context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT)) {
				choice.set().accept(choice.defaultValue());
				context.playClickSound();
				go(() -> {
				}, true);
				return true;
			}
			dragging = true;
			pick(mouseX);
			return true;
		}

		@Override
		public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double dx,
				double dy) {
			if (dragging) {
				pick(mouseX);
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
			if (dragging) {
				dragging = false;
				go(() -> {
				}, true); // Apply and Reset follow the change
				return true;
			}
			return false;
		}

		@Override
		public boolean isFocusable() {
			return true;
		}
	}

	private static String step(List<String> values, String current, boolean back) {
		int i = values.indexOf(current);
		int n = values.size();
		if (n == 0) {
			return current;
		}
		int next = i < 0 ? 0 : Math.floorMod(i + (back ? -1 : 1), n);
		return values.get(next);
	}
}
