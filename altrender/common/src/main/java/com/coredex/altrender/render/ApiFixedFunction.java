/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.render;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.render.FixedFunction;
import com.coredex.altrender.api.render.WorldPipeline;
import com.coredex.altrender.gl.GlStateTracker;

/**
 * The API's {@link FixedFunction} on the tracker, and the tracker listener that tells the world pipeline about
 * changes while the world renders (development.md 6.1).
 */
final class ApiFixedFunction implements FixedFunction, GlStateTracker.Listener {

	private static final int GL_TEXTURE_2D = 0x0DE1;
	private static final int GL_FOG = 0x0B60;
	private static final int GL_LIGHTING = 0x0B50;
	private static final int GL_ALPHA_TEST = 0x0BC0;

	/** Listen while a pipeline is drawing the world. */
	void listen(boolean on) {
		GlStateTracker.listener(on ? this : null);
	}

	@Override
	public void enabled(int cap, boolean on) {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null && !com.coredex.altrender.strategy.Strategies.inPass()) { // not Altrender's own changes
			p.fixedFunction(switch (cap) {
				case GL_FOG -> Change.FOG;
				case GL_LIGHTING -> Change.LIGHTING;
				case GL_ALPHA_TEST -> Change.ALPHA_TEST;
				default -> Change.TEXTURE;
			});
		}
	}

	@Override
	public void fogParameter() {
		WorldPipeline p = AltrenderApi.worldPipeline();
		if (p != null && !com.coredex.altrender.strategy.Strategies.inPass()) {
			p.fixedFunction(Change.FOG_PARAMETER);
		}
	}

	@Override
	public boolean texture() {
		return GlStateTracker.isEnabled(GL_TEXTURE_2D);
	}

	@Override
	public boolean fog() {
		return GlStateTracker.isEnabled(GL_FOG);
	}

	@Override
	public int fogMode() {
		return GlStateTracker.fogMode();
	}

	@Override
	public float fogStart() {
		return GlStateTracker.fogStart();
	}

	@Override
	public float fogEnd() {
		return GlStateTracker.fogEnd();
	}

	@Override
	public float fogDensity() {
		return GlStateTracker.fogDensity();
	}

	@Override
	public void fogColor(float[] out) {
		GlStateTracker.fogColor(out);
	}

	@Override
	public int fogDistanceMode() {
		return GlStateTracker.fogDistanceMode();
	}

	@Override
	public boolean lighting() {
		return GlStateTracker.isEnabled(GL_LIGHTING);
	}

	@Override
	public float alphaRef() {
		return GlStateTracker.alphaRef();
	}

	@Override
	public boolean alphaTest() {
		return GlStateTracker.isEnabled(GL_ALPHA_TEST);
	}
}
