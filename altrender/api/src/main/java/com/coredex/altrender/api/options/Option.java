/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api.options;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One setting on a page of Altrender's video settings (development.md 5.2). Changes stay pending (shown, not yet in
 * effect) until the player applies them; then the value goes to its setter, and each changed storage saves once.
 * Texts are translation keys (Altrender's, the game's) or plain text. Render thread only.
 */
public final class Option<T> {

	private final String id;
	private final String name;
	private final String tooltip;
	private final OptionImpact impact;
	private final Set<OptionFlag> flags;
	private final Control<T> control;
	private final OptionStorage storage;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final BooleanSupplier available;
	private final Supplier<String> unavailableReason;
	private T pending;

	private Option(Builder<T> b) {
		id = Objects.requireNonNull(b.id, "id");
		name = Objects.requireNonNull(b.name, "name");
		tooltip = b.tooltip;
		impact = b.impact;
		flags = b.flags.isEmpty() ? Set.of() : Set.copyOf(b.flags);
		control = Objects.requireNonNull(b.control, "control");
		storage = Objects.requireNonNull(b.storage, "storage");
		getter = Objects.requireNonNull(b.getter, "getter");
		setter = Objects.requireNonNull(b.setter, "setter");
		available = b.available;
		unavailableReason = b.unavailableReason;
	}

	/** Starts an option; {@code id} is unique within its mod (e.g. {@code render_distance}). */
	public static <T> Builder<T> builder(String id) {
		return new Builder<>(id);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** The description, or null. */
	public String tooltip() {
		return tooltip;
	}

	/** Null when it costs nothing worth mentioning. */
	public OptionImpact impact() {
		return impact;
	}

	public Set<OptionFlag> flags() {
		return flags;
	}

	public Control<T> control() {
		return control;
	}

	public OptionStorage storage() {
		return storage;
	}

	/** The value shown: the pending one, else the current one. */
	public T value() {
		return pending != null ? pending : getter.get();
	}

	/** Makes {@code value} pending (it takes effect on apply). */
	public void setValue(T value) {
		pending = value;
	}

	/** Whether a pending value differs from the current one. */
	public boolean hasChanged() {
		return pending != null && !pending.equals(getter.get());
	}

	/** Drops the pending value. */
	public void reset() {
		pending = null;
	}

	/** Puts a changed pending value into effect. */
	public void applyChanges() {
		if (hasChanged()) {
			setter.accept(pending);
		}
		pending = null;
	}

	public boolean isAvailable() {
		return available.getAsBoolean();
	}

	/** Why it can't be changed now, or null. */
	public String unavailableReason() {
		return isAvailable() ? null : unavailableReason.get();
	}

	public static final class Builder<T> {
		private final String id;
		private String name;
		private String tooltip;
		private OptionImpact impact;
		private final Set<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
		private Control<T> control;
		private OptionStorage storage;
		private Supplier<T> getter;
		private Consumer<T> setter;
		private BooleanSupplier available = () -> true;
		private Supplier<String> unavailableReason = () -> null;

		private Builder(String id) {
			this.id = id;
		}

		public Builder<T> name(String name) {
			this.name = name;
			return this;
		}

		public Builder<T> tooltip(String tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		public Builder<T> impact(OptionImpact impact) {
			this.impact = impact;
			return this;
		}

		public Builder<T> flags(OptionFlag... flags) {
			this.flags.addAll(java.util.Arrays.asList(flags));
			return this;
		}

		public Builder<T> control(Control<T> control) {
			this.control = control;
			return this;
		}

		public Builder<T> binding(Supplier<T> getter, Consumer<T> setter) {
			this.getter = getter;
			this.setter = setter;
			return this;
		}

		public Builder<T> storage(OptionStorage storage) {
			this.storage = storage;
			return this;
		}

		/** When it can be changed; otherwise it's shown struck through, with {@code reason} in its tooltip. */
		public Builder<T> available(BooleanSupplier available, Supplier<String> reason) {
			this.available = available;
			this.unavailableReason = reason;
			return this;
		}

		public Option<T> build() {
			return new Option<>(this);
		}
	}
}
