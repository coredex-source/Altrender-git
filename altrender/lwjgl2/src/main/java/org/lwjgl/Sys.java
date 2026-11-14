/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl;

import org.lwjgl.sdl.SDLMessageBox;
import org.lwjgl.sdl.SDLMisc;

/** LWJGL2's {@code Sys} (Altrender's LWJGL2 layer): version, timer, URLs, alerts. */
public final class Sys {

	private static final long START = System.nanoTime();

	private Sys() {
	}

	public static String getVersion() {
		return "2.9.4 (Altrender LWJGL2 layer on LWJGL " + Version.getVersion() + ")";
	}

	public static void initialize() {
	}

	public static boolean is64Bit() {
		return "64".equals(System.getProperty("sun.arch.data.model", "64"));
	}

	/** Ticks of {@link #getTimerResolution()} per second (milliseconds), monotonic. */
	public static long getTime() {
		return (System.nanoTime() - START) / 1_000_000L;
	}

	public static long getTimerResolution() {
		return 1000L;
	}

	public static boolean openURL(String url) {
		return url != null && SDLMisc.SDL_OpenURL(url);
	}

	public static void alert(String title, String message) {
		SDLMessageBox.SDL_ShowSimpleMessageBox(SDLMessageBox.SDL_MESSAGEBOX_ERROR, String.valueOf(title),
				String.valueOf(message), com.coredex.altrender.lwjgl2.SdlWindow.handle());
	}

	public static String getClipboard() {
		return org.lwjgl.sdl.SDLClipboard.SDL_GetClipboardText();
	}
}
