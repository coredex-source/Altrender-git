/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/materialmap/NamespacedId.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.materialmap;

import java.util.Objects;

public class NamespacedId {
	private final String namespace;
	private final String name;

	public NamespacedId(String combined) {
		int colonIdx = combined.indexOf(':');

		if (colonIdx == -1) {
			namespace = "minecraft";
			name = combined;
		} else {
			namespace = combined.substring(0, colonIdx);
			name = combined.substring(colonIdx + 1);
		}
	}

	public NamespacedId(String namespace, String name) {
		this.namespace = Objects.requireNonNull(namespace);
		this.name = Objects.requireNonNull(name);
	}

	public String getNamespace() {
		return namespace;
	}

	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		NamespacedId that = (NamespacedId) o;

		return namespace.equals(that.namespace) && name.equals(that.name);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return namespace + ":" + name;
	}
}
