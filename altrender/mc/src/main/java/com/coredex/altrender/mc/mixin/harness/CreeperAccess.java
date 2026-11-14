/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.mixin.harness;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.mob.monster.CreeperEntity;

/** Test scenes (development.md 0.7): a creeper's fuse, for one about to explode. */
@Mixin(CreeperEntity.class)
public interface CreeperAccess {

	@Accessor("fuse")
	void altrender$setFuse(int fuse);

	@Accessor("lastFuse")
	void altrender$setLastFuse(int lastFuse);
}
