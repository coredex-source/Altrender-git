/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debugscreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.terrain.TerrainRenderer;

/** The debug screen's entries that don't depend on the game version (development.md 5.3): system, GL, Altrender. */
public final class AltrenderEntries {

	private static String cpu;

	private AltrenderEntries() {
	}

	public static void register() {
		DebugScreen.register(new DebugEntry("system", "altrender.debug.entry.system", DebugEntry.Column.RIGHT,
				DebugEntry.Visibility.F3, AltrenderEntries::system));
		DebugScreen.register(new DebugEntry("gl", "altrender.debug.entry.gl", DebugEntry.Column.RIGHT,
				DebugEntry.Visibility.F3, AltrenderEntries::gl));
		DebugScreen.register(new DebugEntry("altrender", "altrender.debug.entry.altrender", DebugEntry.Column.RIGHT,
				DebugEntry.Visibility.F3, AltrenderEntries::altrender));
	}

	private static void system(List<String> out) {
		Runtime rt = Runtime.getRuntime();
		long max = rt.maxMemory();
		long total = rt.totalMemory();
		long used = total - rt.freeMemory();
		out.add("Java: " + Runtime.version() + " " + System.getProperty("sun.arch.data.model", "?") + "bit");
		out.add("Mem: " + used * 100 / max + "% " + (used >> 20) + "/" + (max >> 20) + "MB");
		out.add("Allocated: " + total * 100 / max + "% " + (total >> 20) + "MB");
		out.add("CPU: " + rt.availableProcessors() + "x " + cpu());
	}

	private static void gl(List<String> out) {
		Renderer r = Renderer.instance();
		if (r == null) {
			return;
		}
		GlCaps caps = r.caps();
		out.add("");
		out.add(caps.renderer);
		out.add(caps.vendor + " " + caps.version);
	}

	private static void altrender(List<String> out) {
		Renderer r = Renderer.instance();
		if (r == null) {
			return;
		}
		out.add("Altrender: " + r.tier().tier() + " (" + r.strategies().stream().id() + ")");
		TerrainRenderer terrain = Renderer.terrain();
		if (terrain != null) {
			terrain.debugLines(out);
		}
		out.add(String.format(Locale.ROOT, "Debug screen: %.2f ms", DebugScreen.drawMs()));
	}

	/** The CPU's model (Linux /proc/cpuinfo, Windows' PROCESSOR_IDENTIFIER), else the architecture. */
	private static String cpu() {
		if (cpu == null) {
			String name = System.getenv("PROCESSOR_IDENTIFIER");
			Path info = Path.of("/proc/cpuinfo");
			if (name == null && Files.isReadable(info)) {
				try (var lines = Files.lines(info)) {
					name = lines.filter(l -> l.startsWith("model name")).map(l -> l.substring(l.indexOf(':') + 1).trim())
							.findFirst().orElse(null);
				} catch (IOException | RuntimeException e) {
					name = null;
				}
			}
			cpu = name != null ? name : System.getProperty("os.arch");
		}
		return cpu;
	}
}
