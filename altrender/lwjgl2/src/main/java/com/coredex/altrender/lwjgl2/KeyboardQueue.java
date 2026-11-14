/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import java.util.ArrayDeque;

/**
 * Keyboard state and events in LWJGL2's shape, fed by {@link SdlWindow#pump()}.
 * <p>
 * LWJGL2 delivered a key and its character in one event. SDL3 sends the key, then a separate text event; the first
 * character of that text is attached to the key-down it follows, further characters become character-only events
 * (key 0), as LWJGL2 did for input methods. Keys without text get the control character LWJGL2 gave them.
 */
public final class KeyboardQueue {

	public record Event(int key, boolean down, char character, long nanos, boolean repeat) {
	}

	private static final ArrayDeque<Event> QUEUE = new ArrayDeque<>();
	private static final boolean[] DOWN = new boolean[KeyCodes.KEYBOARD_SIZE];
	private static boolean repeatEvents;
	/** The last queued key-down still waiting for its text event, or null. */
	private static Event pendingText;
	private static Event current;

	private KeyboardQueue() {
	}

	static void key(int scancode, boolean down, boolean repeat, long sdlNanos) {
		int key = KeyCodes.fromSdl(scancode);
		if (key == 0) {
			pendingText = null;
			return;
		}
		DOWN[key] = down;
		if (repeat && !repeatEvents) {
			pendingText = null;
			return;
		}
		Event e = new Event(key, down, down ? KeyCodes.controlChar(key) : 0, sdlNanos, repeat);
		QUEUE.add(e);
		pendingText = down && e.character() == 0 ? e : null;
	}

	static void text(String text, long sdlNanos) {
		if (text == null) {
			return;
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (pendingText != null) {
				Event withChar = new Event(pendingText.key(), true, c, pendingText.nanos(), pendingText.repeat());
				replaceLast(pendingText, withChar);
				pendingText = null;
			} else {
				QUEUE.add(new Event(0, true, c, sdlNanos, false));
			}
		}
	}

	private static void replaceLast(Event old, Event replacement) {
		if (QUEUE.peekLast() == old) {
			QUEUE.pollLast();
			QUEUE.add(replacement);
		} else {
			QUEUE.add(new Event(0, true, replacement.character(), replacement.nanos(), false));
		}
	}

	/** Focus lost: every held key is released, so nothing stays stuck. */
	static void releaseAll() {
		for (int i = 0; i < DOWN.length; i++) {
			if (DOWN[i]) {
				DOWN[i] = false;
				QUEUE.add(new Event(i, false, (char) 0, System.nanoTime(), false));
			}
		}
		pendingText = null;
	}

	public static boolean next() {
		current = QUEUE.poll();
		return current != null;
	}

	public static Event current() {
		return current;
	}

	public static int pending() {
		return QUEUE.size();
	}

	public static boolean isDown(int key) {
		return key >= 0 && key < DOWN.length && DOWN[key];
	}

	public static void setRepeatEvents(boolean on) {
		repeatEvents = on;
	}

	public static boolean repeatEvents() {
		return repeatEvents;
	}

	public static void reset() {
		QUEUE.clear();
		current = null;
		pendingText = null;
		java.util.Arrays.fill(DOWN, false);
	}
}
