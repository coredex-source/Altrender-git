/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/parsing/DirectiveHolder.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.parsing;

import com.coredex.altshaders.pack.helpers.Vector2f;
import com.coredex.altshaders.pack.helpers.Vector3i;
import com.coredex.altshaders.pack.helpers.Vector4f;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface DirectiveHolder {
	void acceptUniformDirective(String name, Runnable onDetected);

	void acceptCommentStringDirective(String name, Consumer<String> consumer);

	void acceptCommentIntDirective(String name, IntConsumer consumer);

	void acceptCommentFloatDirective(String name, java.util.function.Consumer<Float> consumer);

	void acceptConstBooleanDirective(String name, java.util.function.Consumer<Boolean> consumer);

	void acceptConstStringDirective(String name, Consumer<String> consumer);

	void acceptConstIntDirective(String name, IntConsumer consumer);

	void acceptConstFloatDirective(String name, java.util.function.Consumer<Float> consumer);

	void acceptConstVec2Directive(String name, Consumer<Vector2f> consumer);

	void acceptConstIVec3Directive(String name, Consumer<Vector3i> consumer);

	void acceptConstVec4Directive(String name, Consumer<Vector4f> consumer);
}
