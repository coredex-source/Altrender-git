/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.shaderpacks;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A shader pack addon's packs and options, which Altrender shows as its shader pack screen (since 0.3.0; laid out as
 * Iris's: the pack list, then the pack's own option screens). Changes wait until {@link #apply}; everything runs on
 * the render thread.
 */
public interface ShaderPackMenu {

	/** The packs in the packs folder, by name. */
	List<String> packs();

	/** The chosen pack (pending), or null for none. */
	String pack();

	/** Choose a pack (pending until {@link #apply}). */
	void setPack(String name);

	/** Whether shader packs are on (pending). */
	boolean enabled();

	void setEnabled(boolean on);

	/** The applied pack's options, with pending values, or null (no pack on, or it didn't load). */
	Options options();

	/** Whether anything waits to be applied. */
	boolean changed();

	/** Applies the pending choices and values (written, the pack reloaded). */
	void apply();

	/** Drops the pending choices and values. */
	void cancel();

	/** The last load or build problem (with pack file and line), or null. */
	String problem();

	/** Changes when the applied pack is loaded again (after Apply): the screen shows it anew. */
	default int revision() {
		return 0;
	}

	/** Shows the packs folder in the file manager. */
	void openFolder();

	/** The system's file chooser for packs (zips) to copy into the packs folder. */
	void addPacks();

	/** A pack's option screens. */
	interface Options {

		/** The pack's name, for the header. */
		String title();

		Screen main();

		/** A sub-screen by id, or null. */
		Screen screen(String id);

		/** Every option back to its default (pending). */
		void resetAll();

		/** Whether some option differs from its default. */
		boolean anyChanged();
	}

	/** One screen of options in {@code columns} columns, in order. */
	record Screen(String id, String title, int columns, List<Element> elements) {
	}

	/** One cell of a screen. */
	sealed interface Element {
	}

	/** An empty cell. */
	record Empty() implements Element {
	}

	/** A button to another screen. */
	record Link(String screen, String label, String comment) implements Element {
	}

	/** The pack's profiles; {@code current} null: custom (no profile matches). */
	record Profile(List<String> names, Function<String, String> label, Supplier<String> current,
			Consumer<String> select) implements Element {
	}

	/** An on/off option. */
	record Toggle(String id, String label, String comment, Supplier<Boolean> value, boolean defaultValue,
			Consumer<Boolean> set) implements Element {
	}

	/** An option with a list of values ({@code label}: a value as shown; {@code slider}: shown as a slider). */
	record Choice(String id, String label, String comment, List<String> values, Function<String, String> valueLabel,
			Supplier<String> value, String defaultValue, Consumer<String> set, boolean slider) implements Element {
	}
}
