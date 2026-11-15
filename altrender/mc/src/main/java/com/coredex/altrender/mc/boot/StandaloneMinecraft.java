/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import java.applet.Applet;

import com.coredex.altrender.Log;
import com.coredex.altrender.debug.harness.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import net.minecraft.util.crash.CrashReport;

/**
 * The game without AWT (development.md 0.4). LWJGL2 drew into an AWT canvas; SDL3 can't, so the game runs on its
 * standalone-window path (no canvas) and the LWJGL2 layer's SDL3 window is the only window. Two ways in:
 * <ul>
 * <li>the applet path, which Fabric Loader uses for applet-era versions: its {@code AppletFrame} hosts
 * {@code MinecraftApplet}, calls {@code init()} and {@code start()}, and only then shows the frame.
 * {@link #startFromApplet} takes the applet's parameters, runs the game and doesn't return until it ends, so that empty
 * frame is never shown (a tiling window manager would tile it even if it were hidden right after);</li>
 * <li>the vanilla standalone {@code main} ({@link #start}).</li>
 * </ul>
 */
public final class StandaloneMinecraft extends Minecraft {

	private StandaloneMinecraft(int width, int height, boolean fullscreen) {
		super(null, null, null, width, height, fullscreen);
	}

	/** Runs the game and returns only if it ends without exiting the process (then this exits). */
	public static void startFromApplet(Applet applet) {
		String server = applet.getParameter("server");
		String port = applet.getParameter("port");
		StandaloneMinecraft minecraft = create("true".equalsIgnoreCase(applet.getParameter("fullscreen")));
		minecraft.session = session(applet.getParameter("username"), applet.getParameter("sessionid"));
		minecraft.session.password = applet.getParameter("mppass");
		Thread game = launch(minecraft, server, port != null ? parsePort(port) : 25565);
		// The game ends the process itself (System.exit after quitting, or after a crash); until then, keep the launcher
		// from showing its frame.
		while (game.isAlive()) {
			try {
				game.join();
			} catch (InterruptedException e) {
				// keep waiting: returning would show the frame
			}
		}
		System.exit(0);
	}

	public static void start(String username, String sessionId, String server) {
		String host = server;
		int port = 25565;
		if (server != null && server.lastIndexOf(':') > 0) {
			host = server.substring(0, server.lastIndexOf(':'));
			port = parsePort(server.substring(server.lastIndexOf(':') + 1));
		}
		StandaloneMinecraft minecraft = create(false);
		minecraft.session = session(username, sessionId);
		launch(minecraft, host, port);
	}

	private static Session session(String username, String sessionId) {
		String name = username != null ? username : "Player" + System.currentTimeMillis() % 1000L;
		return new Session(name, sessionId != null ? sessionId : "");
	}

	private static int parsePort(String port) {
		try {
			return Integer.parseInt(port.trim());
		} catch (NumberFormatException e) {
			return 25565;
		}
	}

	/**
	 * The game before its {@link Session}, as vanilla's {@code MinecraftApplet.init}: {@code Session}'s static block list
	 * loads {@code Block} and {@code Item}, and mods set up before those in {@code Minecraft}'s constructor (OSL bootstraps
	 * its registries right after {@code Object.<init>}; without that, its item registry rejects the achievements' mapper:
	 * "registry minecraft:item is not synced").
	 */
	private static StandaloneMinecraft create(boolean fullscreen) {
		StandaloneMinecraft game = new StandaloneMinecraft(854, 480, fullscreen);
		game.hostAddress = "www.minecraft.net";
		return game;
	}

	private static Thread launch(StandaloneMinecraft game, String server, int port) {
		if (server != null) {
			game.setStartupServer(server, port);
		}
		Thread thread = new Thread(game, "Minecraft main thread");
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
		return thread;
	}

	/**
	 * Vanilla showed the report in its frame; without one: the log, a message box, then exit (nothing else would end
	 * the process). No GL here: LWJGL 3 aborts the JVM on GL calls without a context, where LWJGL2 threw. Automated
	 * runs (the scene harness) never wait on a dialog: log and exit.
	 */
	@Override
	public void handleCrash(CrashReport report) {
		Log.error("the game crashed: " + report.description, report.exception);
		String detail = report.exception != null ? report.exception.toString() : "";
		try {
			if (!Harness.active()) {
				org.lwjgl.Sys.alert("Minecraft has crashed", report.description + "\n" + detail + "\n\nSee the log for details.");
			}
		} finally {
			System.exit(1);
		}
	}
}
