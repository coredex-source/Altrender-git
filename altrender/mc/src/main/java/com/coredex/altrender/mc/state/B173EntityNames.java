/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import java.util.Map;

import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.vehicle.MinecartEntity;

/**
 * b1.7.3's entities under their modern names (development.md 6.8), for shader packs' entity.properties: the version's
 * own keys ({@code PigZombie}) and the entities it never registered (player, lightning, fireball, …).
 */
public final class B173EntityNames {

	private static final Map<String, String> KEYS = Map.ofEntries(Map.entry("Arrow", "arrow"),
			Map.entry("Snowball", "snowball"), Map.entry("Item", "item"), Map.entry("Painting", "painting"),
			Map.entry("Creeper", "creeper"), Map.entry("Skeleton", "skeleton"), Map.entry("Spider", "spider"),
			Map.entry("Giant", "giant"), Map.entry("Zombie", "zombie"), Map.entry("Slime", "slime"),
			Map.entry("Ghast", "ghast"), Map.entry("PigZombie", "zombified_piglin"), Map.entry("Pig", "pig"),
			Map.entry("Sheep", "sheep"), Map.entry("Cow", "cow"), Map.entry("Chicken", "chicken"),
			Map.entry("Squid", "squid"), Map.entry("Wolf", "wolf"), Map.entry("PrimedTnt", "tnt"),
			Map.entry("FallingSand", "falling_block"), Map.entry("Boat", "oak_boat"));

	private B173EntityNames() {
	}

	/** The entity's modern type name, or {@code null}. */
	public static String name(Entity e) {
		if (e instanceof PlayerEntity) {
			return "minecraft:player";
		}
		if (e instanceof LightningBoltEntity) {
			return "minecraft:lightning_bolt";
		}
		if (e instanceof FireballEntity) {
			return "minecraft:fireball";
		}
		if (e instanceof EggEntity) {
			return "minecraft:egg";
		}
		if (e instanceof FishingBobberEntity) {
			return "minecraft:fishing_bobber";
		}
		if (e instanceof MinecartEntity m) {
			return switch (m.type) {
				case 1 -> "minecraft:chest_minecart";
				case 2 -> "minecraft:furnace_minecart";
				default -> "minecraft:minecart";
			};
		}
		String key = Entities.getKey(e);
		String name = key != null ? KEYS.get(key) : null;
		return name != null ? "minecraft:" + name : null;
	}
}
