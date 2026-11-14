/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.ui;

import java.util.List;
import java.util.Set;

import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionFlag;
import com.coredex.altrender.api.options.OptionImpact;
import com.coredex.altrender.api.options.OptionStorage;
import com.coredex.altrender.mc.mixin.ui.MinecraftUiAccess;
import com.coredex.altrender.platform.GameVideoOptions;
import com.coredex.altrender.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import net.minecraft.locale.Language;
import org.lwjgl.opengl.Display;

/**
 * b1.7.3's video options for Altrender's settings (development.md 5.2): every option of the game's own Video Settings
 * (graphics, render distance, smooth lighting, frame limit, 3D anaglyph, view bobbing, GUI scale, Advanced OpenGL)
 * plus fullscreen, with the side effects the game's own buttons have; values in {@code options.txt}. Owner
 * (2026-11-12): the render distance is any 2..32 chunks and the frame limit Sodium's cap (both in Altrender's
 * config; the game's own step and limiter follow).
 */
public final class B173VideoOptions implements GameVideoOptions {

	private static final List<Integer> FOUR = List.of(0, 1, 2, 3);
	private static final String[] GUI_SCALES = {"options.guiScale.auto", "options.guiScale.small",
			"options.guiScale.normal", "options.guiScale.large"};

	private final Minecraft mc;
	private final GameOptions o;
	private final OptionStorage storage;

	public B173VideoOptions(Minecraft mc) {
		this.mc = mc;
		this.o = mc.options;
		this.storage = flags -> o.save();
	}

	/**
	 * Once, when Altrender's frame cap has never been set: the game's own limit becomes it (Power saver: 40 FPS, else
	 * none), and the game's limiter goes to Max FPS so only one cap applies.
	 */
	public static void migrateFrameLimit(GameOptions o) {
		com.coredex.altrender.settings.Config c = com.coredex.altrender.render.Renderer.config();
		if (c.maxFps() > 0) {
			return;
		}
		c.setMaxFps(o.fpsLimit == 2 ? 40 : 260);
		c.save();
		if (o.fpsLimit != 0) {
			o.fpsLimit = 0;
			o.save();
		}
	}

	@Override
	public List<Option<?>> general() {
		// Any distance from 2 to 32 chunks; the game's own four steps are named (owner, 2026-11-12).
		OptionStorage both = flags -> {
			o.save();
			com.coredex.altrender.render.Renderer.config().save();
		};
		Option<Integer> distance = Option.<Integer>builder("render_distance").name("options.renderDistance")
				.tooltip("altrender.options.render_distance.tooltip").impact(OptionImpact.HIGH)
				.control(new Control.Slider(RenderDistance.MIN, RenderDistance.MAX, 1, v -> {
					String step = RenderDistance.stepName(v);
					return v + " chunks" + (step != null ? " (" + Language.getInstance().translate(step) + ")" : "");
				}))
				.binding(() -> RenderDistance.chunks(o), v -> {
					com.coredex.altrender.render.Renderer.config().setRenderDistance(v);
					o.viewDistance = RenderDistance.stepFor(v);
				}).storage(both).build();
		// Sodium's frame cap, on Altrender's precise pacer; the game's own limiter stays at Max FPS.
		Option<Integer> fps = Option.<Integer>builder("max_framerate").name("altrender.options.max_framerate")
				.tooltip("altrender.options.max_framerate.tooltip").control(new Control.Slider(10, 260, 10,
						v -> v == 260 ? "altrender.options.unlimited" : v + " FPS"))
				.binding(() -> com.coredex.altrender.render.Renderer.config().maxFps(), v -> {
					com.coredex.altrender.render.Renderer.config().setMaxFps(v);
					o.fpsLimit = 0;
				}).storage(both).build();
		Option<Integer> gui = Option.<Integer>builder("gui_scale").name("options.guiScale")
				.tooltip("altrender.options.gui_scale.tooltip").control(new Control.Cycling<>(FOUR, v -> GUI_SCALES[v]))
				.binding(() -> o.guiScale, v -> o.guiScale = v).storage(storage).flags(OptionFlag.REQUIRES_LAYOUT)
				.build();
		Option<Boolean> fullscreen = Option.<Boolean>builder("fullscreen").name("altrender.options.fullscreen")
				.tooltip("altrender.options.fullscreen.tooltip").control(new Control.TickBox())
				.binding(() -> ((MinecraftUiAccess) mc).altrender$fullscreen(), v -> {
					if (v != ((MinecraftUiAccess) mc).altrender$fullscreen()) {
						mc.toggleFullscreen();
					}
				}).storage(storage).build();
		Option<Boolean> bobbing = Option.<Boolean>builder("view_bobbing").name("options.viewBobbing")
				.tooltip("altrender.options.view_bobbing.tooltip").control(new Control.TickBox())
				.binding(() -> o.viewBobbing, v -> o.viewBobbing = v).storage(storage).build();
		Option<Boolean> anaglyph = Option.<Boolean>builder("anaglyph").name("options.anaglyph")
				.tooltip("altrender.options.anaglyph.tooltip").impact(OptionImpact.HIGH).control(new Control.TickBox())
				.binding(() -> o.anaglyph, v -> o.anaglyph = v).storage(storage).flags(OptionFlag.REQUIRES_ASSET_RELOAD)
				.build();
		return List.of(distance, fps, gui, fullscreen, bobbing, anaglyph);
	}

	@Override
	public List<Option<?>> quality() {
		Option<Boolean> graphics = Option.<Boolean>builder("graphics").name("options.graphics")
				.tooltip("altrender.options.graphics.tooltip").impact(OptionImpact.MEDIUM)
				.control(new Control.Cycling<>(List.of(true, false), v -> v ? "options.graphics.fancy"
						: "options.graphics.fast"))
				.binding(() -> o.fancyGraphics, v -> o.fancyGraphics = v).storage(storage)
				.flags(OptionFlag.REQUIRES_RENDERER_RELOAD).build();
		Option<Boolean> smooth = Option.<Boolean>builder("smooth_lighting").name("options.ao")
				.tooltip("altrender.options.smooth_lighting.tooltip").impact(OptionImpact.LOW)
				.control(new Control.TickBox()).binding(() -> o.ambientOcclusion, v -> o.ambientOcclusion = v)
				.storage(storage).flags(OptionFlag.REQUIRES_RENDERER_RELOAD).build();
		Option<Boolean> advanced = Option.<Boolean>builder("advanced_opengl").name("options.advancedOpengl")
				.tooltip("altrender.options.advanced_opengl.tooltip").impact(OptionImpact.VARIES)
				.control(new Control.TickBox()).binding(() -> o.advancedOpengl, v -> o.advancedOpengl = v)
				.storage(storage).flags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.available(() -> Renderer.terrain() == null, () -> "altrender.options.advanced_opengl.unavailable")
				.build();
		return List.of(graphics, smooth, advanced);
	}

	@Override
	public void applyEffects(Set<OptionFlag> flags) {
		if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
			mc.textureManager.reload();
		}
		if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD) && mc.world != null) {
			mc.worldRenderer.reload();
		}
		if (flags.contains(OptionFlag.REQUIRES_LAYOUT) && mc.screen != null) {
			Window window = new Window(o, mc.width, mc.height);
			mc.screen.init(mc, window.getWidth(), window.getHeight());
		}
	}

	@Override
	public void setVsync(boolean on) {
		Display.setVSyncEnabled(on);
	}
}
