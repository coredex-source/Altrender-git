/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/preprocessor/PropertiesPreprocessor.java at commit bff1e69c,
 * licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' own preprocessor (Cpp) in place of JCPP; the line handling and the
 * backslash check kept.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.preprocessor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.helpers.StringPair;
import com.coredex.altshaders.pack.option.ShaderPackOptions;

/**
 * Preprocesses a pack's .properties files as Iris does: lines trimmed and blank ones dropped, a {@code #} line that
 * isn't a directive is a comment, other {@code #}s removed, backslashes kept away from the preprocessor (they continue
 * property lines, not directive lines), then directives run and macros expanded with the pack's options (a true
 * boolean option is defined, a string option is its value) and the environment's defines.
 */
public final class PropertiesPreprocessor {

	/** A property line, in an id map, that doesn't continue the previous one (a missing {@code \}). */
	public static final Pattern BACKSLASH_MATCHER = Pattern.compile(
			"(?<!\\\\\\n)^(?![ \\t]*(#|block\\.\\d*|layer\\.\\d*|item\\.\\d*|entity\\.\\d*|dimension\\.\\d*)).+",
			Pattern.MULTILINE);

	private static final String BACKSLASH = "IRIS_PASSTHROUGHBACKSLASH";

	/** The directive words JCPP knows; a {@code #} line starting with any other is a comment. */
	private static final List<String> DIRECTIVES = List.of("define", "elif", "else", "endif", "error", "if", "ifdef",
			"ifndef", "include", "line", "pragma", "undef", "warning", "include_next", "import", "ident", "sccs",
			"assert", "unassert");

	private PropertiesPreprocessor() {
	}

	public static String preprocessSource(String source, ShaderPackOptions options, Iterable<StringPair> environment) {
		check(source);
		Cpp cpp = new Cpp(false);
		options.getOptionSet().getBooleanOptions().forEach((name, option) -> {
			if (options.getOptionValues().getBooleanValueOrDefault(name)) {
				cpp.define(name);
			}
		});
		for (StringPair e : environment) {
			if (e.value().isEmpty()) {
				cpp.define(e.key());
			} else {
				cpp.define(e.key(), e.value());
			}
		}
		options.getOptionSet().getStringOptions().forEach(
				(name, option) -> cpp.define(name, options.getOptionValues().getStringValueOrDefault(name)));
		return process(cpp, source);
	}

	/** Without the pack's options (dimension.properties, read before the options are known). */
	public static String preprocessSource(String source, Iterable<StringPair> environment) {
		check(source);
		Cpp cpp = new Cpp(false);
		for (StringPair e : environment) {
			cpp.define(e.key(), e.value());
		}
		return process(cpp, source);
	}

	private static void check(String source) {
		if (source.contains(BACKSLASH)) {
			throw new IllegalArgumentException("a properties file uses " + BACKSLASH + ", a reserved name");
		}
	}

	private static String process(Cpp cpp, String source) {
		StringBuilder lines = new StringBuilder(source.length());
		for (String raw : source.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("#")) {
				String rest = line.substring(1).toLowerCase(Locale.ROOT);
				line = isDirective(rest) ? line : "";
			} else {
				line = line.replace("#", "");
			}
			lines.append(line).append('\n');
		}
		String out = cpp.process(lines.toString().replace("\\", BACKSLASH));
		for (String p : cpp.problems()) {
			PackEnv.LOG.warn("properties preprocessing: " + p);
		}
		return out.replace(BACKSLASH, "\\");
	}

	/** As Iris: the line starts with a directive's word (so {@code #iffy} counts, and is then dropped as unknown). */
	private static boolean isDirective(String afterHash) {
		for (String d : DIRECTIVES) {
			if (afterHash.startsWith(d)) {
				return true;
			}
		}
		return false;
	}

}
