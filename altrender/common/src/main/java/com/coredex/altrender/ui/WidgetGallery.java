/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionFlag;
import com.coredex.altrender.api.options.OptionGroup;
import com.coredex.altrender.api.options.OptionImpact;
import com.coredex.altrender.api.options.OptionPage;
import com.coredex.altrender.api.options.OptionPageProvider;
import com.coredex.altrender.api.options.OptionStorage;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.Key;
import com.coredex.altrender.ui.options.ControlElement;
import com.coredex.altrender.ui.options.OptionsScreen;
import com.coredex.altrender.ui.widgets.Button;
import com.coredex.altrender.ui.widgets.ScrollPane;

/**
 * The toolkit's check screen (development.md 5.1; dev only, {@code -Daltrender.debug.uiGallery}): the options screen
 * with demo pages that use every control and state, and {@link #selfTest} driving them with synthetic input.
 */
public final class WidgetGallery extends OptionsScreen {

	private static final State STATE = new State();

	/** The demo options' values and how often they were saved. */
	private static final class State {
		boolean tick = true;
		boolean tick2;
		int distance = 12;
		int percent = 50;
		String graphics = "Fancy";
		final boolean[] longTicks = new boolean[30];
		int saves;
	}

	private static final OptionStorage STORAGE = flags -> STATE.saves++;

	public WidgetGallery() {
		super(List.of(provider("altrender", "Altrender (gallery)", WidgetGallery::controlsPages),
				provider("othermod", "Another Mod", WidgetGallery::otherPages)), flags -> {
				});
	}

	private static OptionPageProvider provider(String id, String name,
			java.util.function.Supplier<List<OptionPage>> pages) {
		return new OptionPageProvider() {
			@Override
			public String modId() {
				return id;
			}

			@Override
			public String modName() {
				return name;
			}

			@Override
			public List<OptionPage> pages() {
				return pages.get();
			}
		};
	}

	private static List<OptionPage> controlsPages() {
		Option<Boolean> tick = Option.<Boolean>builder("tick").name("Tick box (on)")
				.tooltip("A tick box. Clicking it, Space or Enter turns it on or off; the change waits for Apply.")
				.impact(OptionImpact.LOW).control(new Control.TickBox()).binding(() -> STATE.tick, v -> STATE.tick = v)
				.storage(STORAGE).build();
		Option<Boolean> tick2 = Option.<Boolean>builder("tick2").name("Tick box (off)")
				.tooltip("Another tick box.").control(new Control.TickBox())
				.binding(() -> STATE.tick2, v -> STATE.tick2 = v).storage(STORAGE).build();
		Option<Boolean> locked = Option.<Boolean>builder("locked").name("Unavailable option")
				.tooltip("Shown struck through; the reason is in its tooltip.").control(new Control.TickBox())
				.binding(() -> true, v -> {
				}).storage(STORAGE).available(() -> false, () -> "Needs a GL 4.5 driver.").build();
		Option<Integer> distance = Option.<Integer>builder("distance").name("Slider")
				.tooltip("A slider: hover it to see the track; drag, click, or Left/Right.")
				.impact(OptionImpact.HIGH).control(new Control.Slider(2, 32, 1, v -> v + " chunks"))
				.binding(() -> STATE.distance, v -> STATE.distance = v).storage(STORAGE).build();
		Option<Integer> percent = Option.<Integer>builder("percent").name("Slider (percent)")
				.tooltip("Steps of 10.").impact(OptionImpact.MEDIUM)
				.control(new Control.Slider(0, 100, 10, v -> v + "%"))
				.binding(() -> STATE.percent, v -> STATE.percent = v).storage(STORAGE)
				.flags(OptionFlag.REQUIRES_GAME_RESTART).build();
		Option<String> graphics = Option.<String>builder("graphics").name("Cycling")
				.tooltip("Clicks through its values (Shift: backwards); \"Fabulous\" isn't available here and is skipped.")
				.impact(OptionImpact.VARIES)
				.control(new Control.Cycling<>(List.of("Fast", "Fancy", "Fabulous"), v -> v, v -> !v.equals("Fabulous")))
				.binding(() -> STATE.graphics, v -> STATE.graphics = v).storage(STORAGE).build();
		OptionPage controls = new OptionPage("gallery.controls", "Controls", List.of(
				OptionGroup.of(tick, tick2, locked), OptionGroup.of(distance, percent), OptionGroup.of(graphics)));
		List<Option<?>> many = new ArrayList<>();
		for (int i = 0; i < STATE.longTicks.length; i++) {
			int n = i;
			many.add(Option.<Boolean>builder("long" + i).name("Option " + (i + 1)).tooltip("Row " + (i + 1) + " of 30.")
					.control(new Control.TickBox()).binding(() -> STATE.longTicks[n], v -> STATE.longTicks[n] = v)
					.storage(STORAGE).build());
		}
		OptionPage longPage = new OptionPage("gallery.long", "Long page", List.of(OptionGroup.of(many.subList(0, 10)),
				OptionGroup.of(many.subList(10, 20)), OptionGroup.of(many.subList(20, 30))));
		return List.of(controls, longPage);
	}

	private static List<OptionPage> otherPages() {
		Option<Boolean> extra = Option.<Boolean>builder("extra").name("An option from another mod")
				.tooltip("Pages other mods register appear under their own header.").control(new Control.TickBox())
				.binding(() -> STATE.tick2, v -> STATE.tick2 = v).storage(STORAGE).build();
		return List.of(new OptionPage("othermod.page", "Its page", List.of(OptionGroup.of(extra))));
	}

	/**
	 * Drives the screen with synthetic input at the current layout and checks each part reacted (the harness runs it
	 * at every GUI scale). Returns what failed; it leaves the demo values as it found them.
	 */
	public List<String> selfTest() {
		List<String> failed = new ArrayList<>();
		undoChanges();
		selectTab(0);
		// Tick box: a click makes a pending change; Apply puts it in effect and saves once.
		ControlElement<?> tick = element("tick");
		boolean before = STATE.tick;
		click(tick);
		check(failed, tick.option().hasChanged() && STATE.tick == before, "tick box click is pending");
		check(failed, hasPendingChanges(), "pending changes noticed");
		int saves = STATE.saves;
		applyChanges();
		check(failed, STATE.tick != before && STATE.saves == saves + 1, "apply (" + STATE.tick + ", " + STATE.saves + ")");
		click(element("tick"));
		applyChanges(); // back
		// Unavailable: nothing happens.
		click(element("locked"));
		check(failed, !element("locked").option().hasChanged(), "unavailable ignores clicks");
		// Slider: a click at three quarters of its track; Undo drops it.
		ControlElement<?> slider = element("distance");
		Dim2i d = screen(slider.dim());
		double trackX = d.getLimitX() - 96 + 90 * 0.75;
		onMouseClicked(trackX, d.getCenterY(), 0);
		onMouseReleased(trackX, d.getCenterY(), 0);
		Object v = slider.option().value();
		check(failed, v instanceof Integer i && Math.abs(i - (2 + 0.75 * 30)) <= 1, "slider click (" + v + ")");
		undoChanges();
		check(failed, !slider.option().hasChanged(), "undo");
		// Cycling: Fancy → (Fabulous skipped) → Fast.
		click(element("graphics"));
		check(failed, "Fast".equals(element("graphics").option().value()), "cycling skips unavailable ("
				+ element("graphics").option().value() + ")");
		undoChanges();
		// Keyboard: Tab focuses; Space on a focused tick box changes it.
		setFocus(null);
		onKey(Key.TAB, 0);
		check(failed, focused() != null, "tab focuses");
		setFocus(element("tick2"));
		onKey(Key.SPACE, 0);
		check(failed, element("tick2").option().hasChanged(), "space on focused tick box");
		undoChanges();
		setFocus(null);
		// Search: typing filters the page; clearing it brings everything back.
		Dim2i s = searchField().dim();
		onMouseClicked(s.getCenterX(), s.getCenterY(), 0);
		check(failed, focused() == searchField(), "search focus on click");
		for (char c : "slider".toCharArray()) {
			onChar(c);
		}
		check(failed, page() != null && page().elements().size() == 2, "search filters ("
				+ (page() == null ? "-" : page().elements().size()) + " rows)");
		onKey(Key.A, Key.Mods.CTRL);
		onKey(Key.BACKSPACE, 0);
		check(failed, page() != null && page().elements().size() == 6, "search cleared ("
				+ (page() == null ? "-" : page().elements().size()) + " rows)");
		onKey(Key.ESCAPE, 0);
		// Tabs: the second page; the wheel and the scroll bar scroll it when it's taller than the view.
		selectTab(1);
		check(failed, page() != null && page().elements().size() == 30, "tab switch");
		ScrollPane pane = pagePane();
		if (pane != null && pane.canScroll()) {
			Dim2i p = pane.dim();
			onMouseScrolled(p.getCenterX(), p.getCenterY(), -1);
			check(failed, pane.target() > 0, "wheel scrolls the page");
			pane.setOffset(0);
			onMouseClicked(p.getLimitX() - 3, p.y() + 2, 0);
			onMouseDragged(p.getLimitX() - 3, p.getLimitY() + 400, 0, 0, 400);
			onMouseReleased(p.getLimitX() - 3, p.getLimitY() + 400, 0);
			check(failed, pane.offset() > 0, "scroll bar drag");
			pane.setOffset(0);
		}
		selectTab(0);
		return failed;
	}

	/** For screenshots: the slider row focused, so its track and tooltip show without a cursor. */
	public void showcase() {
		setFocus(element("distance"));
	}

	private void selectTab(int index) {
		Button tab = tabButtons().get(index);
		Dim2i side = sidebarPane().dim();
		Dim2i t = tab.dim();
		onMouseClicked(side.x() + t.getCenterX(), side.y() + t.getCenterY() - sidebarPane().offset(), 0);
	}

	private ControlElement<?> element(String id) {
		for (ControlElement<?> e : page().elements()) {
			if (e.option().id().equals(id)) {
				return e;
			}
		}
		throw new IllegalStateException("no option " + id);
	}

	/** An element's rectangle on screen (it lives in the page's scrolled panel). */
	private Dim2i screen(Dim2i d) {
		Dim2i p = pagePane().dim();
		return d.offset(p.x(), p.y() - (int) Math.round(pagePane().offset()));
	}

	private void click(ControlElement<?> e) {
		Dim2i d = screen(e.dim());
		onMouseClicked(d.getCenterX(), d.getCenterY(), 0);
		onMouseReleased(d.getCenterX(), d.getCenterY(), 0);
	}

	private static void check(List<String> failed, boolean ok, String what) {
		if (!ok) {
			failed.add(what);
		}
	}
}
