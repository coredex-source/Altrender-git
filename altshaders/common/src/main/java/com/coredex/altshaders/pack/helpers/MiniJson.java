/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for the few JSON files packs carry (a custom texture's {@code .mcmeta}): objects become
 * {@link Map}s, arrays {@link List}s, numbers {@link Double}s, plus strings, booleans and {@code null}.
 */
public final class MiniJson {

	private final String s;
	private int i;

	private MiniJson(String s) {
		this.s = s;
	}

	/** @throws IllegalArgumentException on malformed JSON */
	public static Object parse(String json) {
		MiniJson p = new MiniJson(json);
		Object v = p.value();
		p.space();
		if (p.i < p.s.length()) {
			throw p.error("trailing characters");
		}
		return v;
	}

	private IllegalArgumentException error(String what) {
		return new IllegalArgumentException(what + " at offset " + i);
	}

	private void space() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
	}

	private Object value() {
		space();
		if (i >= s.length()) {
			throw error("unexpected end");
		}
		char c = s.charAt(i);
		switch (c) {
			case '{' -> {
				i++;
				Map<String, Object> m = new LinkedHashMap<>();
				space();
				if (i < s.length() && s.charAt(i) == '}') {
					i++;
					return m;
				}
				while (true) {
					space();
					if (i >= s.length() || s.charAt(i) != '"') {
						throw error("key expected");
					}
					String key = string();
					space();
					expect(':');
					m.put(key, value());
					space();
					if (i < s.length() && s.charAt(i) == ',') {
						i++;
						continue;
					}
					expect('}');
					return m;
				}
			}
			case '[' -> {
				i++;
				List<Object> l = new ArrayList<>();
				space();
				if (i < s.length() && s.charAt(i) == ']') {
					i++;
					return l;
				}
				while (true) {
					l.add(value());
					space();
					if (i < s.length() && s.charAt(i) == ',') {
						i++;
						continue;
					}
					expect(']');
					return l;
				}
			}
			case '"' -> {
				return string();
			}
			default -> {
				if (s.startsWith("true", i)) {
					i += 4;
					return Boolean.TRUE;
				}
				if (s.startsWith("false", i)) {
					i += 5;
					return Boolean.FALSE;
				}
				if (s.startsWith("null", i)) {
					i += 4;
					return null;
				}
				int start = i;
				while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
					i++;
				}
				if (start == i) {
					throw error("value expected");
				}
				try {
					return Double.parseDouble(s.substring(start, i));
				} catch (NumberFormatException e) {
					throw error("bad number");
				}
			}
		}
	}

	private void expect(char c) {
		if (i >= s.length() || s.charAt(i) != c) {
			throw error("'" + c + "' expected");
		}
		i++;
	}

	private String string() {
		expect('"');
		StringBuilder b = new StringBuilder();
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '"') {
				return b.toString();
			}
			if (c != '\\') {
				b.append(c);
				continue;
			}
			if (i >= s.length()) {
				break;
			}
			char e = s.charAt(i++);
			switch (e) {
				case 'n' -> b.append('\n');
				case 't' -> b.append('\t');
				case 'r' -> b.append('\r');
				case 'b' -> b.append('\b');
				case 'f' -> b.append('\f');
				case 'u' -> {
					if (i + 4 > s.length()) {
						throw error("bad \\u escape");
					}
					b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
					i += 4;
				}
				default -> b.append(e);
			}
		}
		throw error("unterminated string");
	}
}
