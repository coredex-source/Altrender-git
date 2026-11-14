/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Celeritas (https://git.taumc.org/embeddedt/celeritas),
 * common/src/main/java/org/embeddedt/embeddium/impl/gui/framework/FontMetricsProvider.java at commit 38ccea6c, licensed under LGPL-3.0; Celeritas's
 * options GUI derives from Reese's Sodium Options (Copyright (c) 2021 FlashyReese, MIT; the notice is in
 * META-INF/licenses/altrender/reeses-sodium-options.txt).
 * Modified by Coredex, 2026-11-12: Altrender package and style; extractString first, the rest defaults over it.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altrender.ui.framework;

import java.util.ArrayList;
import java.util.List;

/** Measuring text in GUI units. */
public interface FontMetricsProvider {

	/** The component's text, translated and with arguments filled in (no formatting codes). */
	String extractString(TextComponent component);

	int getStringWidth(String str);

	default int getStringWidth(TextComponent component) {
		return getStringWidth(extractString(component));
	}

	default String substrByWidth(String str, int maxWidth) {
		return substrByWidth(str, maxWidth, false);
	}

	/** The longest prefix (or with {@code reverse}, suffix) of {@code str} at most {@code maxWidth} wide. */
	default String substrByWidth(String str, int maxWidth, boolean reverse) {
		if (getStringWidth(str) <= maxWidth) {
			return str;
		}
		int lo = 0;
		int hi = str.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			String part = reverse ? str.substring(str.length() - mid) : str.substring(0, mid);
			if (getStringWidth(part) <= maxWidth) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return reverse ? str.substring(str.length() - lo) : str.substring(0, lo);
	}

	/** Word-wrapped lines of at most {@code maxWidth}. */
	default List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				String candidate = line.length() == 0 ? word : line + " " + word;
				if (getStringWidth(candidate) <= maxWidth || line.length() == 0) {
					line.setLength(0);
					line.append(candidate);
				} else {
					lines.add(line.toString());
					line.setLength(0);
					line.append(word);
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	int lineHeight();
}
