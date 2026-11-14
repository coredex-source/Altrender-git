/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/TextComponent.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; no annotations dependency.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/** Text as the UI passes it around: literal, a translation key with arguments, or styled. */
public interface TextComponent {

	default List<TextComponent> children() {
		return List.of();
	}

	record Literal(String text) implements TextComponent {
		@Override
		public String toString() {
			return text;
		}
	}

	record Translatable(List<String> keys, List<?> args) implements TextComponent {
		@Override
		public String toString() {
			String keyString = keys.size() == 1 ? keys.get(0) : keys.toString();
			return args.isEmpty() ? keyString : keyString + args;
		}
	}

	record Styled(TextComponent inner, EnumSet<TextFormattingStyle> styles) implements TextComponent {
		@Override
		public List<TextComponent> children() {
			return List.of(inner);
		}

		@Override
		public TextComponent withStyle(TextFormattingStyle style, TextFormattingStyle... rest) {
			EnumSet<TextFormattingStyle> set = EnumSet.copyOf(styles);
			set.add(style);
			set.addAll(Arrays.asList(rest));
			return new Styled(inner, set);
		}

		@Override
		public String toString() {
			return inner + "[styled]";
		}
	}

	default TextComponent withStyle(TextFormattingStyle style, TextFormattingStyle... rest) {
		return new Styled(this, EnumSet.of(style, rest));
	}

	static TextComponent literal(String text) {
		return new Literal(text);
	}

	static TextComponent translatable(String key, Object... args) {
		return new Translatable(List.of(key), Arrays.asList(args));
	}

	static TextComponent translatable(List<String> keys, Object... args) {
		return new Translatable(keys, Arrays.asList(args));
	}
}
