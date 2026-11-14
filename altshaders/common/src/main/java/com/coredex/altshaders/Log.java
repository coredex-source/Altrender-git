/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * AltShaders' log. Every line starts with {@code [AltShaders]}. The mc layer routes it to the game's logger with
 * {@link #setSink}; until then, and on profiles without one, lines go to stdout.
 */
public final class Log {

	public enum Level { INFO, WARN, ERROR }

	@FunctionalInterface
	public interface Sink {
		void log(Level level, String message, Throwable error);
	}

	private static final String PREFIX = "[AltShaders] ";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	/** Stdout in the same shape as Fabric Loader's console fallback. */
	public static final Sink STDOUT = (level, message, error) -> {
		System.out.printf("[%s] [%s/%s]: %s%n", LocalTime.now().format(TIME), Thread.currentThread().getName(), level,
				message);
		if (error != null) {
			error.printStackTrace(System.out);
		}
	};

	private static volatile Sink sink = STDOUT;

	private Log() {
	}

	public static void setSink(Sink newSink) {
		sink = newSink != null ? newSink : STDOUT;
	}

	public static void info(String message) {
		sink.log(Level.INFO, PREFIX + message, null);
	}

	public static void warn(String message) {
		sink.log(Level.WARN, PREFIX + message, null);
	}

	public static void warn(String message, Throwable error) {
		sink.log(Level.WARN, PREFIX + message, error);
	}

	public static void error(String message, Throwable error) {
		sink.log(Level.ERROR, PREFIX + message, error);
	}
}
