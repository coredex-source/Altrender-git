/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/preprocessor/JcppProcessor.java at commit bff1e69c,
 * licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' own preprocessor (Cpp) in place of JCPP.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.preprocessor;

import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.helpers.StringPair;

/**
 * Preprocesses a pack's GLSL source (its includes already resolved) as Iris does: the environment's defines are given
 * to the preprocessor rather than written into the source, comments are kept, null characters removed (some packs
 * have them), and the {@code #version} and {@code #extension} lines of the taken branches are moved to the top, since
 * strict drivers (Mesa) want extensions before any code and lenient ones let packs put them anywhere.
 */
public final class GlslPreprocessor {

	private GlslPreprocessor() {
	}

	public static String process(String source, Iterable<StringPair> environment) {
		Cpp cpp = new Cpp(true);
		for (StringPair e : environment) {
			cpp.define(e.key(), e.value());
		}
		String body = cpp.process(source.replace("\u0000", ""));
		for (String p : cpp.problems()) {
			PackEnv.LOG.warn("GLSL preprocessing: " + p);
		}
		StringBuilder out = new StringBuilder(body.length() + 256);
		for (String h : cpp.hoisted()) {
			out.append(h).append('\n');
		}
		return out.append(body).append('\n').toString();
	}
}
