/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader (scene files, the config): objects become {@code Map<String, Object>}, arrays {@code List<Object>},
 * numbers {@code Double}, plus String, Boolean and null. No library: production profiles of old versions have none.
 */
public final class Json {

	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		Json p = new Json(text);
		Object v = p.value();
		p.ws();
		if (p.i != p.s.length()) {
			throw p.error("trailing characters");
		}
		return v;
	}

	private IllegalArgumentException error(String what) {
		return new IllegalArgumentException("JSON: " + what + " at " + i);
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
	}

	private Object value() {
		ws();
		if (i >= s.length()) {
			throw error("unexpected end");
		}
		char c = s.charAt(i);
		return switch (c) {
			case '{' -> object();
			case '[' -> array();
			case '"' -> string();
			case 't' -> literal("true", Boolean.TRUE);
			case 'f' -> literal("false", Boolean.FALSE);
			case 'n' -> literal("null", null);
			default -> number();
		};
	}

	private Object literal(String word, Object v) {
		if (!s.startsWith(word, i)) {
			throw error("expected " + word);
		}
		i += word.length();
		return v;
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++;
		ws();
		if (s.charAt(i) == '}') {
			i++;
			return m;
		}
		while (true) {
			ws();
			String key = string();
			ws();
			if (s.charAt(i++) != ':') {
				throw error("expected ':'");
			}
			m.put(key, value());
			ws();
			char c = s.charAt(i++);
			if (c == '}') {
				return m;
			}
			if (c != ',') {
				throw error("expected ',' or '}'");
			}
		}
	}

	private List<Object> array() {
		List<Object> l = new ArrayList<>();
		i++;
		ws();
		if (s.charAt(i) == ']') {
			i++;
			return l;
		}
		while (true) {
			l.add(value());
			ws();
			char c = s.charAt(i++);
			if (c == ']') {
				return l;
			}
			if (c != ',') {
				throw error("expected ',' or ']'");
			}
		}
	}

	private String string() {
		if (s.charAt(i) != '"') {
			throw error("expected a string");
		}
		StringBuilder sb = new StringBuilder();
		i++;
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					case 'r' -> sb.append('\r');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'u' -> {
						sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
						i += 4;
					}
					default -> sb.append(e);
				}
			} else {
				sb.append(c);
			}
		}
	}

	private Double number() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
			i++;
		}
		if (start == i) {
			throw error("unexpected character '" + s.charAt(i) + "'");
		}
		return Double.parseDouble(s.substring(start, i));
	}
}
