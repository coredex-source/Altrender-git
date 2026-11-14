/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.coredex.altrender.strategy.BlendFactor;
import com.coredex.altrender.strategy.Compare;
import com.coredex.altrender.strategy.GlState;
import com.coredex.altrender.strategy.Primitive;
import com.coredex.altrender.strategy.ProgramStrategy;
import com.coredex.altrender.strategy.RasterState;
import com.coredex.altrender.strategy.ShaderStage;
import com.coredex.altrender.strategy.Strategies;
import com.coredex.altrender.strategy.StreamBuffer;
import com.coredex.altrender.strategy.VertexLayout;
import com.coredex.altrender.ui.framework.DrawContext;
import com.coredex.altrender.ui.framework.TextComponent;
import com.coredex.altrender.ui.framework.TextFormattingStyle;
import org.lwjgl.system.MemoryUtil;

/**
 * The UI's 2D renderer (development.md 5.1): everything a screen draws (rectangles, rounded rectangles, gradients,
 * textured quads and the game's font) goes into one vertex batch, drawn with our shader through the tier's
 * strategies; a batch is cut only by a texture change or a new clip. Coordinates are GUI units; transforms are kept
 * on the CPU; clips are scissor rectangles. One {@link #begin}/{@link #end} per screen frame, an Altrender pass
 * (the game's state is put back after). Render thread only.
 */
public final class UiRenderer implements DrawContext {

	/** Bytes per vertex: position, uv, local position and half size (rounded shapes), colour, radius, mode. */
	private static final int STRIDE = 44;
	private static final int MODE_SOLID = 0;
	private static final int MODE_TEXTURED = 1;
	private static final int MODE_ROUNDED = 2;
	private static final int GL_ALPHA_TEST = 0x0BC0;
	private static final int UNIFORM_ALIGN = 256;

	private static final VertexLayout LAYOUT = new VertexLayout(STRIDE, List.of(
			new VertexLayout.Attribute(0, "a_Pos", 2, VertexLayout.AttribType.FLOAT, false, 0),
			new VertexLayout.Attribute(1, "a_UV", 2, VertexLayout.AttribType.FLOAT, false, 8),
			new VertexLayout.Attribute(2, "a_Local", 2, VertexLayout.AttribType.FLOAT, false, 16),
			new VertexLayout.Attribute(3, "a_Half", 2, VertexLayout.AttribType.FLOAT, false, 24),
			new VertexLayout.Attribute(4, "a_Color", 4, VertexLayout.AttribType.UBYTE, true, 32),
			new VertexLayout.Attribute(5, "a_Shape", 2, VertexLayout.AttribType.FLOAT, false, 36)));

	private static final RasterState RASTER = new RasterState(true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
			BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, 0xF, false, Compare.ALWAYS, false, 0f, 0f, false, false, null);

	static final String VERTEX = """
			#version 330 core
			in vec2 a_Pos;
			in vec2 a_UV;
			in vec2 a_Local;
			in vec2 a_Half;
			in vec4 a_Color;
			// corner radius, mode (0 solid, 1 textured, 2 rounded)
			in vec2 a_Shape;
			layout(std140) uniform UiFrame {
				// 2 / width, -2 / height in GUI units
				vec4 u_Screen;
			};
			out vec2 v_UV;
			out vec2 v_Local;
			flat out vec2 v_Half;
			out vec4 v_Color;
			flat out vec2 v_Shape;

			void main() {
				gl_Position = vec4(a_Pos.x * u_Screen.x - 1.0, a_Pos.y * u_Screen.y + 1.0, 0.0, 1.0);
				v_UV = a_UV;
				v_Local = a_Local;
				v_Half = a_Half;
				v_Color = a_Color;
				v_Shape = a_Shape;
			}
			""";

	static final String FRAGMENT = """
			#version 330 core
			in vec2 v_UV;
			in vec2 v_Local;
			flat in vec2 v_Half;
			in vec4 v_Color;
			flat in vec2 v_Shape;
			uniform sampler2D u_Texture;
			out vec4 fragColor;

			void main() {
				vec4 color = v_Color;
				int mode = int(v_Shape.y + 0.5);
				if (mode == 1) {
					color *= texture(u_Texture, v_UV);
				} else if (mode == 2) {
					// signed distance to the rounded box; one pixel of anti-aliasing at its edge
					float r = v_Shape.x;
					vec2 q = abs(v_Local) - v_Half + r;
					float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
					color.a *= clamp(0.5 - d / max(fwidth(d), 1e-4), 0.0, 1.0);
				}
				if (color.a <= 0.002) {
					discard;
				}
				fragColor = color;
			}
			""";

	/** The 16 chat colours, by code. */
	private static final int[] CODE_COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
			0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private final Strategies gl;
	private final int program;
	private final UiFont.Glyph glyph = new UiFont.Glyph();
	private ByteBuffer vertices = MemoryUtil.memAlloc(STRIDE * 6 * 1024).order(ByteOrder.LITTLE_ENDIAN);
	private int count;
	private int batchTexture;
	private UiHost host;
	private UiFont font;
	private float invWidth;
	private float invHeight;
	private int scale;
	private boolean alphaTest;
	private boolean active;
	private double tx;
	private double ty;
	private final double[] transforms = new double[64];
	private int transformDepth;
	/** Clip rectangles in framebuffer pixels (x, y, w, h), innermost last. */
	private final int[] clips = new int[64];
	private int clipDepth;
	private long drawCalls;

	public UiRenderer(Strategies gl) {
		this.gl = gl;
		ProgramStrategy p = gl.program();
		ProgramStrategy.Result vs = p.compile(ShaderStage.VERTEX, VERTEX);
		ProgramStrategy.Result fs = p.compile(ShaderStage.FRAGMENT, FRAGMENT);
		if (!vs.ok() || !fs.ok()) {
			throw new IllegalStateException("ui shaders: " + vs.log() + " " + fs.log());
		}
		ProgramStrategy.Result linked = p.link(vs.name(), fs.name(), LAYOUT.names(), List.of("fragColor"));
		p.deleteShader(vs.name());
		p.deleteShader(fs.name());
		if (!linked.ok()) {
			throw new IllegalStateException("ui program: " + linked.log());
		}
		program = linked.name();
		gl.uniform().configureProgram(program); // UiFrame → binding 0
		gl.beginPass("ui-setup");
		p.setSamplerUnit(program, "u_Texture", 0);
		gl.endPass();
	}

	/** Starts a frame of UI drawing: an Altrender pass until {@link #end}. */
	public void begin(UiHost host) {
		if (active) {
			throw new IllegalStateException("UiRenderer.begin twice");
		}
		active = true;
		this.host = host;
		this.font = host.font();
		scale = Math.max(1, host.guiScale());
		float width = host.framebufferWidth() / (float) scale;
		float height = host.framebufferHeight() / (float) scale;
		invWidth = 2f / width;
		invHeight = -2f / height;
		tx = 0;
		ty = 0;
		transformDepth = 0;
		clipDepth = 0;
		count = 0;
		batchTexture = 0;
		gl.beginPass("ui");
		GlState state = gl.state();
		state.apply(RASTER);
		state.scissorTest(false);
		// The game's alpha test also applies to shader output in a compatibility context: off while we draw.
		alphaTest = com.coredex.altrender.gl.GlStateTracker.isEnabled(GL_ALPHA_TEST);
		if (alphaTest) {
			org.lwjgl.opengl.GL11C.glDisable(GL_ALPHA_TEST);
		}
		state.useProgram(program);
		StreamBuffer stream = gl.stream();
		long at = stream.alloc(16, UNIFORM_ALIGN);
		stream.memory().putFloat((int) at, invWidth).putFloat((int) at + 4, invHeight).putFloat((int) at + 8, 0f)
				.putFloat((int) at + 12, 0f);
		stream.commit(at, 16);
		gl.uniform().bindBlock(0, stream.buffer(), at, 16);
	}

	/** Draws what's left and gives the game its state back. */
	public void end() {
		if (!active) {
			return;
		}
		flush();
		if (alphaTest) {
			org.lwjgl.opengl.GL11C.glEnable(GL_ALPHA_TEST);
		}
		gl.endPass();
		active = false;
		host = null;
	}

	/** {@code d} under the current transform, in screen GUI units (anchors for tooltips drawn later). */
	public com.coredex.altrender.ui.framework.Dim2i toScreen(com.coredex.altrender.ui.framework.Dim2i d) {
		return d.offset((int) Math.round(tx), (int) Math.round(ty));
	}

	/** GL draw calls since the renderer was made (stats). */
	public long drawCalls() {
		return drawCalls;
	}

	public void delete() {
		gl.program().deleteProgram(program);
		MemoryUtil.memFree(vertices);
		vertices = null;
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int color) {
		quad(x1, y1, x2, y2, color, color, 0, 0, 0, 0, MODE_SOLID, 0f);
	}

	@Override
	public void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom) {
		quad(x1, y1, x2, y2, colorTop, colorBottom, 0, 0, 0, 0, MODE_SOLID, 0f);
	}

	@Override
	public void fillRounded(int x1, int y1, int x2, int y2, float radius, int color) {
		float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
		if (r <= 0f) {
			fill(x1, y1, x2, y2, color);
			return;
		}
		quad(x1, y1, x2, y2, color, color, 0, 0, 0, 0, MODE_ROUNDED, r);
	}

	@Override
	public void blit(int texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int color) {
		if (texture == 0) {
			return;
		}
		useTexture(texture);
		quad(x, y, x + width, y + height, color, color, u0, v0, u1, v1, MODE_TEXTURED, 0f);
	}

	@Override
	public void blitWholeImage(String icon, int x, int y, int width, int height) {
		blit(host.texture(icon), x, y, width, height, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
	}

	@Override
	public int drawString(TextComponent component, int x, int y, int color, boolean shadow) {
		// Styles of the outermost styled component: its colour wins over {@code color}; italic, struck, underlined.
		boolean italic = false;
		boolean strike = false;
		boolean underline = false;
		TextComponent c = component;
		while (c instanceof TextComponent.Styled s) {
			for (TextFormattingStyle style : s.styles()) {
				if (style.isColor()) {
					color = (color & 0xFF000000) | CODE_COLORS[style.ordinal()];
				} else if (style == TextFormattingStyle.ITALIC) {
					italic = true;
				} else if (style == TextFormattingStyle.STRIKETHROUGH) {
					strike = true;
				} else if (style == TextFormattingStyle.UNDERLINE) {
					underline = true;
				}
			}
			c = s.inner();
		}
		String text = extractString(c);
		if (shadow) {
			text(text, x + 1, y + 1, color, true, italic, strike, underline);
		}
		return text(text, x, y, color, false, italic, strike, underline);
	}

	/** Draws {@code text} with colour codes (and styles: italic leans the glyph tops a unit right); returns the x after it. */
	private int text(String text, float x, float y, int color, boolean shadow, boolean italic, boolean strike,
			boolean underline) {
		int end = text(text, x, y, color, shadow, italic);
		if (strike || underline) {
			if ((color & 0xFC000000) == 0) {
				color |= 0xFF000000;
			}
			int c = shadow ? (color & 0xFCFCFC) >> 2 | color & 0xFF000000 : color;
			if (strike) {
				quadF(x, y + 3.5f, end, y + 4.5f, c, c, 0, 0, 0, 0, MODE_SOLID, 0f, 0f);
			}
			if (underline) {
				quadF(x, y + 8f, end, y + 9f, c, c, 0, 0, 0, 0, MODE_SOLID, 0f, 0f);
			}
		}
		return end;
	}

	private int text(String text, float x, float y, int color, boolean shadow, boolean italic) {
		if ((color & 0xFC000000) == 0) {
			color |= 0xFF000000; // as the game: no alpha given means opaque
		}
		int alpha = color & 0xFF000000;
		int base = shadow ? (color & 0xFCFCFC) >> 2 | alpha : color;
		int current = base;
		int texture = font.texture();
		float pen = x;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				int code = "0123456789abcdef".indexOf(Character.toLowerCase(text.charAt(i + 1)));
				int rgb = code >= 0 ? CODE_COLORS[code] : base & 0xFFFFFF;
				if (code >= 0 && shadow) {
					rgb = (rgb & 0xFCFCFC) >> 2;
				}
				current = (text.charAt(i + 1) == 'r' ? base & 0xFFFFFF : rgb) | alpha;
				i++;
				continue;
			}
			if (!font.glyph(c, glyph)) {
				continue;
			}
			useTexture(texture);
			quadF(pen, y, pen + glyph.width, y + glyph.height, current, current, glyph.u0, glyph.v0, glyph.u1, glyph.v1,
					MODE_TEXTURED, 0f, italic ? 1f : 0f);
			pen += glyph.advance;
		}
		return (int) pen;
	}

	@Override
	public String extractString(TextComponent component) {
		if (component instanceof TextComponent.Literal l) {
			return l.text();
		}
		if (component instanceof TextComponent.Translatable t) {
			Object[] args = new Object[t.args().size()];
			for (int i = 0; i < args.length; i++) {
				Object a = t.args().get(i);
				args[i] = a instanceof TextComponent c ? extractString(c) : a;
			}
			return host.translate(t.keys().get(0), args);
		}
		if (component instanceof TextComponent.Styled s) {
			String inner = extractString(s.inner());
			for (TextFormattingStyle style : s.styles()) {
				if (style.isColor()) {
					return "§" + "0123456789abcdef".charAt(style.ordinal()) + inner + "§r";
				}
			}
			return inner;
		}
		return component.toString();
	}

	@Override
	public int getStringWidth(String str) {
		return width(font, str);
	}

	/** {@code str}'s width in {@code font} (colour codes skipped); usable outside a frame. */
	public static int width(UiFont font, String str) {
		UiFont.Glyph g = new UiFont.Glyph();
		int width = 0;
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '§' && i + 1 < str.length()) {
				i++;
			} else if (font.glyph(c, g)) {
				width += g.advance;
			}
		}
		return width;
	}

	@Override
	public int lineHeight() {
		return font.lineHeight();
	}

	@Override
	public void pushMatrix() {
		transforms[transformDepth * 2] = tx;
		transforms[transformDepth * 2 + 1] = ty;
		transformDepth++;
	}

	@Override
	public void translate(double x, double y, double z) {
		tx += x;
		ty += y;
	}

	@Override
	public void popMatrix() {
		transformDepth--;
		tx = transforms[transformDepth * 2];
		ty = transforms[transformDepth * 2 + 1];
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		// To framebuffer pixels (y up), rounding outward, intersected with the enclosing clip.
		int fx0 = (int) Math.floor((x1 + tx) * scale);
		int fx1 = (int) Math.ceil((x2 + tx) * scale);
		int fy0 = host.framebufferHeight() - (int) Math.ceil((y2 + ty) * scale);
		int fy1 = host.framebufferHeight() - (int) Math.floor((y1 + ty) * scale);
		if (clipDepth > 0) {
			int o = (clipDepth - 1) * 4;
			fx0 = Math.max(fx0, clips[o]);
			fy0 = Math.max(fy0, clips[o + 1]);
			fx1 = Math.min(fx1, clips[o] + clips[o + 2]);
			fy1 = Math.min(fy1, clips[o + 1] + clips[o + 3]);
		}
		flush();
		int o = clipDepth * 4;
		clips[o] = fx0;
		clips[o + 1] = fy0;
		clips[o + 2] = Math.max(0, fx1 - fx0);
		clips[o + 3] = Math.max(0, fy1 - fy0);
		clipDepth++;
		applyClip();
	}

	@Override
	public void disableScissor() {
		flush();
		clipDepth = Math.max(0, clipDepth - 1);
		applyClip();
	}

	private void applyClip() {
		if (clipDepth == 0) {
			gl.state().scissorTest(false);
		} else {
			int o = (clipDepth - 1) * 4;
			gl.state().scissor(clips[o], clips[o + 1], clips[o + 2], clips[o + 3]);
		}
	}

	private void useTexture(int texture) {
		if (batchTexture != 0 && batchTexture != texture) {
			flush();
		}
		batchTexture = texture;
	}

	private void quad(int x1, int y1, int x2, int y2, int colorTop, int colorBottom, float u0, float v0, float u1,
			float v1, int mode, float radius) {
		quadF(x1, y1, x2, y2, colorTop, colorBottom, u0, v0, u1, v1, mode, radius, 0f);
	}

	/** @param shear how far right the top edge is moved (italic glyphs) */
	private void quadF(float x1, float y1, float x2, float y2, int colorTop, int colorBottom, float u0, float v0,
			float u1, float v1, int mode, float radius, float shear) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		if ((count + 6) * STRIDE > vertices.capacity()) {
			ByteBuffer bigger = MemoryUtil.memAlloc(vertices.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
			MemoryUtil.memCopy(vertices, bigger);
			MemoryUtil.memFree(vertices);
			vertices = bigger;
		}
		float ax = (float) (x1 + tx);
		float ay = (float) (y1 + ty);
		float bx = (float) (x2 + tx);
		float by = (float) (y2 + ty);
		float hx = (x2 - x1) / 2f;
		float hy = (y2 - y1) / 2f;
		// two triangles: (a, b, c), (a, c, d) with a top-left, b bottom-left, c bottom-right, d top-right
		vertex(ax + shear, ay, u0, v0, -hx, -hy, hx, hy, colorTop, radius, mode);
		vertex(ax, by, u0, v1, -hx, hy, hx, hy, colorBottom, radius, mode);
		vertex(bx, by, u1, v1, hx, hy, hx, hy, colorBottom, radius, mode);
		vertex(ax + shear, ay, u0, v0, -hx, -hy, hx, hy, colorTop, radius, mode);
		vertex(bx, by, u1, v1, hx, hy, hx, hy, colorBottom, radius, mode);
		vertex(bx + shear, ay, u1, v0, hx, -hy, hx, hy, colorTop, radius, mode);
	}

	private void vertex(float x, float y, float u, float v, float lx, float ly, float hx, float hy, int argb,
			float radius, int mode) {
		// Straight to memory (capacity checked per quad): the debug screen writes thousands a frame.
		long a = MemoryUtil.memAddress(vertices) + (long) count * STRIDE;
		MemoryUtil.memPutFloat(a, x);
		MemoryUtil.memPutFloat(a + 4, y);
		MemoryUtil.memPutFloat(a + 8, u);
		MemoryUtil.memPutFloat(a + 12, v);
		MemoryUtil.memPutFloat(a + 16, lx);
		MemoryUtil.memPutFloat(a + 20, ly);
		MemoryUtil.memPutFloat(a + 24, hx);
		MemoryUtil.memPutFloat(a + 28, hy);
		// RGBA bytes = ABGR as a little-endian int
		MemoryUtil.memPutInt(a + 32, (argb >> 16 & 0xFF) | (argb & 0xFF00) | (argb & 0xFF) << 16 | (argb & 0xFF000000));
		MemoryUtil.memPutFloat(a + 36, radius);
		MemoryUtil.memPutFloat(a + 40, mode);
		count++;
	}

	private void flush() {
		if (count == 0) {
			return;
		}
		StreamBuffer stream = gl.stream();
		long bytes = (long) count * STRIDE;
		long at = stream.alloc(bytes, STRIDE);
		if (at < 0) {
			throw new IllegalStateException("ui batch of " + bytes + " bytes doesn't fit the stream buffer");
		}
		MemoryUtil.memCopy(MemoryUtil.memAddress(vertices), MemoryUtil.memAddress(stream.memory()) + at, bytes);
		stream.commit(at, bytes);
		gl.vertexInput().bind(LAYOUT, stream.buffer(), 0);
		if (batchTexture != 0) {
			gl.state().bindTexture(0, GlState.TARGET_2D, batchTexture);
		}
		gl.vertexInput().drawArrays(Primitive.TRIANGLES, (int) (at / STRIDE), count);
		drawCalls++;
		count = 0;
		batchTexture = 0;
	}
}
