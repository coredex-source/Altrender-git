/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLHints.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLKeyboard.SDL_StartTextInput;
import static org.lwjgl.sdl.SDLVideo.*;

import java.nio.IntBuffer;

import com.coredex.altrender.Log;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryStack;

/**
 * The one game window: SDL3 window, OpenGL compatibility context and the event pump behind LWJGL2's {@code Display},
 * {@code Keyboard} and {@code Mouse}. Everything runs on the thread that creates the display (the game's render
 * thread).
 * <p>
 * Context (development.md 0.5): compatibility profile 4.6, then 3.3, then whatever the driver gives without a version
 * request (Altrender itself then steps aside; vanilla still needs a context). {@code -Daltrender.glDebug} adds the debug
 * flag.
 * <p>
 * SDL hints (set before SDL starts): app id {@code minecraft} ({@code -Daltrender.appId}); the video driver is SDL's
 * choice (on Linux it takes Wayland only where the compositor has the frame-pacing protocols it wants, else X11)
 * unless {@code -Daltrender.videoDriver=wayland|x11|windows}; relative mouse motion is raw (no OS acceleration or
 * scaling); the window is high-DPI aware: the framebuffer and every coordinate the game sees are in pixels.
 */
public final class SdlWindow {

	private static final int[][] CONTEXT_VERSIONS = {{4, 6}, {3, 3}, null};

	private static boolean sdlReady;
	private static long window;
	private static long context;
	private static String title = "Game";
	private static int width = 640;
	private static int height = 480;
	private static boolean fullscreen;
	private static boolean resizable;
	private static boolean vsync;
	private static boolean closeRequested;
	private static boolean focused = true;
	private static boolean visible = true;
	private static boolean resized;
	private static float density = 1f;
	private static SDL_Event event;

	private SdlWindow() {
	}

	private static void initSdl() throws LWJGLException {
		if (sdlReady) {
			return;
		}
		SDL_SetHint(SDL_HINT_APP_ID, System.getProperty("altrender.appId", "minecraft"));
		// The JVM owns signals; the game decides when to quit.
		SDL_SetHint(SDL_HINT_NO_SIGNAL_HANDLERS, "1");
		SDL_SetHint(SDL_HINT_QUIT_ON_LAST_WINDOW_CLOSE, "0");
		String driver = System.getProperty("altrender.videoDriver");
		if (driver != null) {
			SDL_SetHint(SDL_HINT_VIDEO_DRIVER, driver);
		}
		// Raw relative motion for the grabbed mouse: no OS pointer acceleration or scaling.
		SDL_SetHint(SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE, "0");
		if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) {
			throw new LWJGLException("SDL_Init failed: " + SDL_GetError());
		}
		event = SDL_Event.malloc();
		// LWJGL 3.4 loads its GL functions through EGL whenever WAYLAND_DISPLAY is set, even when SDL runs on X11
		// (XWayland) with a GLX context; that only works because libglvnd dispatches to whichever context is current.
		// Tell it the API SDL actually uses, before LWJGL's GL class loads (the user can still set the property).
		if (org.lwjgl.system.Configuration.OPENGL_CONTEXT_API.get() == null) {
			String video = SDL_GetCurrentVideoDriver();
			org.lwjgl.system.Configuration.OPENGL_CONTEXT_API.set("wayland".equals(video) ? "EGL" : "native");
		}
		sdlReady = true;
	}

	public static synchronized void create() throws LWJGLException {
		if (window != 0) {
			throw new IllegalStateException("Display already created");
		}
		initSdl();
		long flags = SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN | SDL_WINDOW_HIGH_PIXEL_DENSITY;
		if (resizable) {
			flags |= SDL_WINDOW_RESIZABLE;
		}
		if (fullscreen) {
			flags |= SDL_WINDOW_FULLSCREEN;
		}
		SDL_GL_ResetAttributes();
		SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1);
		SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 24);
		SDL_GL_SetAttribute(SDL_GL_STENCIL_SIZE, 8);
		window = SDL_CreateWindow(title, width, height, flags);
		if (window == 0) {
			throw new LWJGLException("SDL_CreateWindow failed: " + SDL_GetError());
		}
		com.coredex.altrender.api.AltrenderApi.Host.desktop(DESKTOP);
		boolean debug = Boolean.getBoolean("altrender.glDebug");
		String requested = null;
		for (int[] version : CONTEXT_VERSIONS) {
			if (version != null) {
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY);
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, version[0]);
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, version[1]);
			} else {
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, 0);
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 1);
				SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0);
			}
			SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, debug ? SDL_GL_CONTEXT_DEBUG_FLAG : 0);
			context = SDL_GL_CreateContext(window);
			if (context != 0) {
				requested = version != null ? version[0] + "." + version[1] + " compat" : "legacy";
				break;
			}
			Log.warn("OpenGL " + (version != null ? version[0] + "." + version[1] + " compatibility" : "legacy")
					+ " context failed: " + SDL_GetError());
		}
		if (context == 0) {
			SDL_DestroyWindow(window);
			window = 0;
			throw new LWJGLException("no OpenGL context: " + SDL_GetError());
		}
		SDL_GL_MakeCurrent(window, context);
		// GL entry points come from LWJGL's own GL library, loaded by its GL class for the context API set in initSdl
		// (GLX's libGL or libEGL on Linux, opengl32 on Windows).
		GL.createCapabilities();
		org.lwjgl.opengl.GLContext.contextCreated();
		applySwapInterval();
		SDL_StartTextInput(window);
		SDL_ShowWindow(window);
		readSize();
		Log.info("context=[" + GL11.glGetString(GL11.GL_VERSION) + " " + (requested.equals("legacy") ? "legacy" : "compat")
				+ "] renderer=" + GL11.glGetString(GL11.GL_RENDERER) + " vendor=" + GL11.glGetString(GL11.GL_VENDOR)
				+ " requested=" + requested + " lwjgl=" + org.lwjgl.Version.getVersion() + " sdl=" + sdlVersion()
				+ " video=" + org.lwjgl.sdl.SDLVideo.SDL_GetCurrentVideoDriver() + " density=" + density);
		pump();
	}

	public static synchronized void destroy() {
		if (context != 0) {
			SDL_GL_DestroyContext(context);
			context = 0;
		}
		if (window != 0) {
			SDL_DestroyWindow(window);
			window = 0;
		}
		GL.setCapabilities(null);
	}

	public static boolean isCreated() {
		return window != 0;
	}

	public static long handle() {
		return window;
	}

	private static final boolean LOG_FPS = Boolean.getBoolean("altrender.debug.fps");
	private static long fpsWindowStart;
	private static int fpsFrames;
	private static long totalFrames;

	public static void swap() {
		if (window != 0) {
			long started = System.nanoTime();
			SDL_GL_SwapWindow(window);
			com.coredex.altrender.debug.FrameStats.swapped(System.nanoTime() - started);
			totalFrames++;
			if (LOG_FPS) {
				logFps();
			}
		}
	}

	/** Dev only ({@code -Daltrender.debug.fps}): presented frames per second, every 5 s. */
	private static void logFps() {
		long now = System.nanoTime();
		if (fpsWindowStart == 0) {
			fpsWindowStart = now;
		}
		fpsFrames++;
		if (now - fpsWindowStart >= 5_000_000_000L) {
			Log.info("debug.fps: " + (fpsFrames * 1_000_000_000L / (now - fpsWindowStart)) + " fps, " + totalFrames
					+ " frames, window " + width + "x" + height + (focused ? "" : " (unfocused)"));
			fpsWindowStart = now;
			fpsFrames = 0;
		}
	}

	/** The API's desktop on SDL3 (file manager, file chooser); its answers run in {@link #pump}. */
	private static final SdlDesktop DESKTOP = new SdlDesktop();

	/** Drain SDL's event queue into the window state and the keyboard/mouse queues. */
	public static void pump() {
		if (!sdlReady) {
			return;
		}
		DESKTOP.poll();
		while (SDL_PollEvent(event)) {
			switch (event.type()) {
				case SDL_EVENT_QUIT, SDL_EVENT_WINDOW_CLOSE_REQUESTED -> closeRequested = true;
				case SDL_EVENT_WINDOW_FOCUS_GAINED -> focused = true;
				case SDL_EVENT_WINDOW_FOCUS_LOST -> {
					focused = false;
					KeyboardQueue.releaseAll();
				}
				case SDL_EVENT_WINDOW_MINIMIZED -> visible = false;
				case SDL_EVENT_WINDOW_RESTORED -> visible = true;
				case SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED, SDL_EVENT_WINDOW_RESIZED, SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> {
					int w = width;
					int h = height;
					readSize();
					resized |= w != width || h != height;
				}
				case SDL_EVENT_KEY_DOWN, SDL_EVENT_KEY_UP -> KeyboardQueue.key(event.key().scancode(), event.key().down(),
						event.key().repeat(), event.key().timestamp());
				case SDL_EVENT_TEXT_INPUT -> KeyboardQueue.text(event.text().textString(), event.text().timestamp());
				case SDL_EVENT_MOUSE_MOTION -> MouseQueue.motion(event.motion().x(), event.motion().y(),
						event.motion().xrel(), event.motion().yrel(), event.motion().timestamp());
				case SDL_EVENT_MOUSE_BUTTON_DOWN, SDL_EVENT_MOUSE_BUTTON_UP -> MouseQueue.button(event.button().button(),
						event.button().down(), event.button().x(), event.button().y(), event.button().timestamp());
				case SDL_EVENT_MOUSE_WHEEL -> MouseQueue.wheel(event.wheel().y(), event.wheel().timestamp());
				default -> {
				}
			}
		}
	}

	private static void readSize() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			if (SDL_GetWindowSizeInPixels(window, w, h)) {
				width = Math.max(1, w.get(0));
				height = Math.max(1, h.get(0));
			}
			float d = SDL_GetWindowPixelDensity(window);
			density = d > 0 ? d : 1f;
		}
	}

	// --- state used by Display ---

	public static String title() {
		return title;
	}

	public static void setTitle(String t) {
		title = t != null ? t : "";
		if (window != 0) {
			SDL_SetWindowTitle(window, title);
		}
	}

	public static int width() {
		return width;
	}

	public static int height() {
		return height;
	}

	/** Requested size in pixels (LWJGL2's unit); SDL sizes windows in points. */
	public static void setSize(int w, int h) {
		width = Math.max(1, w);
		height = Math.max(1, h);
		if (window != 0 && !fullscreen) {
			SDL_SetWindowSize(window, Math.max(1, Math.round(width / density)), Math.max(1, Math.round(height / density)));
			readSize();
		}
	}

	/** Pixels per point: SDL's event coordinates are in points, the game's in pixels. */
	public static float density() {
		return density;
	}

	private static String sdlVersion() {
		int v = org.lwjgl.sdl.SDLVersion.SDL_GetVersion();
		return v / 1_000_000 + "." + v / 1000 % 1000 + "." + v % 1000;
	}

	public static boolean fullscreen() {
		return fullscreen;
	}

	/** Borderless desktop fullscreen (no video mode switch). */
	public static void setFullscreen(boolean on) {
		fullscreen = on;
		if (window != 0) {
			SDL_SetWindowFullscreen(window, on);
			pump();
			readSize();
		}
	}

	public static boolean resizable() {
		return resizable;
	}

	public static void setResizable(boolean on) {
		resizable = on;
		if (window != 0) {
			SDL_SetWindowResizable(window, on);
		}
	}

	public static void setVsync(boolean on) {
		vsync = on;
		if (context != 0) {
			applySwapInterval();
		}
	}

	/**
	 * Vsync on: adaptive (-1, a late frame tears instead of waiting a whole refresh) where the driver has it, else 1
	 * (development.md 3.6). Off: 0.
	 */
	private static void applySwapInterval() {
		if (!vsync) {
			SDL_GL_SetSwapInterval(0);
		} else if (!SDL_GL_SetSwapInterval(-1)) {
			SDL_GL_SetSwapInterval(1);
		}
	}

	public static boolean consumeCloseRequested() {
		boolean r = closeRequested;
		closeRequested = false;
		return r;
	}

	public static boolean focused() {
		return focused;
	}

	public static boolean visible() {
		return visible;
	}

	public static boolean consumeResized() {
		boolean r = resized;
		resized = false;
		return r;
	}

	/** Desktop mode of the window's display (or the primary one before the window exists): {w, h, refresh}. */
	public static int[] desktopMode() {
		try {
			initSdl();
		} catch (LWJGLException e) {
			return new int[] {width, height, 60};
		}
		int display = window != 0 ? SDL_GetDisplayForWindow(window) : SDL_GetPrimaryDisplay();
		SDL_DisplayMode mode = SDL_GetDesktopDisplayMode(display);
		if (mode == null) {
			return new int[] {width, height, 60};
		}
		return new int[] {mode.w(), mode.h(), Math.round(mode.refresh_rate())};
	}

	public static void makeCurrent() {
		if (window != 0) {
			SDL_GL_MakeCurrent(window, context);
		}
	}

	public static void releaseCurrent() {
		if (window != 0) {
			SDL_GL_MakeCurrent(window, 0);
		}
	}
}
