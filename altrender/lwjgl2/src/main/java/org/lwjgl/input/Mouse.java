/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.input;

import com.coredex.altrender.lwjgl2.MouseQueue;
import com.coredex.altrender.lwjgl2.SdlWindow;
import org.lwjgl.LWJGLException;
import org.lwjgl.sdl.SDLMouse;

/** LWJGL2's {@code Mouse} on SDL3 events (Altrender's LWJGL2 layer). Grabbed = SDL relative mouse mode. */
public final class Mouse {

	public static final int EVENT_SIZE = 22;

	private static boolean created;
	private static boolean grabbed;
	private static Cursor nativeCursor;

	private Mouse() {
	}

	public static void create() throws LWJGLException {
		if (!SdlWindow.isCreated()) {
			throw new IllegalStateException("Display must be created before Mouse");
		}
		created = true;
	}

	public static void destroy() {
		created = false;
		setGrabbed(false);
	}

	public static boolean isCreated() {
		return created;
	}

	public static void poll() {
	}

	public static boolean next() {
		return MouseQueue.next();
	}

	public static int getEventButton() {
		var e = MouseQueue.current();
		return e != null ? e.button() : -1;
	}

	public static boolean getEventButtonState() {
		var e = MouseQueue.current();
		return e != null && e.down();
	}

	public static int getEventX() {
		var e = MouseQueue.current();
		return e != null ? e.x() : MouseQueue.x();
	}

	public static int getEventY() {
		var e = MouseQueue.current();
		return e != null ? e.y() : MouseQueue.y();
	}

	public static int getEventDX() {
		var e = MouseQueue.current();
		return e != null ? e.dx() : 0;
	}

	public static int getEventDY() {
		var e = MouseQueue.current();
		return e != null ? e.dy() : 0;
	}

	public static int getEventDWheel() {
		var e = MouseQueue.current();
		return e != null ? e.dwheel() : 0;
	}

	public static long getEventNanoseconds() {
		var e = MouseQueue.current();
		return e != null ? e.nanos() : 0L;
	}

	public static int getX() {
		return MouseQueue.x();
	}

	public static int getY() {
		return MouseQueue.y();
	}

	public static int getDX() {
		return MouseQueue.takeDx();
	}

	public static int getDY() {
		return MouseQueue.takeDy();
	}

	public static int getDWheel() {
		return MouseQueue.takeWheel();
	}

	public static boolean isButtonDown(int button) {
		return MouseQueue.isDown(button);
	}

	public static int getButtonCount() {
		return MouseQueue.BUTTONS;
	}

	public static String getButtonName(int button) {
		return button >= 0 && button < MouseQueue.BUTTONS ? "BUTTON" + button : null;
	}

	public static int getButtonIndex(String name) {
		if (name != null && name.startsWith("BUTTON")) {
			try {
				return Integer.parseInt(name.substring(6));
			} catch (NumberFormatException ignored) {
			}
		}
		return -1;
	}

	public static boolean hasWheel() {
		return true;
	}

	public static boolean isInsideWindow() {
		return SdlWindow.focused();
	}

	public static boolean isGrabbed() {
		return grabbed;
	}

	public static void setGrabbed(boolean grab) {
		grabbed = grab;
		MouseQueue.setRelative(grab);
		if (SdlWindow.isCreated()) {
			SDLMouse.SDL_SetWindowRelativeMouseMode(SdlWindow.handle(), grab);
		}
		MouseQueue.takeDx();
		MouseQueue.takeDy();
	}

	public static void setCursorPosition(int x, int y) {
		if (SdlWindow.isCreated()) {
			float d = SdlWindow.density();
			SDLMouse.SDL_WarpMouseInWindow(SdlWindow.handle(), x / d, (SdlWindow.height() - 1 - y) / d);
		}
		MouseQueue.setPosition(x, y);
	}

	public static Cursor getNativeCursor() {
		return nativeCursor;
	}

	public static Cursor setNativeCursor(Cursor cursor) throws LWJGLException {
		Cursor old = nativeCursor;
		nativeCursor = cursor;
		if (cursor != null && cursor.isBlank()) {
			SDLMouse.SDL_HideCursor();
		} else {
			SDLMouse.SDL_ShowCursor();
		}
		return old;
	}

	public static void setClipMouseCoordinatesToWindow(boolean clip) {
	}
}
