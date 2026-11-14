/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack;

import java.util.LinkedHashMap;
import java.util.Map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.coredex.altshaders.Log;

/**
 * What the code ported from Iris asked of Iris itself (development.md 6.2): a logger with Iris's {@code {}}
 * placeholders, the debug-options switch ({@code -Daltshaders.debug.packs}), and the option changes the pack's
 * screen has queued but not yet applied.
 */
public final class PackEnv {

	/** Iris's very verbose parse logging; off. */
	public static final boolean SPAM = false;
	public static final Logger LOG = new Logger();
	private static final Map<String, String> OPTION_QUEUE = new LinkedHashMap<>();

	private PackEnv() {
	}

	public static boolean debugOptions() {
		return Boolean.getBoolean("altshaders.debug.packs");
	}

	/**
	 * What the GPU and tier can do, for {@link FeatureFlags} (packs requiring a missing feature are refused). Set by
	 * AltShaders at startup from the renderer's capabilities; none until then.
	 */
	public record Caps(boolean imageLoadStore, boolean bufferBlending, boolean compute, boolean tessellation,
			boolean ssbo) {
		public static final Caps NONE = new Caps(false, false, false, false, false);
		public static final Caps ALL = new Caps(true, true, true, true, true);
	}

	private static volatile Caps caps = Caps.NONE;
	private static volatile Path gameDir = Path.of(".");

	public static Caps caps() {
		return caps;
	}

	public static void setCaps(Caps c) {
		caps = c;
	}

	public static void setGameDir(Path dir) {
		gameDir = dir;
	}

	/** Where the debug dumps go ({@code -Daltshaders.debug.packs}): {@code <game dir>/patched_shaders}, as Iris. */
	public static Path debugDir() {
		Path dir = gameDir.resolve("patched_shaders");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOG.warn("can't create " + dir + ": " + e.getMessage());
		}
		return dir;
	}

	/** Option changes made in the pack's screen, not yet applied (they show as the options' values meanwhile). */
	public static Map<String, String> optionQueue() {
		return OPTION_QUEUE;
	}

	/** Iris's logger calls, to AltShaders' log. */
	public static final class Logger {
		private Logger() {
		}

		public void info(String message, Object... args) {
			Log.info(format(message, args));
		}

		public void debug(String message, Object... args) {
			if (debugOptions()) {
				Log.info(format(message, args));
			}
		}

		public void warn(String message, Object... args) {
			Throwable t = throwable(args);
			if (t != null) {
				Log.warn(format(message, args), t);
			} else {
				Log.warn(format(message, args));
			}
		}

		public void error(String message, Object... args) {
			Log.error(format(message, args), throwable(args));
		}

		public void fatal(String message, Object... args) {
			Log.error(format(message, args), throwable(args));
		}

		private static Throwable throwable(Object[] args) {
			return args.length > 0 && args[args.length - 1] instanceof Throwable t ? t : null;
		}

		private static String format(String message, Object[] args) {
			StringBuilder b = new StringBuilder();
			int arg = 0;
			int i = 0;
			while (i < message.length()) {
				int at = message.indexOf("{}", i);
				if (at < 0 || arg >= args.length || args[arg] instanceof Throwable && arg == args.length - 1) {
					b.append(message, i, message.length());
					break;
				}
				b.append(message, i, at).append(args[arg++]);
				i = at + 2;
			}
			return b.toString();
		}
	}
}
