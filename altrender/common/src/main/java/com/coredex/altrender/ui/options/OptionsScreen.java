/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/{CeleritasVideoOptionsController,frame/tab/TabFrame,
 * frame/tab/TabHeaderWidget}.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's options GUI derives from
 * Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: one UiScreen on Altrender's toolkit and option API (pages come from
 * OptionPageProviders, one side-bar header per mod); the search matches option names and value names (Celeritas's
 * fuzzy search and text-field model aren't taken); the applied options' flags go to a host callback.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.options;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionFlag;
import com.coredex.altrender.api.options.OptionPage;
import com.coredex.altrender.api.options.OptionPageProvider;
import com.coredex.altrender.api.options.OptionStorage;
import com.coredex.altrender.ui.UiScreen;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;
import com.coredex.altrender.ui.widgets.Button;
import com.coredex.altrender.ui.widgets.Panel;
import com.coredex.altrender.ui.widgets.ScrollPane;
import com.coredex.altrender.ui.widgets.TextField;

/**
 * Altrender's video settings (development.md 5.2): Reese's Sodium Options' layout on the toolkit. A search bar on
 * top; a side bar with each mod's pages under its name; the selected page's options, scrolling; Undo, Apply and Done
 * at the bottom right (Done waits until changes are applied or undone).
 */
public class OptionsScreen extends UiScreen {

	private static final float ASPECT_RATIO = 5f / 4f;
	private static final int MINIMUM_WIDTH = 550;
	private static final int TAB_OPTION_INDENT = 5;
	private static final int TAB_HEIGHT = 18;
	/** The page shown last (by id), kept across openings as Sodium keeps it. */
	private static String selectedPageId;

	/** A page under its mod's header. */
	public record Entry(String modId, String modName, OptionPage page) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private final Consumer<Set<OptionFlag>> applyEffects;
	private String query = "";
	private TextField search;
	private Button undoButton;
	private Button applyButton;
	private Button closeButton;
	private ScrollPane pagePane;
	private OptionPagePanel pagePanel;
	private double sidebarOffset;
	private ScrollPane sidebarPane;
	private final List<Button> tabButtons = new ArrayList<>();

	/**
	 * @param applyEffects does what the applied options' flags ask (rebuild terrain, reload textures, lay out again);
	 *                     called once per Apply, after storages saved
	 */
	public OptionsScreen(List<OptionPageProvider> providers, Consumer<Set<OptionFlag>> applyEffects) {
		this.applyEffects = applyEffects;
		for (OptionPageProvider p : providers) {
			for (OptionPage page : p.pages()) {
				entries.add(new Entry(p.modId(), p.modName(), page));
			}
		}
	}

	@Override
	public String title() {
		return host.translate("altrender.options.title");
	}

	@Override
	protected void build() {
		int newWidth = width;
		if (newWidth > MINIMUM_WIDTH && (float) width / height > ASPECT_RATIO) {
			newWidth = Math.max(MINIMUM_WIDTH, (int) (height * ASPECT_RATIO));
		}
		Dim2i frame = new Dim2i((width - newWidth) / 2, 0, newWidth, height);
		Dim2i tabFrame = new Dim2i(frame.x() + frame.width() / 20 / 2, frame.y() + frame.height() / 4 / 2,
				frame.width() - frame.width() / 20, frame.height() / 4 * 3);
		Dim2i undoDim = new Dim2i(tabFrame.getLimitX() - 203, tabFrame.getLimitY() + 5, 65, 20);
		Dim2i applyDim = new Dim2i(tabFrame.getLimitX() - 134, tabFrame.getLimitY() + 5, 65, 20);
		Dim2i closeDim = new Dim2i(tabFrame.getLimitX() - 65, tabFrame.getLimitY() + 5, 65, 20);
		Dim2i searchDim = new Dim2i(tabFrame.x(), tabFrame.y() - 26, tabFrame.width(), 20);

		// The search field outlives layouts (its text, cursor and focus survive a re-layout while typing).
		if (search == null) {
			search = new TextField(searchDim, 100, this::onSearch);
			search.placeholder(TextComponent.translatable("altrender.options.search"));
		} else {
			search.setDim(searchDim);
		}
		add(search);
		buildTabs(tabFrame);
		undoButton = add(new Button(undoDim, TextComponent.translatable("altrender.options.undo"), this::undoChanges));
		applyButton = add(new Button(applyDim, TextComponent.translatable("altrender.options.apply"), this::applyChanges));
		closeButton = add(new Button(closeDim, TextComponent.translatable("gui.done"), this::onClose));
		// A shader pack addon's screen (Iris's place: a button next to the video settings' own).
		com.coredex.altrender.api.shaderpacks.ShaderPackMenu shaderPacks = com.coredex.altrender.api.AltrenderApi
				.shaderPackMenu();
		if (shaderPacks != null) {
			add(new Button(new Dim2i(tabFrame.x(), tabFrame.getLimitY() + 5, 120, 20),
					TextComponent.literal("Shader Packs..."),
					() -> host.open(new com.coredex.altrender.ui.shaderpacks.ShaderPackScreen(shaderPacks))));
		}
	}

	private void buildTabs(Dim2i dim) {
		List<Entry> visible = new ArrayList<>();
		for (Entry e : entries) {
			if (canShow(e.page())) {
				visible.add(e);
			}
		}
		Entry selected = null;
		for (Entry e : visible) {
			if (e.page().id().equals(selectedPageId)) {
				selected = e;
			}
		}
		if (selected == null && !visible.isEmpty()) {
			selected = visible.get(0);
		}
		Set<String> mods = new LinkedHashSet<>();
		for (Entry e : visible) {
			mods.add(e.modId());
		}
		// The side bar: as wide as its longest mod name or page title, + 24.
		int longest = 0;
		for (Entry e : visible) {
			longest = Math.max(longest, textWidth(e.modName()) + 10);
			longest = Math.max(longest, textWidth(e.page().name()) + TAB_OPTION_INDENT);
		}
		int sidebarWidth = visible.isEmpty() ? (int) (dim.width() * 0.35) : longest + 24;
		Dim2i tabSection = new Dim2i(dim.x(), dim.y(), sidebarWidth, dim.height());
		Dim2i frameSection = new Dim2i(tabSection.getLimitX(), dim.y(), dim.width() - sidebarWidth, dim.height());

		Panel sidebar = new Panel();
		tabButtons.clear();
		int offsetY = 0;
		int tabWidth = sidebarWidth - 4;
		for (String mod : mods) {
			String modName = null;
			for (Entry e : visible) {
				if (e.modId().equals(mod)) {
					modName = e.modName();
				}
			}
			Button header = new HeaderButton(new Dim2i(0, offsetY, tabWidth, TAB_HEIGHT),
					TextComponent.translatable(modName).withStyle(TextFormattingStyle.UNDERLINE));
			header.setLeftAligned(true);
			sidebar.add(header);
			offsetY += TAB_HEIGHT;
			for (Entry e : visible) {
				if (!e.modId().equals(mod)) {
					continue;
				}
				Button tab = new TabButton(new Dim2i(0, offsetY, tabWidth, TAB_HEIGHT),
						TextComponent.translatable(e.page().name()), () -> selectPage(e));
				tab.setSelected(e == selected);
				tab.setLeftAligned(true);
				sidebar.add(tab);
				tabButtons.add(tab);
				offsetY += TAB_HEIGHT;
			}
		}
		// The shader pack screen also from the side bar (where Sodium puts Iris's), when an addon registered one.
		com.coredex.altrender.api.shaderpacks.ShaderPackMenu packs = com.coredex.altrender.api.AltrenderApi.shaderPackMenu();
		if (packs != null) {
			offsetY += 6;
			Button shaderTab = new TabButton(new Dim2i(0, offsetY, tabWidth, TAB_HEIGHT),
					TextComponent.literal("Shader Packs..."),
					() -> host.open(new com.coredex.altrender.ui.shaderpacks.ShaderPackScreen(packs)));
			shaderTab.setLeftAligned(true);
			sidebar.add(shaderTab);
			offsetY += TAB_HEIGHT;
		}
		sidebar.setContentHeight(offsetY);
		sidebarPane = add(new ScrollPane(tabSection, sidebar));
		sidebarPane.setOffset(sidebarOffset);

		if (selected != null) {
			double pageOffset = pagePane != null && selected.page().id().equals(selectedPageId) ? pagePane.offset() : 0;
			selectedPageId = selected.page().id();
			pagePanel = new OptionPagePanel(frameSection.width() - 11, selected.page(), filter());
			pagePanel.setTooltipLimit(frameSection.getLimitY());
			pagePane = add(new ScrollPane(frameSection, pagePanel));
			pagePane.setOffset(pageOffset);
		} else {
			pagePanel = null;
			pagePane = null;
		}
	}

	private int textWidth(String key) {
		return key == null ? 0 : com.coredex.altrender.ui.UiRenderer.width(host.font(), host.translate(key));
	}

	private void selectPage(Entry e) {
		selectedPageId = e.page().id();
		relayout(true);
	}

	/** Lays the screen out again (same size), keeping the side bar's scroll and, unless asked, the page's. */
	private void relayout(boolean resetPage) {
		if (pagePane != null && resetPage) {
			pagePane = null;
		}
		if (sidebarPane != null) {
			sidebarOffset = sidebarPane.offset();
		}
		init(host, width, height);
	}

	private void onSearch(String text) {
		query = text;
		pagePane = null;
		relayout(true);
		setFocus(search);
	}

	/** Whether an option matches the search: every word in its name or one of its value names. */
	private Predicate<Option<?>> filter() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return o -> true;
		}
		String[] words = q.split("\\s+");
		return o -> {
			StringBuilder text = new StringBuilder(host.translate(o.name()).toLowerCase(Locale.ROOT));
			if (o.control() instanceof Control.Cycling<?> c) {
				appendNames(text, c);
			}
			String s = text.toString();
			for (String w : words) {
				if (!s.contains(w)) {
					return false;
				}
			}
			return true;
		};
	}

	private <T> void appendNames(StringBuilder text, Control.Cycling<T> c) {
		for (T v : c.values()) {
			text.append(' ').append(host.translate(c.names().apply(v)).toLowerCase(Locale.ROOT));
		}
	}

	private boolean canShow(OptionPage page) {
		if (page.groups().isEmpty()) {
			return false;
		}
		Predicate<Option<?>> f = filter();
		for (Option<?> o : page.options()) {
			if (f.test(o)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float partialTicks) {
		updateControls();
		super.render(ctx, mouseX, mouseY, partialTicks);
	}

	private void updateControls() {
		boolean changes = hasPendingChanges();
		applyButton.setEnabled(changes);
		undoButton.setVisible(changes);
		closeButton.setEnabled(!changes);
	}

	public boolean hasPendingChanges() {
		for (Entry e : entries) {
			for (Option<?> o : e.page().options()) {
				if (o.hasChanged()) {
					return true;
				}
			}
		}
		return false;
	}

	/** Puts every pending change into effect, saves each storage that had one, then the flags' side effects. */
	public void applyChanges() {
		Set<OptionStorage> dirty = new LinkedHashSet<>();
		EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
		for (Entry e : entries) {
			for (Option<?> o : e.page().options()) {
				if (!o.hasChanged()) {
					continue;
				}
				o.applyChanges();
				flags.addAll(o.flags());
				dirty.add(o.storage());
			}
		}
		for (OptionStorage s : dirty) {
			s.save(flags);
		}
		if (!flags.isEmpty()) {
			applyEffects.accept(flags);
		}
	}

	public void undoChanges() {
		for (Entry e : entries) {
			for (Option<?> o : e.page().options()) {
				o.reset();
			}
		}
	}

	/** The options of the page shown (tests). */
	public OptionPagePanel page() {
		return pagePanel;
	}

	public ScrollPane pagePane() {
		return pagePane;
	}

	public TextField searchField() {
		return search;
	}

	/** Shows the page with {@code id} (tests, scripted checks); false if there's none. */
	public boolean selectPage(String id) {
		for (Entry e : entries) {
			if (e.page().id().equals(id)) {
				selectPage(e);
				return true;
			}
		}
		return false;
	}

	/**
	 * Clicks option {@code id} on the page shown, as the mouse would (scrolled into view first; tests); false if it's
	 * not on the page.
	 */
	public boolean clickOption(String id) {
		if (pagePanel == null) {
			return false;
		}
		for (ControlElement<?> e : pagePanel.elements()) {
			if (e.option().id().equals(id)) {
				pagePane.setOffset(Math.max(0, e.dim().y() - 4));
				Dim2i p = pagePane.dim();
				double x = p.x() + e.dim().getCenterX();
				double y = p.y() + e.dim().getCenterY() - pagePane.offset();
				onMouseClicked(x, y, 0);
				onMouseReleased(x, y, 0);
				return true;
			}
		}
		return false;
	}

	/** The side bar's page buttons, in order, and the pane they scroll in (tests). */
	public List<Button> tabButtons() {
		return tabButtons;
	}

	public ScrollPane sidebarPane() {
		return sidebarPane;
	}

	public List<Entry> entries() {
		return entries;
	}

	/** A mod's header in the side bar: not clickable, its name underlined. */
	private static final class HeaderButton extends Button {
		HeaderButton(Dim2i dim, TextComponent label) {
			super(dim, label, () -> {
			});
		}

		@Override
		protected boolean isHovered(int mouseX, int mouseY) {
			return false;
		}

		@Override
		public boolean isFocusable() {
			return false;
		}

		@Override
		public boolean mouseClicked(com.coredex.altrender.ui.framework.InteractionContext context, double mouseX,
				double mouseY, int button) {
			return false;
		}
	}

	/** A page's entry in the side bar, indented under its mod. */
	private static final class TabButton extends Button {
		TabButton(Dim2i dim, TextComponent label, Runnable action) {
			super(dim, label, action);
		}

		@Override
		protected int getLeftAlignedTextOffset(DrawContext ctx) {
			return TAB_OPTION_INDENT + super.getLeftAlignedTextOffset(ctx);
		}
	}
}
