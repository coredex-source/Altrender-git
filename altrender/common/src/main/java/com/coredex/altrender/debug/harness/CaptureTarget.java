/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug.harness;

import static org.lwjgl.opengl.GL30C.*;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

/**
 * The harness's own framebuffer at the capture size, so window managers that resize the window (tiling ones do) can't
 * change what is captured. The game draws into it; it is then copied to the window. Uses GL 3.0 framebuffers, which
 * every context Altrender runs on has.
 */
public final class CaptureTarget {

	private final int width;
	private final int height;
	private int fbo;
	private int color;
	private int depth;

	public CaptureTarget(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void bind() {
		if (fbo == 0) {
			color = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, color);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			// Altrender's own GL calls bypass the tracker, so they put back what the game had bound (1.4).
			glBindTexture(GL_TEXTURE_2D, com.coredex.altrender.gl.GlStateTracker.boundTexture2D());
			depth = glGenRenderbuffers();
			glBindRenderbuffer(GL_RENDERBUFFER, depth);
			glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
			glBindRenderbuffer(GL_RENDERBUFFER, 0);
			fbo = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, fbo);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
			glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
			int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
			if (status != GL_FRAMEBUFFER_COMPLETE) {
				throw new IllegalStateException("capture framebuffer incomplete: 0x" + Integer.toHexString(status));
			}
		}
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
	}

	/** RGB, bottom row first (GL order). */
	public ByteBuffer read() {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 3);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
		glPixelStorei(GL_PACK_ALIGNMENT, 1);
		glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
		glPixelStorei(GL_PACK_ALIGNMENT, com.coredex.altrender.gl.GlStateTracker.pixelStoreValue(GL_PACK_ALIGNMENT));
		return pixels;
	}

	/** Copies the capture to the window (scaled to fit) and rebinds the window's framebuffer. */
	public void present(int windowWidth, int windowHeight) {
		glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		glBlitFramebuffer(0, 0, width, height, 0, 0, windowWidth, windowHeight, GL_COLOR_BUFFER_BIT, GL_LINEAR);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}
}
