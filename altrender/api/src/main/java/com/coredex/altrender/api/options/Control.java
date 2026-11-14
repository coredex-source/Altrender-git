/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** How an option is edited. */
public sealed interface Control<T> permits Control.TickBox, Control.Slider, Control.Cycling, Control.Action {

	/** On or off. */
	record TickBox() implements Control<Boolean> {
	}

	/**
	 * An integer in [min, max], in steps of {@code interval} (max - min must be a multiple of it).
	 *
	 * @param format the value as shown (plain text or a translation key)
	 */
	record Slider(int min, int max, int interval, IntFunction<String> format) implements Control<Integer> {
		public Slider {
			if (max <= min || interval <= 0 || (max - min) % interval != 0) {
				throw new IllegalArgumentException("slider " + min + ".." + max + " step " + interval);
			}
		}
	}

	/**
	 * One of {@code values}, clicked through in order (Shift: backwards); values {@code allowed} rejects are skipped.
	 *
	 * @param names each value as shown (plain text or a translation key)
	 */
	record Cycling<T>(List<T> values, Function<T, String> names, Predicate<T> allowed) implements Control<T> {
		public Cycling(List<T> values, Function<T, String> names) {
			this(values, names, v -> true);
		}
	}

	/**
	 * A button on the option's row that does something at once ({@code run}, on click; since 0.3.0): opening a folder,
	 * reloading. The option's value isn't used.
	 *
	 * @param label the button's text (plain text or a translation key)
	 */
	record Action(String label, Runnable run) implements Control<Boolean> {
	}
}
