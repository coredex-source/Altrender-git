/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.coredex.altrender.Log;

/**
 * Altrender's own strings ({@code assets/altrender/lang/<locale>.properties}, UTF-8): the same on every version,
 * whatever the game's translation system. English is the fallback for missing keys.
 */
public final class Lang {

	private static final Map<String, String> STRINGS = new HashMap<>();
	private static String loaded;

	private Lang() {
	}

	/** The string for {@code key} in {@code locale} (e.g. {@code en_us}), or null. */
	public static synchronized String get(String locale, String key) {
		String l = locale == null ? "en_us" : locale.toLowerCase(java.util.Locale.ROOT);
		if (!l.equals(loaded)) {
			STRINGS.clear();
			load("en_us");
			if (!l.equals("en_us")) {
				load(l);
			}
			loaded = l;
		}
		return STRINGS.get(key);
	}

	private static void load(String locale) {
		try (InputStream in = Lang.class.getResourceAsStream("/assets/altrender/lang/" + locale + ".properties")) {
			if (in == null) {
				return;
			}
			Properties p = new Properties();
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String k : p.stringPropertyNames()) {
				STRINGS.put(k, p.getProperty(k));
			}
		} catch (IOException e) {
			Log.warn("lang " + locale + ": " + e);
		}
	}
}
