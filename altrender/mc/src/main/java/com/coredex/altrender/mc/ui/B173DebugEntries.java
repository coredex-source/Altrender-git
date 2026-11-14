/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import java.util.List;
import java.util.Locale;

import com.coredex.altrender.debugscreen.DebugEntry;
import com.coredex.altrender.debugscreen.DebugScreen;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.terrain.TerrainRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * b1.7.3's debug-screen entries (development.md 5.3): what the game's own F3 showed (version, FPS, its renderer
 * counts, memory, position, facing) plus biome, light, the targeted block and dimension; chunk updates are
 * Altrender's sections built per second (the game's own counter stays 0 with Altrender's terrain).
 */
public final class B173DebugEntries {

	private static final String[] FACING = {"south (Towards positive Z)", "west (Towards negative X)",
			"north (Towards negative Z)", "east (Towards positive X)"};
	private static boolean registered;
	private static long lastBuilt;
	private static long lastSecond;
	private static long builtPerSecond;

	private B173DebugEntries() {
	}

	public static void register(Minecraft mc) {
		if (registered) {
			return;
		}
		registered = true;
		String version = FabricLoader.getInstance().getModContainer("altrender")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		DebugScreen.register(new DebugEntry("game", "altrender.debug.entry.game", DebugEntry.Column.LEFT,
				DebugEntry.Visibility.F3, out -> game(mc, version, out)));
		DebugScreen.register(new DebugEntry("renderer", "altrender.debug.entry.renderer", DebugEntry.Column.LEFT,
				DebugEntry.Visibility.F3, out -> renderer(mc, out)));
		DebugScreen.register(new DebugEntry("position", "altrender.debug.entry.position", DebugEntry.Column.LEFT,
				DebugEntry.Visibility.F3, out -> position(mc, out)));
		DebugScreen.register(new DebugEntry("world", "altrender.debug.entry.world", DebugEntry.Column.LEFT,
				DebugEntry.Visibility.F3, out -> world(mc, out)));
		DebugScreen.register(new DebugEntry("target", "altrender.debug.entry.target", DebugEntry.Column.LEFT,
				DebugEntry.Visibility.F3, out -> target(mc, out)));
		com.coredex.altrender.debugscreen.AltrenderEntries.register();
		DebugScreen.register(new DebugEntry("display", "altrender.debug.entry.display", DebugEntry.Column.RIGHT,
				DebugEntry.Visibility.F3, out -> display(mc, out)));
	}

	private static void game(Minecraft mc, String version, List<String> out) {
		String fps = mc.fpsDebugInfo;
		int comma = fps.indexOf(',');
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			long now = System.currentTimeMillis();
			if (now - lastSecond >= 1000) {
				long built = terrain.meshesBuilt();
				builtPerSecond = lastSecond == 0 ? 0 : built - lastBuilt;
				lastBuilt = built;
				lastSecond = now;
			}
			fps = (comma > 0 ? fps.substring(0, comma) : fps) + ", " + builtPerSecond + " chunk updates";
		}
		out.add("Minecraft Beta 1.7.3 (Altrender " + version + ")");
		out.add(fps);
	}

	private static void renderer(Minecraft mc, List<String> out) {
		if (Renderer.terrain() == null) {
			out.add(mc.getRenderChunkDebugInfo()); // with Altrender's terrain, its own line (right) replaces this
		}
		out.add(mc.getRenderEntityDebugInfo());
		out.add(mc.getWorldDebugInfo());
		out.add(mc.getChunkSourceDebugInfo());
	}

	private static void position(Minecraft mc, List<String> out) {
		net.minecraft.entity.mob.player.PlayerEntity p = mc.player;
		if (p == null) {
			return;
		}
		int bx = MathHelper.floor(p.x);
		int by = MathHelper.floor(p.y - p.eyeHeight);
		int bz = MathHelper.floor(p.z);
		out.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", p.x, p.y - p.eyeHeight, p.z));
		out.add("Block: " + bx + " " + by + " " + bz);
		out.add("Chunk: " + (bx & 15) + " " + (by & 15) + " " + (bz & 15) + " in " + (bx >> 4) + " " + (by >> 4) + " "
				+ (bz >> 4));
		int f = MathHelper.floor(p.yaw * 4.0F / 360.0F + 0.5) & 3;
		out.add(String.format(Locale.ROOT, "Facing: %s (%.1f / %.1f) f: %d", FACING[f],
				wrap(p.yaw), p.pitch, f));
	}

	private static void world(Minecraft mc, List<String> out) {
		net.minecraft.entity.mob.player.PlayerEntity p = mc.player;
		World w = mc.world;
		if (p == null || w == null) {
			return;
		}
		int bx = MathHelper.floor(p.x);
		int by = MathHelper.floor(p.y - p.eyeHeight);
		int bz = MathHelper.floor(p.z);
		out.add("Biome: " + w.getBiomeSource().getBiome(bx, bz).name);
		int sky = w.getLight(LightType.SKY, bx, by, bz);
		int block = w.getLight(LightType.BLOCK, bx, by, bz);
		out.add("Light: " + w.getRawBrightness(bx, by, bz) + " (" + sky + " sky, " + block + " block)");
		out.add("Dimension: " + (p.dimension == -1 ? "The Nether" : "Overworld"));
	}

	private static void target(Minecraft mc, List<String> out) {
		HitResult t = mc.crosshairTarget;
		World w = mc.world;
		if (t == null || w == null) {
			return;
		}
		if (t.type == HitResult.Type.BLOCK) {
			int id = w.getBlock(t.x, t.y, t.z);
			int meta = w.getBlockMetadata(t.x, t.y, t.z);
			Block b = id > 0 && id < Block.BY_ID.length ? Block.BY_ID[id] : null;
			out.add("Targeted Block: " + t.x + ", " + t.y + ", " + t.z);
			out.add(id + ":" + meta + (b != null ? " " + b.getName() : ""));
		} else if (t.entity != null) {
			out.add("Targeted Entity: " + t.entity.getClass().getSimpleName());
		}
	}

	private static float wrap(float degrees) {
		float d = degrees % 360f;
		return d >= 180f ? d - 360f : d < -180f ? d + 360f : d;
	}

	private static void display(Minecraft mc, List<String> out) {
		Window window = new Window(mc.options, mc.width, mc.height);
		out.add("Display: " + mc.width + "x" + mc.height + " (GUI scale " + window.scale + ")");
	}
}
