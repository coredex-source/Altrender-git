/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.mesh;

import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The tessellator's raw buffer and state, for per-worker tessellators and capture (development.md 2.2). */
@Mixin(Tesselator.class)
public interface TesselatorAccessor {

	@Invoker("<init>")
	static Tesselator altrender$create(int size) {
		throw new AssertionError();
	}

	@Accessor("TRIANGLE_MODE")
	static boolean altrender$triangleMode() {
		throw new AssertionError();
	}

	@Accessor("vertexBuffer")
	int[] altrender$vertexBuffer();

	@Accessor("vertexCount")
	int altrender$vertexCount();

	@Accessor("vertexCount")
	void altrender$setVertexCount(int count);

	@Accessor("index")
	void altrender$setIndex(int index);

	@Accessor("nextVertexCount")
	void altrender$setNextVertexCount(int count);

	@Accessor("colored")
	boolean altrender$colored();

	@Accessor("textured")
	boolean altrender$textured();

	@Accessor("hasNormals")
	boolean altrender$hasNormals();

	@Accessor("tesselating")
	void altrender$setTesselating(boolean tesselating);
}
