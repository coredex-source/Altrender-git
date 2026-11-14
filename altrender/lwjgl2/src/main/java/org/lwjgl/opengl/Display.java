/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.opengl;

import java.nio.ByteBuffer;

import com.coredex.altrender.lwjgl2.KeyboardQueue;
import com.coredex.altrender.lwjgl2.MouseQueue;
import com.coredex.altrender.lwjgl2.SdlWindow;
import org.lwjgl.LWJGLException;

/**
 * LWJGL2's {@code Display} on an SDL3 window (Altrender's LWJGL2 layer, development.md 0.4). Fullscreen is borderless
 * desktop fullscreen; embedding in an AWT canvas isn't supported (each version's adapter starts the game without
 * one).
 */
public final class Display {

	private static DisplayMode mode = new DisplayMode(640, 480);
	private static long lastSync;

	private Display() {
	}

	public static void create() throws LWJGLException {
		SdlWindow.setSize(mode.getWidth(), mode.getHeight());
		KeyboardQueue.reset();
		MouseQueue.reset();
		SdlWindow.create();
	}

	public static void create(PixelFormat format) throws LWJGLException {
		create();
	}

	public static void destroy() {
		SdlWindow.destroy();
	}

	public static boolean isCreated() {
		return SdlWindow.isCreated();
	}

	/** Swap, then process window and input events (LWJGL2's order). */
	public static void update() {
		update(true);
	}

	public static void update(boolean processMessages) {
		SdlWindow.swap();
		if (processMessages) {
			SdlWindow.pump();
		}
	}

	public static void swapBuffers() throws LWJGLException {
		SdlWindow.swap();
	}

	public static void processMessages() {
		SdlWindow.pump();
	}

	/** Frame limiter: calls at most {@code fps} per second apart, paced precisely (Altrender FramePacer, 3.6). */
	public static void sync(int fps) {
		if (fps <= 0) {
			return;
		}
		long frame = 1_000_000_000L / fps;
		long now = System.nanoTime();
		if (lastSync == 0 || now - lastSync > 4 * frame) {
			lastSync = now; // first call, or fell far behind: no catch-up burst
			return;
		}
		lastSync += frame;
		com.coredex.altrender.render.FramePacer.waitUntil(lastSync);
	}

	public static boolean isActive() {
		return SdlWindow.focused();
	}

	public static boolean isVisible() {
		return SdlWindow.visible();
	}

	public static boolean isDirty() {
		return false;
	}

	public static boolean isCloseRequested() {
		return SdlWindow.consumeCloseRequested();
	}

	public static void setTitle(String title) {
		SdlWindow.setTitle(title);
	}

	public static String getTitle() {
		return SdlWindow.title();
	}

	public static void setDisplayMode(DisplayMode newMode) throws LWJGLException {
		if (newMode == null) {
			throw new NullPointerException("mode");
		}
		mode = newMode;
		if (!SdlWindow.fullscreen()) {
			SdlWindow.setSize(newMode.getWidth(), newMode.getHeight());
		}
	}

	public static void setDisplayModeAndFullscreen(DisplayMode newMode) throws LWJGLException {
		mode = newMode;
		setFullscreen(true);
	}

	/** The window's mode: the desktop's while fullscreen, the window size otherwise. */
	public static DisplayMode getDisplayMode() {
		if (SdlWindow.fullscreen()) {
			return getDesktopDisplayMode();
		}
		return isCreated() ? new DisplayMode(SdlWindow.width(), SdlWindow.height()) : mode;
	}

	public static DisplayMode getDesktopDisplayMode() {
		int[] m = SdlWindow.desktopMode();
		return new DisplayMode(m[0], m[1], 32, m[2], true);
	}

	public static DisplayMode[] getAvailableDisplayModes() throws LWJGLException {
		return new DisplayMode[] {getDesktopDisplayMode()};
	}

	public static void setFullscreen(boolean fullscreen) throws LWJGLException {
		SdlWindow.setFullscreen(fullscreen);
		if (!fullscreen && isCreated()) {
			SdlWindow.setSize(mode.getWidth(), mode.getHeight());
		}
	}

	public static boolean isFullscreen() {
		return SdlWindow.fullscreen();
	}

	public static void setParent(java.awt.Canvas parent) throws LWJGLException {
		if (parent != null) {
			throw new LWJGLException("Altrender's LWJGL2 layer can't draw into an AWT canvas");
		}
	}

	public static java.awt.Canvas getParent() {
		return null;
	}

	public static void setResizable(boolean resizable) {
		SdlWindow.setResizable(resizable);
	}

	public static boolean isResizable() {
		return SdlWindow.resizable();
	}

	public static boolean wasResized() {
		return SdlWindow.consumeResized();
	}

	public static int getWidth() {
		return isCreated() ? SdlWindow.width() : mode.getWidth();
	}

	public static int getHeight() {
		return isCreated() ? SdlWindow.height() : mode.getHeight();
	}

	public static int getX() {
		return -1;
	}

	public static int getY() {
		return -1;
	}

	public static void setLocation(int x, int y) {
	}

	public static void setVSyncEnabled(boolean sync) {
		SdlWindow.setVsync(sync);
	}

	public static void setSwapInterval(int interval) {
		SdlWindow.setVsync(interval != 0);
	}

	/** Window icons come from Altrender's SDL hints (0.5); the game's are accepted and ignored for now. */
	public static int setIcon(ByteBuffer[] icons) {
		return 0;
	}

	public static void setInitialBackground(float red, float green, float blue) {
	}

	public static String getAdapter() {
		return null;
	}

	public static String getVersion() {
		return null;
	}

	public static float getPixelScaleFactor() {
		return 1f;
	}

	public static void makeCurrent() throws LWJGLException {
		SdlWindow.makeCurrent();
	}

	public static void releaseContext() throws LWJGLException {
		SdlWindow.releaseCurrent();
	}

	public static boolean isCurrent() throws LWJGLException {
		return true;
	}
}
