/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import static org.lwjgl.sdl.SDLMouse.*;

import java.util.ArrayDeque;

/**
 * Mouse state and events in LWJGL2's shape, fed by {@link SdlWindow#pump()}: origin at the bottom-left of the window,
 * Y up; buttons 0 left, 1 right, 2 middle, 3+ extra; motion events have button -1; one wheel notch is 120 (as LWJGL2
 * reported on Windows; old games only use the sign).
 */
public final class MouseQueue {

	public record Event(int button, boolean down, int x, int y, int dx, int dy, int dwheel, long nanos) {
	}

	public static final int BUTTONS = 8;

	private static final ArrayDeque<Event> QUEUE = new ArrayDeque<>();
	private static final boolean[] DOWN = new boolean[BUTTONS];
	private static int x;
	private static int y;
	private static float dxAccum;
	private static float dyAccum;
	private static int wheelAccum;
	private static float wheelRemainder;
	private static Event current;

	private MouseQueue() {
	}

	/** SDL's coordinates are in points; the game's in pixels, Y up. */
	private static int toX(float sdlX) {
		return Math.round(sdlX * SdlWindow.density());
	}

	private static int toY(float sdlY) {
		return SdlWindow.height() - 1 - Math.round(sdlY * SdlWindow.density());
	}

	/** Grabbed: SDL's relative mode, whose deltas are raw device counts (not points), so they aren't scaled. */
	private static boolean relative;

	public static void setRelative(boolean on) {
		relative = on;
	}

	static void motion(float sx, float sy, float xrel, float yrel, long nanos) {
		float d = relative ? 1f : SdlWindow.density();
		x = toX(sx);
		y = toY(sy);
		dxAccum += xrel * d;
		dyAccum -= yrel * d;
		QUEUE.add(new Event(-1, false, x, y, Math.round(xrel * d), Math.round(-yrel * d), 0, nanos));
	}

	static void button(int sdlButton, boolean down, float sx, float sy, long nanos) {
		int b = switch (sdlButton) {
			case SDL_BUTTON_LEFT -> 0;
			case SDL_BUTTON_RIGHT -> 1;
			case SDL_BUTTON_MIDDLE -> 2;
			default -> sdlButton - 1;
		};
		if (b < 0 || b >= BUTTONS) {
			return;
		}
		DOWN[b] = down;
		x = toX(sx);
		y = toY(sy);
		QUEUE.add(new Event(b, down, x, y, 0, 0, 0, nanos));
	}

	static void wheel(float notches, long nanos) {
		wheelRemainder += notches;
		int whole = (int) wheelRemainder;
		if (whole == 0) {
			return;
		}
		wheelRemainder -= whole;
		wheelAccum += whole * 120;
		QUEUE.add(new Event(-1, false, x, y, 0, 0, whole * 120, nanos));
	}

	public static boolean next() {
		current = QUEUE.poll();
		return current != null;
	}

	public static Event current() {
		return current;
	}

	public static int x() {
		return x;
	}

	public static int y() {
		return y;
	}

	public static void setPosition(int nx, int ny) {
		x = nx;
		y = ny;
	}

	/** Motion since the last call, as LWJGL2's getDX/getDY returned it. */
	public static int takeDx() {
		int d = (int) dxAccum;
		dxAccum -= d;
		return d;
	}

	public static int takeDy() {
		int d = (int) dyAccum;
		dyAccum -= d;
		return d;
	}

	public static int takeWheel() {
		int w = wheelAccum;
		wheelAccum = 0;
		return w;
	}

	public static boolean isDown(int button) {
		return button >= 0 && button < BUTTONS && DOWN[button];
	}

	public static void reset() {
		QUEUE.clear();
		current = null;
		dxAccum = dyAccum = 0;
		wheelAccum = 0;
		java.util.Arrays.fill(DOWN, false);
	}
}
