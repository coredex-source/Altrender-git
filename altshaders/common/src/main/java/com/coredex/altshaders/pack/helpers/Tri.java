/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/helpers/Tri.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.helpers;

import java.util.Objects;

public record Tri<X, Y, Z>(X first, Y second, Z third) {


	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		//noinspection rawtypes
		if (!(obj instanceof Tri tri)) return false;
		return Objects.equals(tri.first, this.first) && Objects.equals(tri.second, this.second) && Objects.equals(tri.third, this.third);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		result = prime * result + ((third == null) ? 0 : third.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "First: " + first.toString() + " Second: " + second.toString() + " Third: " + third.toString();
	}
}
