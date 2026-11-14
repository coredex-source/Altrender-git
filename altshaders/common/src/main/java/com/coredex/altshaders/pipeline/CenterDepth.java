/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/pathways/CenterDepthSampler.java and
 * common/src/main/resources/centerDepth.fsh at commit bff1e69c, licensed under LGPL-3.0 (the smoothing: its decay
 * from the half-life and the frame-time mix).
 * Modified by Coredex, 2026-11-13: two 1×1 sides read and written in turn, the mix worked out on the CPU.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.api.ShaderPrograms;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuFramebuffer;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;

/**
 * {@code centerDepthSmooth}, as Iris gives it: {@code depthtex0} at the screen's centre, smoothed over time with the
 * pack's {@code centerDepthHalflife}, kept on the GPU in a 1×1 R32F texture. Programs read that texture where they
 * declared the uniform ({@link SourcePrep}).
 */
final class CenterDepth {

	/** The sampler the sources read instead of {@code uniform float centerDepthSmooth}. */
	static final String SAMPLER = "altshaders_centerDepthSmooth";

	private static final int GL_R32F = 0x822E, GL_RED = 0x1903, GL_FLOAT = 0x1406;
	private static final int GL_NEAREST = 0x2600, GL_CLAMP_TO_EDGE = 0x812F;

	private final Gpu gpu;
	private final GpuTexture[] sides = new GpuTexture[2];
	private final GpuFramebuffer[] framebuffers = new GpuFramebuffer[2];
	final PackProgram program;
	/** Per second: Iris's {@code 1 / ((halfLife * 0.1) / ln 2)}. */
	private final double decay;
	private int current;
	private long last;

	CenterDepth(Gpu gpu, ShaderPrograms builder, float halfLife) {
		this.gpu = gpu;
		this.decay = Math.log(2) / Math.max(1e-4, halfLife * 0.1);
		ByteBuffer one = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
		one.putFloat(1f).flip();
		for (int i = 0; i < 2; i++) {
			sides[i] = gpu.createTexture(TextureSpec.of2d(GL_R32F, GL_RED, GL_FLOAT, 1, 1),
					"altshaders centerDepthSmooth " + i);
			sides[i].sampling(GL_NEAREST, GL_NEAREST, GL_CLAMP_TO_EDGE);
			sides[i].upload(0, 0, 0, 0, 1, 1, 1, one); // far until the first sample
			one.rewind();
			framebuffers[i] = gpu.createFramebuffer("altshaders centerDepthSmooth " + i);
			framebuffers[i].color(0, sides[i], 0);
			framebuffers[i].drawBuffers(0);
		}
		Map<ShaderPrograms.Stage, String> stages = new EnumMap<>(ShaderPrograms.Stage.class);
		stages.put(ShaderPrograms.Stage.VERTEX, """
				#version 330 compatibility
				void main() {
					gl_Position = ftransform();
				}
				""");
		stages.put(ShaderPrograms.Stage.FRAGMENT, """
				#version 330 compatibility
				uniform sampler2D altshadersDepth;
				uniform sampler2D altshadersPrevious;
				uniform float altshadersMix;
				out vec4 fragColor;
				void main() {
					float now = texture(altshadersDepth, vec2(0.5)).r;
					float before = texture(altshadersPrevious, vec2(0.5)).r;
					fragColor = vec4(mix(before, now, altshadersMix), 0.0, 0.0, 1.0);
				}
				""");
		program = PackProgram.internal("centerDepthSmooth",
				builder.build("altshaders centerDepthSmooth", stages, List.of(), List.of("fragColor")));
	}

	/** The smoothed depth programs read now. */
	GpuTexture read() {
		return sides[current];
	}

	/** Samples {@code depth} at the centre and moves the smoothed value toward it by the time since the last sample. */
	void sample(GpuTexture depth) {
		long now = System.nanoTime();
		float mix = last == 0 ? 1f : (float) (1 - Math.exp(-decay * (now - last) / 1e9));
		last = now;
		int write = current ^ 1;
		gpu.begin("altshaders centerDepthSmooth");
		gpu.bindFramebuffer(framebuffers[write]);
		gpu.viewport(0, 0, 1, 1);
		gpu.useProgram(program.program());
		gpu.blend(null);
		gpu.depth(0, false);
		gpu.colorMask(true, true, true, true);
		for (PackProgram.Sampler s : program.samplers) {
			gpu.bindTexture(s.unit(), s.name().equals("altshadersDepth") ? depth : sides[current]);
		}
		int l = program.location(gpu, "altshadersMix");
		if (l >= 0) {
			gpu.uniform1f(l, mix);
		}
		gpu.drawFullscreen();
		gpu.end();
		current = write;
	}

	void delete() {
		for (int i = 0; i < 2; i++) {
			framebuffers[i].delete();
			sides[i].delete();
		}
	}
}
