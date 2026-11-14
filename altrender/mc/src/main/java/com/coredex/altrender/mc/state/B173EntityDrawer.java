/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import com.coredex.altrender.platform.EntityDrawer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** b1.7.3's entity render for another camera (development.md 6.7): the game's own, with that camera's culling. */
public final class B173EntityDrawer implements EntityDrawer {

	private final Minecraft minecraft;

	public B173EntityDrawer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void draw(BoxTest visible) {
		MobEntity c = minecraft.camera;
		if (minecraft.world == null || minecraft.worldRenderer == null || c == null) {
			return;
		}
		float t = ((com.coredex.altrender.mc.mixin.MinecraftStateAccess) minecraft).altrender$timer().partialTick;
		Vec3d pos = Vec3d.fromPool(c.prevX + (c.x - c.prevX) * t, c.prevY + (c.y - c.prevY) * t,
				c.prevZ + (c.z - c.prevZ) * t);
		minecraft.worldRenderer.renderEntities(pos, new Culler() {
			@Override
			public boolean isVisible(Box b) {
				return visible.visible(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
			}

			@Override
			public void prepare(double x, double y, double z) {
			}
		}, t);
	}
}
