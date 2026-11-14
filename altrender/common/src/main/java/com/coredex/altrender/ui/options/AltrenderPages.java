/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui.options;

import java.util.ArrayList;
import java.util.List;

import com.coredex.altrender.api.options.Control;
import com.coredex.altrender.api.options.Option;
import com.coredex.altrender.api.options.OptionGroup;
import com.coredex.altrender.api.options.OptionImpact;
import com.coredex.altrender.api.options.OptionPage;
import com.coredex.altrender.api.options.OptionPageProvider;
import com.coredex.altrender.api.options.OptionStorage;
import com.coredex.altrender.caps.Feature;
import com.coredex.altrender.caps.GlCaps;
import com.coredex.altrender.caps.Tier;
import com.coredex.altrender.platform.GameVideoOptions;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.settings.Config;
import com.coredex.altrender.terrain.MeshExecutor;

/**
 * Altrender's pages (development.md 5.2): General and Quality (the game's options plus Altrender's), Performance
 * and Advanced (Altrender's: meshing, culling, frame caps, the renderer's GL version).
 */
public final class AltrenderPages implements OptionPageProvider {

	private final GameVideoOptions game;

	public AltrenderPages(GameVideoOptions game) {
		this.game = game;
	}

	@Override
	public String modId() {
		return "altrender";
	}

	@Override
	public String modName() {
		return "Altrender";
	}

	@Override
	public List<OptionPage> pages() {
		Config c = Renderer.config();
		OptionStorage config = flags -> c.save();
		List<OptionPage> pages = new ArrayList<>();

		List<Option<?>> general = new ArrayList<>(game.general());
		general.add(Option.<Boolean>builder("vsync").name("altrender.options.vsync")
				.tooltip("altrender.options.vsync.tooltip").impact(OptionImpact.VARIES).control(new Control.TickBox())
				.binding(c::vsync, v -> {
					c.setVsync(v);
					game.setVsync(v);
				}).storage(config).build());
		Option<Boolean> f3 = Option.<Boolean>builder("modern_debug_screen").name("altrender.options.modern_debug_screen")
				.tooltip("altrender.options.modern_debug_screen.tooltip").control(new Control.TickBox())
				.binding(c::modernDebugScreen, c::setModernDebugScreen).storage(config).build();
		Option<String> counter = Option.<String>builder("fps_counter").name("altrender.options.fps_counter")
				.tooltip("altrender.options.fps_counter.tooltip").impact(OptionImpact.LOW)
				.control(new Control.Cycling<>(List.of("off", "simple", "advanced"), v -> "altrender.options.fps_counter." + v))
				.binding(c::fpsCounter, c::setFpsCounter).storage(config).build();
		Option<Boolean> counterGpu = Option.<Boolean>builder("fps_counter_gpu").name("altrender.options.fps_counter_gpu")
				.tooltip("altrender.options.fps_counter_gpu.tooltip").control(new Control.TickBox())
				.binding(c::fpsCounterGpu, c::setFpsCounterGpu).storage(config).build();
		Option<String> corner = Option.<String>builder("fps_counter_corner").name("altrender.options.fps_counter_corner")
				.control(new Control.Cycling<>(List.of("top_left", "top_right", "bottom_left", "bottom_right"),
						v -> "altrender.options.corner." + v))
				.binding(c::fpsCounterCorner, c::setFpsCounterCorner).storage(config).build();
		Option<String> contrast = Option.<String>builder("fps_counter_contrast")
				.name("altrender.options.fps_counter_contrast").tooltip("altrender.options.fps_counter_contrast.tooltip")
				.control(new Control.Cycling<>(List.of("none", "background", "shadow"), v -> "altrender.options.contrast." + v))
				.binding(c::fpsCounterContrast, c::setFpsCounterContrast).storage(config).build();
		pages.add(new OptionPage("altrender.general", "altrender.options.page.general",
				List.of(OptionGroup.of(general), OptionGroup.of(counter, counterGpu, corner, contrast), OptionGroup.of(f3))));

		pages.add(new OptionPage("altrender.quality", "altrender.options.page.quality",
				List.of(OptionGroup.of(game.quality()))));

		int maxThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
		Option<Integer> threads = Option.<Integer>builder("mesh_threads").name("altrender.options.mesh_threads")
				.tooltip("altrender.options.mesh_threads.tooltip").impact(OptionImpact.HIGH)
				.control(new Control.Slider(0, maxThreads, 1, v -> v == 0
						? "Auto (" + MeshExecutor.automaticThreads() + ")" : String.valueOf(v)))
				.binding(c::meshThreads, v -> {
					c.setMeshThreads(v);
					Renderer.rebuildTerrain();
				}).storage(config).build();
		Option<Integer> budget = Option.<Integer>builder("upload_budget").name("altrender.options.upload_budget")
				.tooltip("altrender.options.upload_budget.tooltip").impact(OptionImpact.MEDIUM)
				.control(new Control.Slider(2, 64, 2, v -> v + " MB")).binding(c::uploadBudgetMb, c::setUploadBudgetMb)
				.storage(config).build();
		Option<Boolean> cave = Option.<Boolean>builder("cave_culling").name("altrender.options.cave_culling")
				.tooltip("altrender.options.cave_culling.tooltip").impact(OptionImpact.HIGH)
				.control(new Control.TickBox()).binding(c::caveCullingSetting, c::setCaveCulling).storage(config).build();
		Option<Boolean> entity = Option.<Boolean>builder("entity_culling").name("altrender.options.entity_culling")
				.tooltip("altrender.options.entity_culling.tooltip").impact(OptionImpact.MEDIUM)
				.control(new Control.TickBox()).binding(c::entityCullingSetting, c::setEntityCulling).storage(config)
				.build();
		Option<Integer> idle = Option.<Integer>builder("idle_fps").name("altrender.options.idle_fps")
				.tooltip("altrender.options.idle_fps.tooltip").impact(OptionImpact.LOW)
				.control(new Control.Slider(0, 60, 5, AltrenderPages::fpsCap)).binding(c::idleFps, c::setIdleFps)
				.storage(config).build();
		Option<Integer> unfocused = Option.<Integer>builder("unfocused_fps").name("altrender.options.unfocused_fps")
				.tooltip("altrender.options.unfocused_fps.tooltip").impact(OptionImpact.LOW)
				.control(new Control.Slider(0, 60, 5, AltrenderPages::fpsCap))
				.binding(c::unfocusedFps, c::setUnfocusedFps).storage(config).build();
		Option<Boolean> stream = Option.<Boolean>builder("immediate_stream").name("altrender.options.immediate_stream")
				.tooltip("altrender.options.immediate_stream.tooltip").impact(OptionImpact.VARIES)
				.control(new Control.TickBox()).binding(c::immediateStreamSetting, c::setImmediateStream)
				.storage(config).build();
		pages.add(new OptionPage("altrender.performance", "altrender.options.page.performance", List.of(
				OptionGroup.of(threads, budget), OptionGroup.of(cave, entity), OptionGroup.of(idle, unfocused),
				OptionGroup.of(stream))));

		Renderer r = Renderer.instance();
		GlCaps caps = r != null ? r.caps() : null;
		boolean gl45 = caps != null && caps.gl45();
		String gl45Name = caps != null && caps.atLeast(4, 6) ? "OpenGL 4.6" : "OpenGL 4.5";
		Option<String> renderer = Option.<String>builder("renderer").name("altrender.options.renderer")
				.tooltip("altrender.options.renderer.tooltip").impact(OptionImpact.VARIES)
				.control(new Control.Cycling<>(List.of("gl45", "gl33"), v -> v.equals("gl45") ? gl45Name : "OpenGL 3.3",
						v -> gl45 || v.equals("gl33")))
				.binding(() -> r != null && r.tier().tier() == Tier.GL45 ? "gl45" : "gl33", v -> {
					c.setTier(v.equals("gl33") ? "gl33" : "auto");
					Renderer.switchTier(v.equals("gl33") ? Tier.GL33 : Tier.GL45);
				}).storage(config)
				.available(() -> gl45, () -> caps == null ? null
						: "This driver lacks OpenGL 4.5 (missing: " + String.join(", ", caps.gl45Missing()) + ").")
				.build();
		boolean indirectCount = r != null && r.strategies().has(Feature.INDIRECT_COUNT)
				&& r.strategies().has(Feature.MULTI_DRAW_INDIRECT);
		Option<Boolean> gpuCommands = Option.<Boolean>builder("gpu_commands").name("altrender.options.gpu_commands")
				.tooltip("altrender.options.gpu_commands.tooltip").impact(OptionImpact.VARIES)
				.control(new Control.TickBox()).binding(c::gpuCommandsSetting, v -> {
					c.setGpuCommands(v);
					Renderer.rebuildTerrain();
				}).storage(config)
				.available(() -> indirectCount, () -> "Needs OpenGL 4.6 indirect count (the OpenGL 4.5+ renderer).")
				.build();
		pages.add(new OptionPage("altrender.advanced", "altrender.options.page.advanced",
				List.of(OptionGroup.of(renderer), OptionGroup.of(gpuCommands))));
		return pages;
	}

	private static String fpsCap(int v) {
		return v == 0 ? "altrender.options.no_limit" : v + " FPS";
	}
}
