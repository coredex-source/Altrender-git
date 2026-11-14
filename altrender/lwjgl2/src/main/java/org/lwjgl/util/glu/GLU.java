/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.lwjgl.util.glu;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The parts of LWJGL2's GLU old versions use (Altrender's LWJGL2 layer), implemented from the GLU 1.3 specification's
 * matrix definitions.
 */
public class GLU {

	private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

	public GLU() {
	}

	/** Multiplies the current matrix by a perspective projection (fovy in degrees). */
	public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
		float f = (float) (1.0 / Math.tan(Math.toRadians(fovy) / 2.0));
		float depth = zNear - zFar;
		MATRIX.clear();
		// column-major
		MATRIX.put(f / aspect).put(0).put(0).put(0);
		MATRIX.put(0).put(f).put(0).put(0);
		MATRIX.put(0).put(0).put((zFar + zNear) / depth).put(-1);
		MATRIX.put(0).put(0).put(2 * zFar * zNear / depth).put(0);
		MATRIX.flip();
		GL11.glMultMatrixf(MATRIX);
	}

	public static void gluOrtho2D(float left, float right, float bottom, float top) {
		GL11.glOrtho(left, right, bottom, top, -1, 1);
	}

	public static void gluLookAt(float eyeX, float eyeY, float eyeZ, float centerX, float centerY, float centerZ,
			float upX, float upY, float upZ) {
		float fx = centerX - eyeX;
		float fy = centerY - eyeY;
		float fz = centerZ - eyeZ;
		float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= fl;
		fy /= fl;
		fz /= fl;
		// s = f x up, normalised; u = s x f
		float sx = fy * upZ - fz * upY;
		float sy = fz * upX - fx * upZ;
		float sz = fx * upY - fy * upX;
		float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		sx /= sl;
		sy /= sl;
		sz /= sl;
		float ux = sy * fz - sz * fy;
		float uy = sz * fx - sx * fz;
		float uz = sx * fy - sy * fx;
		MATRIX.clear();
		MATRIX.put(sx).put(ux).put(-fx).put(0);
		MATRIX.put(sy).put(uy).put(-fy).put(0);
		MATRIX.put(sz).put(uz).put(-fz).put(0);
		MATRIX.put(0).put(0).put(0).put(1);
		MATRIX.flip();
		GL11.glMultMatrixf(MATRIX);
		GL11.glTranslatef(-eyeX, -eyeY, -eyeZ);
	}

	public static String gluErrorString(int error) {
		return switch (error) {
			case GL11.GL_NO_ERROR -> "No error";
			case GL11.GL_INVALID_ENUM -> "Invalid enum";
			case GL11.GL_INVALID_VALUE -> "Invalid value";
			case GL11.GL_INVALID_OPERATION -> "Invalid operation";
			case GL11.GL_STACK_OVERFLOW -> "Stack overflow";
			case GL11.GL_STACK_UNDERFLOW -> "Stack underflow";
			case GL11.GL_OUT_OF_MEMORY -> "Out of memory";
			case 0x0506 -> "Invalid framebuffer operation";
			default -> null;
		};
	}
}
