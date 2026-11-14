/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import static org.lwjgl.sdl.SDLScancode.*;

/**
 * LWJGL2 key codes (the DirectInput scan-code numbering LWJGL2's {@code Keyboard.KEY_*} constants use) and their names,
 * mapped from SDL3 scancodes (physical key positions, like LWJGL2's codes).
 */
public final class KeyCodes {

	public static final int KEYBOARD_SIZE = 256;

	private static final int[] FROM_SDL = new int[SDL_SCANCODE_COUNT];
	private static final String[] NAMES = new String[KEYBOARD_SIZE];

	private KeyCodes() {
	}

	private static void map(int sdl, int lwjgl, String name) {
		FROM_SDL[sdl] = lwjgl;
		NAMES[lwjgl] = name;
	}

	private static void name(int lwjgl, String name) {
		NAMES[lwjgl] = name;
	}

	static {
		name(0, "NONE");
		map(SDL_SCANCODE_ESCAPE, 1, "ESCAPE");
		int[] digits = {SDL_SCANCODE_1, SDL_SCANCODE_2, SDL_SCANCODE_3, SDL_SCANCODE_4, SDL_SCANCODE_5, SDL_SCANCODE_6,
				SDL_SCANCODE_7, SDL_SCANCODE_8, SDL_SCANCODE_9, SDL_SCANCODE_0};
		for (int i = 0; i < 10; i++) {
			map(digits[i], 2 + i, String.valueOf((i + 1) % 10));
		}
		map(SDL_SCANCODE_MINUS, 12, "MINUS");
		map(SDL_SCANCODE_EQUALS, 13, "EQUALS");
		map(SDL_SCANCODE_BACKSPACE, 14, "BACK");
		map(SDL_SCANCODE_TAB, 15, "TAB");
		row(16, "QWERTYUIOP");
		map(SDL_SCANCODE_LEFTBRACKET, 26, "LBRACKET");
		map(SDL_SCANCODE_RIGHTBRACKET, 27, "RBRACKET");
		map(SDL_SCANCODE_RETURN, 28, "RETURN");
		map(SDL_SCANCODE_LCTRL, 29, "LCONTROL");
		row(30, "ASDFGHJKL");
		map(SDL_SCANCODE_SEMICOLON, 39, "SEMICOLON");
		map(SDL_SCANCODE_APOSTROPHE, 40, "APOSTROPHE");
		map(SDL_SCANCODE_GRAVE, 41, "GRAVE");
		map(SDL_SCANCODE_LSHIFT, 42, "LSHIFT");
		map(SDL_SCANCODE_BACKSLASH, 43, "BACKSLASH");
		FROM_SDL[SDL_SCANCODE_NONUSHASH] = 43;
		row(44, "ZXCVBNM");
		map(SDL_SCANCODE_COMMA, 51, "COMMA");
		map(SDL_SCANCODE_PERIOD, 52, "PERIOD");
		map(SDL_SCANCODE_SLASH, 53, "SLASH");
		map(SDL_SCANCODE_RSHIFT, 54, "RSHIFT");
		map(SDL_SCANCODE_KP_MULTIPLY, 55, "MULTIPLY");
		map(SDL_SCANCODE_LALT, 56, "LMENU");
		map(SDL_SCANCODE_SPACE, 57, "SPACE");
		map(SDL_SCANCODE_CAPSLOCK, 58, "CAPITAL");
		int[] f1to10 = {SDL_SCANCODE_F1, SDL_SCANCODE_F2, SDL_SCANCODE_F3, SDL_SCANCODE_F4, SDL_SCANCODE_F5,
				SDL_SCANCODE_F6, SDL_SCANCODE_F7, SDL_SCANCODE_F8, SDL_SCANCODE_F9, SDL_SCANCODE_F10};
		for (int i = 0; i < 10; i++) {
			map(f1to10[i], 59 + i, "F" + (i + 1));
		}
		map(SDL_SCANCODE_NUMLOCKCLEAR, 69, "NUMLOCK");
		map(SDL_SCANCODE_SCROLLLOCK, 70, "SCROLL");
		map(SDL_SCANCODE_KP_7, 71, "NUMPAD7");
		map(SDL_SCANCODE_KP_8, 72, "NUMPAD8");
		map(SDL_SCANCODE_KP_9, 73, "NUMPAD9");
		map(SDL_SCANCODE_KP_MINUS, 74, "SUBTRACT");
		map(SDL_SCANCODE_KP_4, 75, "NUMPAD4");
		map(SDL_SCANCODE_KP_5, 76, "NUMPAD5");
		map(SDL_SCANCODE_KP_6, 77, "NUMPAD6");
		map(SDL_SCANCODE_KP_PLUS, 78, "ADD");
		map(SDL_SCANCODE_KP_1, 79, "NUMPAD1");
		map(SDL_SCANCODE_KP_2, 80, "NUMPAD2");
		map(SDL_SCANCODE_KP_3, 81, "NUMPAD3");
		map(SDL_SCANCODE_KP_0, 82, "NUMPAD0");
		map(SDL_SCANCODE_KP_PERIOD, 83, "DECIMAL");
		map(SDL_SCANCODE_NONUSBACKSLASH, 86, "OEM_102");
		map(SDL_SCANCODE_F11, 87, "F11");
		map(SDL_SCANCODE_F12, 88, "F12");
		map(SDL_SCANCODE_F13, 100, "F13");
		map(SDL_SCANCODE_F14, 101, "F14");
		map(SDL_SCANCODE_F15, 102, "F15");
		map(SDL_SCANCODE_F16, 103, "F16");
		map(SDL_SCANCODE_F17, 104, "F17");
		map(SDL_SCANCODE_F18, 105, "F18");
		map(SDL_SCANCODE_INTERNATIONAL2, 112, "KANA");
		map(SDL_SCANCODE_F19, 113, "F19");
		map(SDL_SCANCODE_INTERNATIONAL4, 121, "CONVERT");
		map(SDL_SCANCODE_INTERNATIONAL5, 123, "NOCONVERT");
		map(SDL_SCANCODE_INTERNATIONAL3, 125, "YEN");
		map(SDL_SCANCODE_KP_EQUALS, 141, "NUMPADEQUALS");
		map(SDL_SCANCODE_KP_ENTER, 156, "NUMPADENTER");
		map(SDL_SCANCODE_RCTRL, 157, "RCONTROL");
		map(SDL_SCANCODE_KP_COMMA, 179, "NUMPADCOMMA");
		map(SDL_SCANCODE_KP_DIVIDE, 181, "DIVIDE");
		map(SDL_SCANCODE_PRINTSCREEN, 183, "SYSRQ");
		map(SDL_SCANCODE_RALT, 184, "RMENU");
		map(SDL_SCANCODE_PAUSE, 197, "PAUSE");
		map(SDL_SCANCODE_HOME, 199, "HOME");
		map(SDL_SCANCODE_UP, 200, "UP");
		map(SDL_SCANCODE_PAGEUP, 201, "PRIOR");
		map(SDL_SCANCODE_LEFT, 203, "LEFT");
		map(SDL_SCANCODE_RIGHT, 205, "RIGHT");
		map(SDL_SCANCODE_END, 207, "END");
		map(SDL_SCANCODE_DOWN, 208, "DOWN");
		map(SDL_SCANCODE_PAGEDOWN, 209, "NEXT");
		map(SDL_SCANCODE_INSERT, 210, "INSERT");
		map(SDL_SCANCODE_DELETE, 211, "DELETE");
		map(SDL_SCANCODE_LGUI, 219, "LMETA");
		map(SDL_SCANCODE_RGUI, 220, "RMETA");
		map(SDL_SCANCODE_APPLICATION, 221, "APPS");
		map(SDL_SCANCODE_POWER, 222, "POWER");
	}

	/** Consecutive letter keys: QWERTY row letters get consecutive LWJGL2 codes from {@code first}. */
	private static void row(int first, String letters) {
		for (int i = 0; i < letters.length(); i++) {
			char c = letters.charAt(i);
			map(SDL_SCANCODE_A + (c - 'A'), first + i, String.valueOf(c));
		}
	}

	/** LWJGL2 code for an SDL scancode, 0 (KEY_NONE) when there's none. */
	public static int fromSdl(int scancode) {
		return scancode >= 0 && scancode < FROM_SDL.length ? FROM_SDL[scancode] : 0;
	}

	public static String name(int key) {
		return key >= 0 && key < NAMES.length ? NAMES[key] : null;
	}

	public static int index(String name) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i] != null && NAMES[i].equals(name)) {
				return i;
			}
		}
		return 0;
	}

	/** The character LWJGL2 delivered with keys that produce no text event. */
	static char controlChar(int key) {
		return switch (key) {
			case 1 -> 27; // escape
			case 14 -> 8; // backspace
			case 15 -> '\t';
			case 28, 156 -> '\r';
			case 211 -> 127; // delete
			default -> 0;
		};
	}
}
