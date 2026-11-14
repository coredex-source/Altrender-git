/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debugscreen;

import java.util.Locale;

import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.settings.Config;
import com.coredex.altrender.ui.UiHost;
import com.coredex.altrender.ui.UiRenderer;
import com.coredex.altrender.ui.framework.Dim2i;
import com.coredex.altrender.ui.framework.TextComponent;

/**
 * The FPS counter (development.md 5.4; Sodium Extra's as the design reference, our own code): a line in a corner,
 * off by default. Simple: the frame rate; Advanced: also its minimum and average and the frame time over the last
 * second; optionally the GPU's time per frame. Text contrast: none, a background, or a shadow. Made again twice a
 * second; drawn in one small batch.
 */
public final class FpsCounter {

	private static final int MARGIN = 2;
	private static String text = "";
	private static int textWidth;
	private static long textTime;
	private static double drawMs;

	private FpsCounter() {
	}

	public static boolean enabled() {
		return !"off".equals(Renderer.config().fpsCounter());
	}

	/** Whether it wants the GPU frame timer. */
	public static boolean wantsGpuTimers() {
		return enabled() && Renderer.config().fpsCounterGpu();
	}

	public static double drawMs() {
		return drawMs;
	}

	/** Where the text goes for a corner, in GUI units (the counter's layout; the harness checks it fits). */
	public static Dim2i bounds(String corner, int textWidth, int lineHeight, int width, int height) {
		int w = textWidth + 4;
		int h = lineHeight + 2;
		int x = corner.endsWith("right") ? width - MARGIN - w : MARGIN;
		int y = corner.startsWith("bottom") ? height - MARGIN - h : MARGIN;
		return new Dim2i(x, y, w, h);
	}

	public static void render(UiRenderer r, UiHost host, int width, int height) {
		long start = System.nanoTime();
		Config c = Renderer.config();
		if (start - textTime >= 500_000_000L || text.isEmpty()) {
			textTime = start;
			text = text(c);
			textWidth = UiRenderer.width(host.font(), text);
		}
		r.begin(host);
		try {
			Dim2i b = bounds(c.fpsCounterCorner(), textWidth, host.font().lineHeight(), width, height);
			if ("background".equals(c.fpsCounterContrast())) {
				r.fill(b.x(), b.y(), b.getLimitX(), b.getLimitY(), 0x90000000);
			}
			r.drawString(TextComponent.literal(text), b.x() + 2, b.y() + 2, 0xFFFFFFFF,
					"shadow".equals(c.fpsCounterContrast()));
		} finally {
			r.end();
		}
		double ms = (System.nanoTime() - start) / 1e6;
		drawMs = drawMs == 0 ? ms : drawMs * 0.95 + ms * 0.05;
	}

	private static String text(Config c) {
		double[] s = DebugScreen.lastSecond();
		StringBuilder b = new StringBuilder();
		b.append(Math.round(s[0])).append(" FPS");
		if ("advanced".equals(c.fpsCounter())) {
			b.append(String.format(Locale.ROOT, "  min %d  avg %d  %.1f ms", Math.round(s[1]), Math.round(s[0]),
					s[0] > 0 ? 1000.0 / s[0] : 0));
		}
		if (c.fpsCounterGpu()) {
			Renderer r = Renderer.instance();
			double[] gpu = r != null ? r.gpuPassMs() : null;
			b.append(gpu != null ? String.format(Locale.ROOT, "  GPU %.1f ms", gpu[0]) : "  GPU -");
		}
		return b.toString();
	}
}
