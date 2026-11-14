/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shadows/ShadowMatrices.java at commit bff1e69c, licensed under LGPL-3.0
 * (the shadow camera: its rotations, grid snapping and projections).
 * Modified by Coredex, 2026-11-13: column-major float arrays, no Minecraft types.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pipeline;

import java.util.HashMap;
import java.util.Map;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.GameState;
import com.coredex.altrender.api.gpu.Gpu;
import com.coredex.altrender.api.gpu.GpuFramebuffer;
import com.coredex.altrender.api.gpu.GpuTexture;
import com.coredex.altrender.api.gpu.TextureSpec;
import com.coredex.altrender.api.render.TerrainPipeline;
import com.coredex.altrender.api.render.TerrainView;
import com.coredex.altshaders.pack.gl.InternalTextureFormat;
import com.coredex.altshaders.pack.gl.ShaderDataType;
import com.coredex.altshaders.pack.helpers.Vector4f;
import com.coredex.altshaders.pack.properties.PackDirectives;
import com.coredex.altshaders.pack.properties.PackShadowDirectives;

/**
 * A pack's shadow pass (development.md 6.7): the shadow camera from the sun or moon (Iris's), its targets
 * ({@code shadowtex0}, {@code shadowtex1} = the depth before translucents, {@code shadowcolor0…}), and the terrain drawn
 * into them through Altrender's terrain view with the pack's shadow programs.
 */
final class ShadowPass {

	private static final int GL_DEPTH_COMPONENT24 = 0x81A6, GL_DEPTH_COMPONENT = 0x1902, GL_FLOAT = 0x1406;
	private static final int GL_LINEAR = 0x2601, GL_NEAREST = 0x2600, GL_CLAMP_TO_EDGE = 0x812F;
	private static final int GL_INT = 0x1404, GL_UNSIGNED_INT = 0x1405;

	private final Gpu gpu;
	private final PackShadowDirectives directives;
	private final PackDirectives packDirectives;
	final int resolution;
	final GpuTexture depth0;
	final GpuTexture depth1;
	private final GpuTexture[] colors = new GpuTexture[8];
	/** Each shadowcolor's second side, made when a shadowcomp pass first writes it (passes read one, write the other). */
	private final GpuTexture[] alts = new GpuTexture[8];
	/** Which side of each shadowcolor holds its picture now (true: the second); the shadow pass resets it. */
	private final boolean[] flipped = new boolean[8];
	/** Per shadowcolor texture (main, then alt): not cleared yet (Iris's full clear, whatever shadowcolorNClear). */
	private final boolean[] fullClear = new boolean[16];
	private final Map<PackProgram, GpuFramebuffer> framebuffers = new HashMap<>();
	/** A shadowcomp pass's framebuffer per flip state of the buffers it writes. */
	private final Map<String, GpuFramebuffer> compositeFramebuffers = new HashMap<>();
	private final GpuFramebuffer clearFramebuffer;
	/** The opaque layer's cut-out quads (all of it without {@link #solidPart}). */
	final PackProgram solid;
	/** Its solid quads, without AltShaders' alpha test (the pack's shadow solid program), or null. */
	PackProgram solidPart;
	final PackProgram translucent;
	/** For the game's entities in the shadow map (their own vertex inputs, not the terrain's), or null. */
	PackProgram entities;
	/** True while the game draws its entities for the shadow map (the pipeline ignores state changes then). */
	boolean drawingEntities;
	/**
	 * Binds a shadow program's own textures and images for the draws (the pipeline's, as for its gbuffers programs):
	 * a pack that voxelizes in its shadow programs writes images there (Refrax's colored light).
	 */
	java.util.function.Consumer<PackProgram> textures = p -> { };
	final float[] modelView = Mat4.identity();
	final float[] projection = Mat4.identity();
	private final double[] camera = new double[3];

	ShadowPass(Gpu gpu, PackDirectives packDirectives, PackProgram solid, PackProgram translucent) {
		this.gpu = gpu;
		this.packDirectives = packDirectives;
		this.directives = packDirectives.getShadowDirectives();
		this.solid = solid;
		this.translucent = translucent;
		this.resolution = Math.max(16, directives.getResolution());
		depth0 = depthTexture("shadowtex0");
		depth1 = depthTexture("shadowtex1");
		clearFramebuffer = gpu.createFramebuffer("altshaders shadow clear");
		clearFramebuffer.depth(depth0);
		clearFramebuffer.drawBuffers();
	}

	private GpuTexture depthTexture(String name) {
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_FLOAT, resolution,
				resolution), "altshaders " + name);
		t.sampling(GL_NEAREST, GL_NEAREST, GL_CLAMP_TO_EDGE);
		return t;
	}

	/** shadowcolor{@code i} as passes read it now (the side holding its picture), made on first use. */
	GpuTexture color(int i) {
		if (i < 0 || i >= colors.length) {
			return null;
		}
		main(i);
		return flipped[i] ? alt(i) : colors[i];
	}

	/** The side the shadow pass draws into. */
	private GpuTexture main(int i) {
		if (colors[i] == null) {
			colors[i] = colorTexture(i, "");
			fullClear[i] = true;
		}
		return colors[i];
	}

	private GpuTexture alt(int i) {
		if (alts[i] == null) {
			alts[i] = colorTexture(i, " (alt)");
			fullClear[8 + i] = true;
		}
		return alts[i];
	}

	/** shadowcolor{@code i}'s format. */
	InternalTextureFormat colorFormat(int i) {
		PackShadowDirectives.SamplingSettings s = directives.getColorSamplingSettings().get(i);
		return s != null && s.getFormat() != null ? s.getFormat() : InternalTextureFormat.RGBA;
	}

	/** shadowcolor{@code i}'s texture in the pack's format. */
	private GpuTexture colorTexture(int i, String side) {
		PackShadowDirectives.SamplingSettings s = directives.getColorSamplingSettings().get(i);
		InternalTextureFormat format = s != null && s.getFormat() != null ? s.getFormat() : InternalTextureFormat.RGBA;
		int type = switch (format.getShaderDataType()) {
			case FLOAT -> GL_FLOAT;
			case INT -> GL_INT;
			case UINT -> GL_UNSIGNED_INT;
		};
		GpuTexture t = gpu.createTexture(TextureSpec.of2d(format.getGlFormat(), format.getPixelFormat().getGlFormat(),
				type, resolution, resolution), "altshaders shadowcolor" + i + side);
		// Iris reads every shadowcolor through a linear sampler, whatever shadowcolorNNearest and its mipmaps (nearest
		// for integer formats, which can't filter).
		boolean nearest = format.getShaderDataType() != ShaderDataType.FLOAT;
		t.sampling(nearest ? GL_NEAREST : GL_LINEAR, nearest ? GL_NEAREST : GL_LINEAR, GL_CLAMP_TO_EDGE);
		return t;
	}

	/**
	 * A shadowcomp pass's framebuffer (Iris's shadow composite): each buffer it writes on the side not read now.
	 */
	GpuFramebuffer compositeFramebuffer(PackProgram p) {
		int[] buffers = p.drawBuffers();
		StringBuilder key = new StringBuilder(p.name);
		for (int b : buffers) {
			key.append(b < 0 || b >= colors.length ? '-' : flipped[b] ? '1' : '0');
		}
		return compositeFramebuffers.computeIfAbsent(key.toString(), k -> {
			GpuFramebuffer fb = gpu.createFramebuffer("altshaders " + p.name);
			int[] attachments = new int[buffers.length];
			for (int i = 0; i < buffers.length; i++) {
				int b = buffers[i];
				if (b >= 0 && b < colors.length) {
					main(b);
					fb.color(i, flipped[b] ? colors[b] : alt(b), 0);
				}
				attachments[i] = i;
			}
			fb.drawBuffers(attachments);
			return fb;
		});
	}

	/** After a shadowcomp pass: what it wrote is what the next ones read, unless the pack says otherwise. */
	void flipAfter(PackProgram p) {
		Map<Integer, Boolean> flips = p.directives.getExplicitFlips();
		int[] buffers = p.drawBuffers();
		for (int b : buffers) {
			if (b >= 0 && b < colors.length && flips.getOrDefault(b, true)) {
				flipped[b] ^= true;
			}
		}
		// As Iris: then each buffer with flip.<pass>.<buffer>=true flips (again, if the pass wrote it).
		preFlips(flips);
	}

	/** {@code flips}' buffers set to true change sides (the shadowcomp stage's {@code flip.shadowcomp_pre.*}). */
	void preFlips(Map<Integer, Boolean> flips) {
		flips.forEach((b, flip) -> {
			if (flip && b >= 0 && b < colors.length) {
				main(b);
				alt(b);
				flipped[b] ^= true;
			}
		});
	}

	private GpuFramebuffer framebuffer(PackProgram p) {
		return framebuffers.computeIfAbsent(p, k -> {
			GpuFramebuffer fb = gpu.createFramebuffer("altshaders shadow " + p.name);
			int[] buffers = p.drawBuffers();
			int[] attachments = new int[buffers.length];
			for (int i = 0; i < buffers.length; i++) {
				fb.color(i, main(buffers[i]), 0);
				attachments[i] = i;
			}
			fb.depth(depth0);
			fb.drawBuffers(attachments);
			return fb;
		});
	}

	/** The shadow camera for this frame (before the uniforms are worked out, which give it to the programs). */
	void matrices() {
		GameState g = AltrenderApi.gameState();
		if (g == null) {
			return;
		}
		g.cameraPosition(camera);
		float sky = g.skyAngle();
		float sunAngle = sky < 0.75f ? sky + 0.25f : sky - 0.75f;
		float shadowAngle = sunAngle > 0.5f ? sunAngle - 0.5f : sunAngle;
		// Iris's baseline: X 90°, then the sky's turn about Z, then the sun path's tilt about X.
		float skyAngle = shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
		float[] m = Mat4.identity();
		rotate(m, 90, 1, 0, 0);
		rotate(m, skyAngle * -360f, 0, 0, 1);
		rotate(m, packDirectives.getSunPathRotation(), 1, 0, 0);
		// Snapped to the shadow interval's grid, so the map doesn't swim as the camera moves.
		float interval = directives.getIntervalSize();
		if (Math.abs(interval) != 0) {
			float half = interval / 2f;
			translate(m, (float) camera[0] % interval - half, (float) camera[1] % interval - half,
					(float) camera[2] % interval - half);
		}
		System.arraycopy(m, 0, modelView, 0, 16);
		Float fov = directives.getFov();
		if (fov != null) {
			perspective(fov, projection);
		} else {
			ortho(directives.getDistance(), directives.getNearPlane(), directives.getFarPlane(), projection);
		}
	}

	/**
	 * Clears the targets for the frame (Iris: at the frame's start, before the begin passes): the depth, and each
	 * shadowcolor on both sides if the pack clears it, else only once after it's made.
	 */
	void clear() {
		java.util.Arrays.fill(flipped, false); // the shadow programs draw into the first sides
		gpu.begin("altshaders shadow clear");
		gpu.bindFramebuffer(clearFramebuffer);
		gpu.viewport(0, 0, resolution, resolution);
		gpu.clearDepth(1f);
		for (int k = 0; k < 16; k++) {
			int i = k & 7;
			GpuTexture t = k < 8 ? colors[i] : alts[i];
			if (t == null) {
				continue;
			}
			PackShadowDirectives.SamplingSettings s = directives.getColorSamplingSettings().get(i);
			if (s != null && !s.getClear() && !fullClear[k]) {
				continue;
			}
			fullClear[k] = false;
			Vector4f c = s != null && s.getClearColor() != null ? s.getClearColor() : new Vector4f(1, 1, 1, 1);
			gpu.bindFramebuffer(colorClearFramebuffer(k, t));
			if (isInteger(i)) {
				gpu.clearColorInt(0, (int) c.x, (int) c.y, (int) c.z, (int) c.w);
			} else {
				gpu.clearColor(0, c.x, c.y, c.z, c.w);
			}
		}
		gpu.end();
	}

	/** Each shadowcolor texture's clear framebuffer, made once (7.4: no framebuffer made and deleted each frame). */
	private final GpuFramebuffer[] colorClears = new GpuFramebuffer[16];

	private GpuFramebuffer colorClearFramebuffer(int k, GpuTexture t) {
		if (colorClears[k] == null) {
			GpuFramebuffer fb = gpu.createFramebuffer("altshaders shadowcolor clear");
			fb.color(0, t, 0);
			fb.drawBuffers(0);
			colorClears[k] = fb;
		}
		return colorClears[k];
	}

	private boolean isInteger(int i) {
		PackShadowDirectives.SamplingSettings s = directives.getColorSamplingSettings().get(i);
		InternalTextureFormat format = s != null && s.getFormat() != null ? s.getFormat() : InternalTextureFormat.RGBA;
		return format.getPixelFormat().isInteger();
	}

	/** Draws the terrain (solid, a copy for {@code shadowtex1}, then translucent), then makes the mipmaps asked for. */
	void render(Uniforms uniforms) {
		renderTerrain(uniforms);
		mipmaps();
	}

	/**
	 * Iris's shadow mipmaps ({@code shadowtexNMipmap}, {@code shadowcolorNMipmap}), after the shadow pass. The depth
	 * textures are then sampled with mipmap filtering ({@link #depthFilter}); shadowcolors are read through a linear
	 * sampler without mipmaps, as Iris (its mipmaps aren't seen).
	 */
	private void mipmaps() {
		var depthSettings = directives.getDepthSamplingSettings();
		for (int i = 0; i < 2 && i < depthSettings.size(); i++) {
			if (depthSettings.get(i).getMipmap()) {
				(i == 0 ? depth0 : depth1).generateMipmaps();
			}
		}
	}

	/** shadowtex{@code which}'s min and mag filters for plain or comparing samplers (Iris's shadow samplers). */
	int[] depthFilter(int which) {
		var list = directives.getDepthSamplingSettings();
		boolean linear = which >= list.size() || !list.get(which).getNearest();
		boolean mip = which < list.size() && list.get(which).getMipmap();
		int min = mip ? (linear ? 0x2703 : 0x2702) : linear ? GL_LINEAR : GL_NEAREST; // *_MIPMAP_LINEAR
		return new int[] {min, linear ? GL_LINEAR : GL_NEAREST};
	}

	private void renderTerrain(Uniforms uniforms) {
		TerrainView view = AltrenderApi.terrainView();
		float mul = directives.getDistanceRenderMul();
		GameState g = AltrenderApi.gameState();
		float distance = mul > 0 ? directives.getDistance() * mul : (g != null ? g.renderDistance() * 16f : 256f);
		culling = culling(g != null ? g.renderDistance() * 16f : 256f);
		// As Iris: the opaque terrain if shadowTerrain, the entities as their settings say, the copy for shadowtex1,
		// then the translucent terrain if shadowTranslucent (whatever shadowTerrain).
		if (view != null && directives.shouldRenderTerrain()) {
			// The opaque layer in two parts: solid quads (no alpha test: early depth test), then cut-out ones. With
			// shadow back-face culling on, the solid quads' faces turned from the light are left out (a closed opaque
			// shape's back is never the nearest to the light, so the map stays the same); cut-outs (leaves, plants)
			// and the translucent layer keep both sides, or light would pass through their clear texels.
			com.coredex.altrender.api.render.ViewCulling all = culling;
			culling = all.withBackFaces(com.coredex.altshaders.ShadersConfig.shadowBackFaces());
			draw(view, uniforms, solidPart != null ? solidPart : solid, TerrainPipeline.Pass.SOLID, distance);
			culling = all;
			draw(view, uniforms, solid, TerrainPipeline.Pass.CUTOUT, distance);
		}
		drawEntities(uniforms);
		gpu.begin("altshaders shadowtex1");
		gpu.copyDepth(solid != null ? framebuffer(solid) : clearFramebuffer, depth1);
		gpu.end();
		if (view != null && directives.shouldRenderTranslucent()) {
			draw(view, uniforms, translucent, TerrainPipeline.Pass.TRANSLUCENT, distance);
		}
	}

	private void draw(TerrainView view, Uniforms uniforms, PackProgram p, TerrainPipeline.Pass pass, float distance) {
		if (p == null || p.program() == 0) {
			return;
		}
		gpu.begin("altshaders " + p.name);
		gpu.useProgram(p.program());
		uniforms.apply(gpu, p);
		uniforms.alphaTest(gpu, p, 0.1f); // Iris's shadow cut-outs
		gpu.end();
		textures.accept(p);
		// The program's blend.shadow* (Iris's default for shadow programs: off), whole and per buffer.
		view.draw(framebuffer(p), 0, 0, resolution, resolution, modelView, projection, camera[0], camera[1], camera[2],
				distance, pass, p.program(), PackPipeline.blendOf(p), PackPipeline.bufferBlendsOf(p), culling);
	}

	/** This frame's shadow culling for the terrain. */
	private com.coredex.altrender.api.render.ViewCulling culling = com.coredex.altrender.api.render.ViewCulling.NONE;

	/**
	 * Iris's shadow culling: {@code shadow.culling=false} (or the default for a pack that voxelizes the world in its
	 * shadow programs) culls by distance only ({@code shadowDistance × shadowRenderDistanceMul}, when that is within the
	 * render distance); otherwise only sections whose shadow can fall into the player's view are drawn, within that
	 * distance. {@code reversed} (voxelizing packs that want everything near) keeps everything within the shadow and
	 * voxel distances.
	 */
	private com.coredex.altrender.api.render.ViewCulling culling(float renderBlocks) {
		com.coredex.altshaders.pack.properties.ShadowCullState state = directives.getCullingState();
		if (state == null) {
			state = com.coredex.altshaders.pack.properties.ShadowCullState.DEFAULT;
		}
		float half = directives.getDistance();
		float mul = directives.getDistanceRenderMul();
		boolean voxels = packDirectives.shouldVoxelizeLightBlocks() || hasImages(solid) || hasImages(solidPart)
				|| hasImages(translucent)
				|| hasImages(entities);
		if (state == com.coredex.altshaders.pack.properties.ShadowCullState.DISTANCE
				|| state == com.coredex.altshaders.pack.properties.ShadowCullState.DEFAULT && voxels) {
			float d = half * mul;
			return d <= 0 || d > renderBlocks ? com.coredex.altrender.api.render.ViewCulling.NONE
					: new com.coredex.altrender.api.render.ViewCulling(d, null, 0);
		}
		if (state == com.coredex.altshaders.pack.properties.ShadowCullState.SAFE_ZONE) {
			float m = mul < 0 ? 1 : mul;
			float d = Math.max(half, directives.getVoxelDistance()) * m;
			return new com.coredex.altrender.api.render.ViewCulling(d < renderBlocks ? d : 0, null, 0);
		}
		float d = mul < 0 ? renderBlocks : half * mul;
		// The direction to the light: the shadow camera looks down its -z, so +z, back in world axes.
		float[] toLight = {modelView[2], modelView[6], modelView[10]};
		float len = (float) Math.sqrt(toLight[0] * toLight[0] + toLight[1] * toLight[1] + toLight[2] * toLight[2]);
		if (len < 1e-6f) {
			return new com.coredex.altrender.api.render.ViewCulling(d < renderBlocks ? d : 0, null, 0);
		}
		toLight[0] /= len;
		toLight[1] /= len;
		toLight[2] /= len;
		return new com.coredex.altrender.api.render.ViewCulling(d < renderBlocks ? d : 0, toLight, 0);
	}

	private static boolean hasImages(PackProgram p) {
		return p != null && !p.images.isEmpty();
	}

	/** The game's entities and block entities, as the pack's settings ask (shadowEntities). */
	private void drawEntities(Uniforms uniforms) {
		com.coredex.altrender.api.render.EntityView view = AltrenderApi.entityView();
		boolean world = directives.shouldRenderEntities();
		// Iris's shadowPlayer: the player even in first person (-Daltshaders.debug.noShadowPlayer: off, for checks).
		// With entity shadows on, Iris draws every entity, the player in first person included (shadowPlayer only adds
		// the player when they're off).
		boolean player = (directives.shouldRenderPlayer() || world)
				&& !Boolean.getBoolean("altshaders.debug.noShadowPlayer");
		if (view == null || entities == null || entities.program() == 0 || !world && !player) {
			return;
		}
		gpu.begin("altshaders " + entities.name);
		gpu.useProgram(entities.program());
		uniforms.apply(gpu, entities);
		uniforms.alphaTest(gpu, entities, 0.1f);
		gpu.end();
		textures.accept(entities);
		float mul = directives.getEntityShadowDistanceMul();
		float distance = directives.getDistance() * (mul > 0 ? mul : 1);
		drawingEntities = true;
		// The game draws them: its blend calls give way to the program's (as for the gbuffers).
		gpu.gameBlend(PackPipeline.blendOf(entities));
		gpu.gameBufferBlends(PackPipeline.bufferBlendsOf(entities));
		try {
			view.draw(framebuffer(entities), 0, 0, resolution, resolution, modelView, projection, camera[0], camera[1],
					camera[2], distance, entities.program(), world, player);
		} finally {
			drawingEntities = false;
			gpu.gameBufferBlends(null);
			gpu.gameBlend(null);
		}
	}

	void delete() {
		for (GpuFramebuffer fb : framebuffers.values()) {
			fb.delete();
		}
		for (GpuFramebuffer fb : compositeFramebuffers.values()) {
			fb.delete();
		}
		clearFramebuffer.delete();
		for (GpuFramebuffer fb : colorClears) {
			if (fb != null) {
				fb.delete();
			}
		}
		depth0.delete();
		depth1.delete();
		for (GpuTexture t : colors) {
			if (t != null) {
				t.delete();
			}
		}
		for (GpuTexture t : alts) {
			if (t != null) {
				t.delete();
			}
		}
	}

	// ---- matrices (column-major) --------------------------------------------------------------------------------

	private static void ortho(float halfPlane, float near, float far, float[] out) {
		java.util.Arrays.fill(out, 0);
		out[0] = 1f / halfPlane;
		out[5] = 1f / halfPlane;
		out[10] = 2f / (near - far);
		out[14] = (far + near) / (near - far);
		out[15] = 1;
	}

	private static void perspective(float fov, float[] out) {
		float near = PackShadowDirectives.DEFAULT_NEAR;
		float far = PackShadowDirectives.DEFAULT_FAR;
		float y = (float) (1.0 / Math.tan(Math.toRadians(fov) * 0.5));
		java.util.Arrays.fill(out, 0);
		out[0] = y;
		out[5] = y;
		out[10] = (far + near) / (near - far);
		out[11] = -1;
		out[14] = 2f * far * near / (near - far);
		out[15] = 1;
	}

	/** {@code m = m · R(angle°, axis)}. */
	private static void rotate(float[] m, float angle, float x, float y, float z) {
		double r = Math.toRadians(angle);
		float c = (float) Math.cos(r);
		float s = (float) Math.sin(r);
		float t = 1 - c;
		float[] rot = {
				t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0,
				t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0,
				t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0,
				0, 0, 0, 1};
		multiply(m, rot);
	}

	/** {@code m = m · T(x, y, z)}. */
	private static void translate(float[] m, float x, float y, float z) {
		multiply(m, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1});
	}

	private static void multiply(float[] m, float[] r) {
		float[] o = new float[16];
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				float v = 0;
				for (int k = 0; k < 4; k++) {
					v += m[k * 4 + row] * r[col * 4 + k];
				}
				o[col * 4 + row] = v;
			}
		}
		System.arraycopy(o, 0, m, 0, 16);
	}
}
