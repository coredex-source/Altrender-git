/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.state;

import java.util.Locale;

import com.coredex.altrender.api.GameState;
import com.coredex.altrender.mc.mixin.MinecraftStateAccess;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * b1.7.3's {@link GameState} (development.md 6.1/6.5), read on the render thread. The biome is sampled once per tick
 * and block column (the biome source's buffers are shared with the game's own weather code, on the same thread).
 */
public final class B173GameState implements GameState {

	private final Minecraft minecraft;
	private long biomeTick = Long.MIN_VALUE;
	private int biomeX;
	private int biomeZ;
	private String biome;
	private float temperature = 0.5f;
	private float rainfall = 0.5f;
	private int precipitation = 1;

	public B173GameState(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public String dimension() {
		World world = minecraft.world;
		if (world == null || world.dimension == null) {
			return null;
		}
		return switch (world.dimension.id) {
			case -1 -> "minecraft:the_nether";
			case 0 -> "minecraft:overworld";
			default -> "minecraft:sky"; // beta's unused sky dimension: no pack has a folder for it
		};
	}

	@Override
	public float partialTick() {
		return ((MinecraftStateAccess) minecraft).altrender$timer().partialTick;
	}

	@Override
	public long worldTime() {
		World w = minecraft.world;
		return w != null ? w.getTime() : 0;
	}

	@Override
	public float skyAngle() {
		World w = minecraft.world;
		return w != null ? w.getTimeOfDay(partialTick()) : 0;
	}

	@Override
	public float rainStrength() {
		World w = minecraft.world;
		return w != null ? w.getRain(partialTick()) : 0;
	}

	@Override
	public float thunderStrength() {
		World w = minecraft.world;
		return w != null ? w.getThunder(partialTick()) : 0;
	}

	@Override
	public float[] skyColor() {
		World w = minecraft.world;
		if (w == null || minecraft.camera == null) {
			return new float[] {0, 0, 0};
		}
		Vec3d c = w.getSkyColor(minecraft.camera, partialTick());
		return new float[] {(float) c.x, (float) c.y, (float) c.z};
	}

	@Override
	public void cameraPosition(double[] out) {
		MobEntity c = minecraft.camera;
		if (c == null) {
			out[0] = out[1] = out[2] = 0;
			return;
		}
		double t = partialTick();
		out[0] = c.prevX + (c.x - c.prevX) * t;
		out[1] = c.prevY + (c.y - c.prevY) * t;
		out[2] = c.prevZ + (c.z - c.prevZ) * t;
	}

	@Override
	public int eyeFluid() {
		MobEntity c = minecraft.camera;
		if (c == null || c.world == null) {
			return 0;
		}
		if (c.isSubmergedIn(Material.WATER)) {
			return 1;
		}
		return c.isSubmergedIn(Material.LAVA) ? 2 : 0;
	}

	private int light(LightType type) {
		MobEntity c = minecraft.camera;
		World w = minecraft.world;
		if (c == null || w == null) {
			return type == LightType.SKY ? 15 : 0;
		}
		int x = (int) Math.floor(c.x);
		int y = (int) Math.floor(c.y + c.getEyeHeight());
		int z = (int) Math.floor(c.z);
		if (y < 0 || y >= 128) {
			return type == LightType.SKY ? 15 : 0;
		}
		return w.getLight(type, x, y, z);
	}

	@Override
	public int eyeBlockLight() {
		return light(LightType.BLOCK);
	}

	@Override
	public int eyeSkyLight() {
		return light(LightType.SKY);
	}

	private void sampleBiome() {
		World w = minecraft.world;
		MobEntity c = minecraft.camera;
		if (w == null || c == null) {
			biome = null;
			return;
		}
		int x = (int) Math.floor(c.x);
		int z = (int) Math.floor(c.z);
		long tick = w.getTime();
		if (tick == biomeTick && x == biomeX && z == biomeZ) {
			return;
		}
		biomeTick = tick;
		biomeX = x;
		biomeZ = z;
		BiomeSource source = w.getBiomeSource();
		Biome b = source.getBiome(x, z);
		biome = b.name.toLowerCase(Locale.ROOT).replace(' ', '_');
		// The Nether's fixed source keeps no per-sample arrays: ask it (no rain there).
		double[] t = source.temperatures != null ? source.temperatures : source.getTemperatures(null, x, z, 1, 1);
		temperature = (float) t[0];
		rainfall = source.downfalls != null ? (float) source.downfalls[0] : 0;
		precipitation = b.isSnowy() ? 2 : b.isRainy() ? 1 : 0;
	}

	@Override
	public String biome() {
		sampleBiome();
		return biome;
	}

	@Override
	public float temperature() {
		sampleBiome();
		return temperature;
	}

	@Override
	public float rainfall() {
		sampleBiome();
		return rainfall;
	}

	@Override
	public int precipitation() {
		sampleBiome();
		return precipitation;
	}

	@Override
	public int heldItem() {
		PlayerEntity p = minecraft.player;
		ItemStack s = p != null ? p.inventory.getSelectedItem() : null;
		return s != null ? s.id : -1;
	}

	@Override
	public int heldItemDamage() {
		PlayerEntity p = minecraft.player;
		ItemStack s = p != null ? p.inventory.getSelectedItem() : null;
		return s != null ? s.getDamage() : 0;
	}

	@Override
	public int heldItemLight() {
		int id = heldItem();
		return id >= 0 && id < net.minecraft.block.Block.LIGHT.length ? net.minecraft.block.Block.LIGHT[id] : 0;
	}

	@Override
	public float health() {
		PlayerEntity p = minecraft.player;
		return p != null ? p.health : 20;
	}

	@Override
	public int air() {
		PlayerEntity p = minecraft.player;
		return p != null ? p.breath : 300;
	}

	@Override
	public boolean sneaking() {
		PlayerEntity p = minecraft.player;
		return p != null && p.isSneaking();
	}

	@Override
	public boolean burning() {
		PlayerEntity p = minecraft.player;
		return p != null && p.isOnFire();
	}

	@Override
	public boolean hurt() {
		PlayerEntity p = minecraft.player;
		return p != null && p.damagedTimer > 0;
	}

	@Override
	public boolean onGround() {
		PlayerEntity p = minecraft.player;
		return p == null || p.onGround;
	}

	@Override
	public float[] brightnessTable() {
		World w = minecraft.world;
		return w != null && w.dimension != null ? w.dimension.brightnessTable.clone() : GameState.super.brightnessTable();
	}

	@Override
	public int skyDarkness() {
		World w = minecraft.world;
		return w != null ? w.ambientDarkness : 0;
	}

	@Override
	public boolean hideGui() {
		return minecraft.options != null && minecraft.options.hideGui;
	}

	@Override
	public int renderDistance() {
		return minecraft.options != null ? com.coredex.altrender.mc.ui.RenderDistance.chunks(minecraft.options) : 8;
	}
}
