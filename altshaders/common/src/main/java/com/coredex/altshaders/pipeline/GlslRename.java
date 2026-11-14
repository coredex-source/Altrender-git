/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pipeline;

import java.util.Map;

/**
 * Renames identifiers in GLSL source (development.md 6.6), comments left alone. Enough for the built-in inputs a
 * pack's terrain shader reads ({@code gl_Vertex} and friends: reserved names, so a rename can't hit the pack's own).
 */
final class GlslRename {

	private GlslRename() {
	}

	static String rename(String src, Map<String, String> names) {
		StringBuilder out = new StringBuilder(src.length() + 64);
		int i = 0;
		int n = src.length();
		while (i < n) {
			char c = src.charAt(i);
			if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
				int end = src.indexOf('\n', i);
				end = end < 0 ? n : end;
				out.append(src, i, end);
				i = end;
				continue;
			}
			if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
				int end = src.indexOf("*/", i + 2);
				end = end < 0 ? n : end + 2;
				out.append(src, i, end);
				i = end;
				continue;
			}
			if (Character.isLetter(c) || c == '_') {
				int j = i + 1;
				while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) {
					j++;
				}
				String id = src.substring(i, j);
				out.append(names.getOrDefault(id, id));
				i = j;
				continue;
			}
			if (Character.isDigit(c)) {
				int j = i + 1;
				while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '.')) {
					j++;
				}
				out.append(src, i, j);
				i = j;
				continue;
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}
}
