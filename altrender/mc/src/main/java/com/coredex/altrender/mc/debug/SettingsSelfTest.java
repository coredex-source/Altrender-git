/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.coredex.altrender.caps.Tier;
import com.coredex.altrender.render.Renderer;
import com.coredex.altrender.ui.options.OptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;

/**
 * development.md 5.2's check, scripted (the harness, {@code -Daltrender.debug.settingsTest}): every option of the
 * game's own Video Settings, clicked and applied in Altrender's screen, changes the game's value (and back), and the
 * renderer switch changes the tier. Fullscreen is left out (it would resize the capture): the owner's check.
 */
public final class SettingsSelfTest {

	private SettingsSelfTest() {
	}

	public static List<String> run(Minecraft mc, OptionsScreen screen) {
		List<String> failed = new ArrayList<>();
		GameOptions o = mc.options;
		screen.selectPage("altrender.general");
		// Render distance and the frame cap are sliders now (owner): a value set and applied takes effect.
		slider(failed, screen, mc, "render_distance", 4, () -> o.viewDistance == 2
				&& com.coredex.altrender.mc.ui.RenderDistance.chunks(o) == 4);
		slider(failed, screen, mc, "render_distance", 13, () -> o.viewDistance == 0
				&& com.coredex.altrender.mc.ui.RenderDistance.chunks(o) == 13);
		slider(failed, screen, mc, "render_distance", 20, () -> o.viewDistance == 0
				&& com.coredex.altrender.mc.ui.RenderDistance.fogBlocks(o) == 320f);
		slider(failed, screen, mc, "max_framerate", 60, () -> Renderer.config().maxFps() == 60 && o.fpsLimit == 0);
		slider(failed, screen, mc, "max_framerate", 260, () -> Renderer.config().maxFps() == 260);
		Renderer.config().setRenderDistance(0); // back to the game's own step (the harness scene's)
		o.viewDistance = 1;
		check(failed, screen, "gui_scale", () -> o.guiScale, 4);
		check(failed, screen, "view_bobbing", () -> o.viewBobbing, 2);
		check(failed, screen, "anaglyph", () -> o.anaglyph, 2);
		screen.selectPage("altrender.quality");
		check(failed, screen, "graphics", () -> o.fancyGraphics, 2);
		check(failed, screen, "smooth_lighting", () -> o.ambientOcclusion, 2);
		// Advanced OpenGL is shown but unavailable while Altrender draws the terrain: clicking does nothing.
		boolean before = o.advancedOpengl;
		screen.clickOption("advanced_opengl");
		screen.applyChanges();
		if (o.advancedOpengl != before) {
			failed.add("advanced_opengl changed although unavailable");
		}
		// The renderer: to OpenGL 3.3 and back.
		screen.selectPage("altrender.advanced");
		Tier start = Renderer.instance().tier().tier();
		if (start == Tier.GL45) {
			screen.clickOption("renderer");
			screen.applyChanges();
			if (Renderer.instance().tier().tier() != Tier.GL33) {
				failed.add("renderer switch to gl33 (" + Renderer.instance().tier().tier() + ")");
			}
			screen.clickOption("renderer");
			screen.applyChanges();
			if (Renderer.instance().tier().tier() != Tier.GL45) {
				failed.add("renderer switch back to gl45 (" + Renderer.instance().tier().tier() + ")");
			}
		}
		screen.selectPage("altrender.general");
		return failed;
	}

	/** Sets a slider option on the page shown to {@code value}, applies, checks. */
	@SuppressWarnings("unchecked")
	private static void slider(List<String> failed, OptionsScreen screen, Minecraft mc, String id, int value,
			java.util.function.BooleanSupplier ok) {
		for (com.coredex.altrender.ui.options.ControlElement<?> e : screen.page().elements()) {
			if (e.option().id().equals(id)) {
				((com.coredex.altrender.api.options.Option<Integer>) e.option()).setValue(value);
				screen.applyChanges();
				if (!ok.getAsBoolean()) {
					failed.add(id + " = " + value);
				}
				return;
			}
		}
		failed.add(id + " missing");
	}

	/** Clicks an option, applies, expects a new value; clicks through the rest ({@code values} in all) back to the start. */
	private static void check(List<String> failed, OptionsScreen screen, String id, Supplier<Object> value, int values) {
		Object start = value.get();
		if (!screen.clickOption(id)) {
			failed.add(id + " missing");
			return;
		}
		screen.applyChanges();
		Object after = value.get();
		if (after.equals(start)) {
			failed.add(id + " unchanged (" + start + ")");
		}
		for (int i = 1; i < values; i++) {
			screen.clickOption(id);
			screen.applyChanges();
		}
		if (!value.get().equals(start)) {
			failed.add(id + " not back (" + start + " -> " + value.get() + ")");
		}
	}
}
