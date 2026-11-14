/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.ItemInHandRenderer;

/** The hand's raise (it rises over ticks after a world loads; the harness freezes ticks): pinned raised. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandAccess {

	@Accessor("handHeight")
	void altrender$setHandHeight(float height);

	@Accessor("lastHandHeight")
	void altrender$setLastHandHeight(float height);
}
