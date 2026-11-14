/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2.merge.org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.coredex.altrender.gl.GlStateTracker;
import com.coredex.altrender.lwjgl2.merge.Replace;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.Checks;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

/**
 * Donor: LWJGL2-only {@code GL11} signatures, merged into LWJGL 3's {@code org.lwjgl.opengl.GL11} at build time
 * (build-logic LwjglNestTask; docs/lwjgl2-surface/). Only public static methods are copied; they must not refer to
 * this class.
 * <p>
 * {@link Replace} methods (development.md 1.4) replace LWJGL 3's own: they update {@link GlStateTracker}, then do what
 * LWJGL's method did. Where that was a JNI native (compatibility-only functions are bound by name, so they can't be
 * wrapped), they call the driver function through LWJGL's {@code JNI} trampolines with its address from
 * {@code GL.getCapabilities()}; where it was a wrapper around an {@code ngl*} native, they call that native. Plain
 * pass-throughs (glColor, glNormal, ...) exist so that FrameStats counts every GL11 function the game uses.
 */
public final class GL11 {

	private GL11() {
	}

	public static void glFog(int pname, FloatBuffer params) {
		org.lwjgl.opengl.GL11.glFogfv(pname, params);
	}

	public static void glLight(int light, int pname, FloatBuffer params) {
		org.lwjgl.opengl.GL11.glLightfv(light, pname, params);
	}

	public static void glLightModel(int pname, FloatBuffer params) {
		org.lwjgl.opengl.GL11.glLightModelfv(pname, params);
	}

	public static void glGetFloat(int pname, FloatBuffer params) {
		org.lwjgl.opengl.GL11.glGetFloatv(pname, params);
	}

	/** LWJGL2 picked the type from the flag and the buffer: unsigned → GL_UNSIGNED_BYTE, else GL_BYTE. */
	public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer pointer) {
		org.lwjgl.opengl.GL11.glColorPointer(size,
				unsigned ? org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE : org.lwjgl.opengl.GL11.GL_BYTE, stride, pointer);
	}

	public static void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
		org.lwjgl.opengl.GL11.glTexCoordPointer(size, org.lwjgl.opengl.GL11.GL_FLOAT, stride, pointer);
	}

	public static void glVertexPointer(int size, int stride, FloatBuffer pointer) {
		org.lwjgl.opengl.GL11.glVertexPointer(size, org.lwjgl.opengl.GL11.GL_FLOAT, stride, pointer);
	}

	public static void glNormalPointer(int stride, ByteBuffer pointer) {
		org.lwjgl.opengl.GL11.glNormalPointer(org.lwjgl.opengl.GL11.GL_BYTE, stride, pointer);
	}

	// ---- matrices ----

	@Replace
	public static void glMatrixMode(int mode) {
		GlStateTracker.matrixMode(mode);
		JNI.callV(mode, GL.getCapabilities().glMatrixMode);
	}

	@Replace
	public static void glLoadIdentity() {
		GlStateTracker.loadIdentity();
		JNI.callV(GL.getCapabilities().glLoadIdentity);
	}

	@Replace
	public static void glPushMatrix() {
		GlStateTracker.pushMatrix();
		JNI.callV(GL.getCapabilities().glPushMatrix);
	}

	@Replace
	public static void glPopMatrix() {
		GlStateTracker.popMatrix();
		JNI.callV(GL.getCapabilities().glPopMatrix);
	}

	@Replace
	public static void glTranslatef(float x, float y, float z) {
		GlStateTracker.translate(x, y, z);
		JNI.callV(x, y, z, GL.getCapabilities().glTranslatef);
	}

	@Replace
	public static void glTranslated(double x, double y, double z) {
		GlStateTracker.translate((float) x, (float) y, (float) z);
		JNI.callV(x, y, z, GL.getCapabilities().glTranslated);
	}

	@Replace
	public static void glRotatef(float angle, float x, float y, float z) {
		GlStateTracker.rotate(angle, x, y, z);
		JNI.callV(angle, x, y, z, GL.getCapabilities().glRotatef);
	}

	@Replace
	public static void glRotated(double angle, double x, double y, double z) {
		GlStateTracker.rotate((float) angle, (float) x, (float) y, (float) z);
		JNI.callV(angle, x, y, z, GL.getCapabilities().glRotated);
	}

	@Replace
	public static void glScalef(float x, float y, float z) {
		GlStateTracker.scale(x, y, z);
		JNI.callV(x, y, z, GL.getCapabilities().glScalef);
	}

	@Replace
	public static void glScaled(double x, double y, double z) {
		GlStateTracker.scale((float) x, (float) y, (float) z);
		JNI.callV(x, y, z, GL.getCapabilities().glScaled);
	}

	@Replace
	public static void glOrtho(double l, double r, double b, double t, double n, double f) {
		GlStateTracker.ortho(l, r, b, t, n, f);
		JNI.callV(l, r, b, t, n, f, GL.getCapabilities().glOrtho);
	}

	@Replace
	public static void glFrustum(double l, double r, double b, double t, double n, double f) {
		GlStateTracker.frustum(l, r, b, t, n, f);
		JNI.callV(l, r, b, t, n, f, GL.getCapabilities().glFrustum);
	}

	@Replace
	public static void glMultMatrixf(FloatBuffer m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.multMatrix(m);
		org.lwjgl.opengl.GL11.nglMultMatrixf(MemoryUtil.memAddress(m));
	}

	@Replace
	public static void glMultMatrixf(float[] m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.multMatrix(m);
		JNI.callPV(m, GL.getCapabilities().glMultMatrixf);
	}

	@Replace
	public static void glMultMatrixd(DoubleBuffer m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.multMatrix(m);
		org.lwjgl.opengl.GL11.nglMultMatrixd(MemoryUtil.memAddress(m));
	}

	@Replace
	public static void glMultMatrixd(double[] m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.multMatrix(m);
		JNI.callPV(m, GL.getCapabilities().glMultMatrixd);
	}

	@Replace
	public static void glLoadMatrixf(FloatBuffer m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.loadMatrix(m);
		org.lwjgl.opengl.GL11.nglLoadMatrixf(MemoryUtil.memAddress(m));
	}

	@Replace
	public static void glLoadMatrixf(float[] m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.loadMatrix(m);
		JNI.callPV(m, GL.getCapabilities().glLoadMatrixf);
	}

	@Replace
	public static void glLoadMatrixd(DoubleBuffer m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.loadMatrix(m);
		org.lwjgl.opengl.GL11.nglLoadMatrixd(MemoryUtil.memAddress(m));
	}

	@Replace
	public static void glLoadMatrixd(double[] m) {
		if (Checks.CHECKS) {
			Checks.check(m, 16);
		}
		GlStateTracker.loadMatrix(m);
		JNI.callPV(m, GL.getCapabilities().glLoadMatrixd);
	}

	// ---- enables, client arrays ----

	@Replace
	public static void glEnable(int cap) {
		if (GlStateTracker.enable(cap) && !GlStateTracker.suppressed(cap)) {
			GL11C.glEnable(cap);
			if (cap == GL11C.GL_BLEND) {
				GlStateTracker.applyBufferBlends(); // every buffer was set: a pipeline's per-buffer blends again
			}
		}
	}

	@Replace
	public static void glDisable(int cap) {
		if (GlStateTracker.disable(cap) && !GlStateTracker.suppressed(cap)) {
			GL11C.glDisable(cap);
			if (cap == GL11C.GL_BLEND) {
				GlStateTracker.applyBufferBlends();
			}
		}
	}

	@Replace
	public static void glEnableClientState(int array) {
		GlStateTracker.enableClient(array);
		JNI.callV(array, GL.getCapabilities().glEnableClientState);
	}

	@Replace
	public static void glDisableClientState(int array) {
		GlStateTracker.disableClient(array);
		JNI.callV(array, GL.getCapabilities().glDisableClientState);
	}

	// ---- fog, alpha, blend, depth, cull, colour mask ----

	@Replace
	public static void glFogf(int pname, float param) {
		GlStateTracker.fog(pname, param);
		JNI.callV(pname, param, GL.getCapabilities().glFogf);
	}

	@Replace
	public static void glFogi(int pname, int param) {
		GlStateTracker.fog(pname, (float) param);
		JNI.callV(pname, param, GL.getCapabilities().glFogi);
	}

	@Replace
	public static void glFogfv(int pname, FloatBuffer params) {
		if (Checks.CHECKS) {
			Checks.check(params, 1);
		}
		GlStateTracker.fog(pname, params);
		org.lwjgl.opengl.GL11.nglFogfv(pname, MemoryUtil.memAddress(params));
	}

	@Replace
	public static void glFogfv(int pname, float[] params) {
		if (Checks.CHECKS) {
			Checks.check(params, 1);
		}
		GlStateTracker.fog(pname, params);
		JNI.callPV(pname, params, GL.getCapabilities().glFogfv);
	}

	@Replace
	public static void glFogiv(int pname, IntBuffer params) {
		if (Checks.CHECKS) {
			Checks.check(params, 1);
		}
		GlStateTracker.fog(pname, params);
		org.lwjgl.opengl.GL11.nglFogiv(pname, MemoryUtil.memAddress(params));
	}

	@Replace
	public static void glFogiv(int pname, int[] params) {
		if (Checks.CHECKS) {
			Checks.check(params, 1);
		}
		GlStateTracker.fog(pname, params);
		JNI.callPV(pname, params, GL.getCapabilities().glFogiv);
	}

	@Replace
	public static void glAlphaFunc(int func, float ref) {
		if (GlStateTracker.alphaFunc(func, ref)) {
			JNI.callV(func, ref, GL.getCapabilities().glAlphaFunc);
		}
	}

	@Replace
	public static void glBlendFunc(int sfactor, int dfactor) {
		if (GlStateTracker.blendFunc(sfactor, dfactor) && !GlStateTracker.blendOverridden()) {
			GL11C.glBlendFunc(sfactor, dfactor);
			GlStateTracker.applyBufferBlends();
		}
	}

	@Replace
	public static void glDepthFunc(int func) {
		if (GlStateTracker.depthFunc(func)) {
			GL11C.glDepthFunc(func);
		}
	}

	@Replace
	public static void glDepthMask(boolean flag) {
		if (GlStateTracker.depthMask(flag)) {
			GL11C.glDepthMask(flag);
		}
	}

	@Replace
	public static void glPixelStorei(int pname, int param) {
		GlStateTracker.pixelStore(pname, param);
		GL11C.glPixelStorei(pname, param);
	}

	@Replace
	public static void glPolygonOffset(float factor, float units) {
		GlStateTracker.polygonOffset(factor, units);
		GL11C.glPolygonOffset(factor, units);
	}

	@Replace
	public static void glCullFace(int mode) {
		if (GlStateTracker.cullFace(mode)) {
			GL11C.glCullFace(mode);
		}
	}

	@Replace
	public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		if (GlStateTracker.colorMask(red, green, blue, alpha)) {
			GL11C.glColorMask(red, green, blue, alpha);
		}
	}

	// ---- textures ----

	@Replace
	public static void glBindTexture(int target, int texture) {
		if (GlStateTracker.bindTexture(target, texture)) {
			GL11C.glBindTexture(target, texture);
		}
	}

	@Replace
	public static void glDeleteTextures(int texture) {
		GlStateTracker.deleteTexture(texture);
		GL11C.glDeleteTextures(texture);
	}

	@Replace
	public static void glDeleteTextures(IntBuffer textures) {
		GlStateTracker.deleteTextures(textures);
		GL11C.glDeleteTextures(textures);
	}

	@Replace
	public static void glDeleteTextures(int[] textures) {
		GlStateTracker.deleteTextures(textures);
		GL11C.glDeleteTextures(textures);
	}

	@Replace
	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border,
			int format, int type, ByteBuffer pixels) {
		GlStateTracker.upload();
		GL11C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	@Replace
	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
			int format, int type, ByteBuffer pixels) {
		GlStateTracker.upload();
		GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	// ---- display lists ----

	@Replace
	public static void glNewList(int list, int mode) {
		GlStateTracker.newList(list, mode);
		JNI.callV(list, mode, GL.getCapabilities().glNewList);
	}

	@Replace
	public static void glEndList() {
		GlStateTracker.endList();
		JNI.callV(GL.getCapabilities().glEndList);
	}

	@Replace
	public static void glCallList(int list) {
		if (!GlStateTracker.compiling() && com.coredex.altrender.immediate.ListCapture.draw(list)) {
			GlStateTracker.callList(list);
			return;
		}
		JNI.callV(list, GL.getCapabilities().glCallList);
		GlStateTracker.callList(list);
	}

	@Replace
	public static void glCallLists(int type, ByteBuffer lists) {
		int size = type == GL11C.GL_BYTE || type == GL11C.GL_UNSIGNED_BYTE ? 1
				: type == GL11C.GL_SHORT || type == GL11C.GL_UNSIGNED_SHORT || type == 0x1407 ? 2 // GL_2_BYTES
				: type == 0x1408 ? 3 // GL_3_BYTES
				: 4;
		org.lwjgl.opengl.GL11.nglCallLists(lists.remaining() / size, type, MemoryUtil.memAddress(lists));
		GlStateTracker.callLists(type, lists);
	}

	@Replace
	public static void glCallLists(ByteBuffer lists) {
		org.lwjgl.opengl.GL11.nglCallLists(lists.remaining(), GL11C.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(lists));
		GlStateTracker.callLists(GL11C.GL_UNSIGNED_BYTE, lists);
	}

	@Replace
	public static void glCallLists(ShortBuffer lists) {
		org.lwjgl.opengl.GL11.nglCallLists(lists.remaining(), GL11C.GL_UNSIGNED_SHORT, MemoryUtil.memAddress(lists));
		GlStateTracker.callLists(lists);
	}

	@Replace
	public static void glCallLists(IntBuffer lists) {
		org.lwjgl.opengl.GL11.nglCallLists(lists.remaining(), GL11C.GL_UNSIGNED_INT, MemoryUtil.memAddress(lists));
		GlStateTracker.callLists(lists);
	}

	@Replace
	public static void glListBase(int base) {
		GlStateTracker.listBase(base);
		JNI.callV(base, GL.getCapabilities().glListBase);
	}

	@Replace
	public static void glDeleteLists(int list, int range) {
		GlStateTracker.deleteLists(list, range);
		JNI.callV(list, range, GL.getCapabilities().glDeleteLists);
	}

	@Replace
	public static void glPushAttrib(int mask) {
		GlStateTracker.pushAttrib(mask);
		JNI.callV(mask, GL.getCapabilities().glPushAttrib);
	}

	@Replace
	public static void glPopAttrib() {
		JNI.callV(GL.getCapabilities().glPopAttrib);
		GlStateTracker.popAttrib();
	}

	@Replace
	public static void glPushClientAttrib(int mask) {
		GlStateTracker.pushClientAttrib(mask);
		JNI.callV(mask, GL.getCapabilities().glPushClientAttrib);
	}

	@Replace
	public static void glPopClientAttrib() {
		JNI.callV(GL.getCapabilities().glPopClientAttrib);
		GlStateTracker.popClientAttrib();
	}

	// ---- draws ----

	@Replace
	public static void glDrawArrays(int mode, int first, int count) {
		GlStateTracker.draw();
		GL11C.glDrawArrays(mode, first, count);
	}

	@Replace
	public static void glDrawElements(int mode, int count, int type, long indices) {
		GlStateTracker.draw();
		GL11C.glDrawElements(mode, count, type, indices);
	}

	@Replace
	public static void glDrawElements(int mode, int type, ByteBuffer indices) {
		GlStateTracker.draw();
		GL11C.glDrawElements(mode, type, indices);
	}

	@Replace
	public static void glDrawElements(int mode, ByteBuffer indices) {
		GlStateTracker.draw();
		GL11C.glDrawElements(mode, indices);
	}

	@Replace
	public static void glDrawElements(int mode, ShortBuffer indices) {
		GlStateTracker.draw();
		GL11C.glDrawElements(mode, indices);
	}

	@Replace
	public static void glDrawElements(int mode, IntBuffer indices) {
		GlStateTracker.draw();
		GL11C.glDrawElements(mode, indices);
	}

	// ---- pass-throughs (counted, not tracked) ----

	@Replace
	public static void glColor4f(float r, float g, float b, float a) {
		JNI.callV(r, g, b, a, GL.getCapabilities().glColor4f);
	}

	@Replace
	public static void glColor3f(float r, float g, float b) {
		JNI.callV(r, g, b, GL.getCapabilities().glColor3f);
	}

	@Replace
	public static void glNormal3f(float x, float y, float z) {
		JNI.callV(x, y, z, GL.getCapabilities().glNormal3f);
	}

	@Replace
	public static void glShadeModel(int mode) {
		JNI.callV(mode, GL.getCapabilities().glShadeModel);
	}

	@Replace
	public static void glColorMaterial(int face, int mode) {
		JNI.callV(face, mode, GL.getCapabilities().glColorMaterial);
	}

	@Replace
	public static int glGenLists(int range) {
		return JNI.callI(range, GL.getCapabilities().glGenLists);
	}
}
