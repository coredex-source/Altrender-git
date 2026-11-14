/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.immediate;

import java.nio.ByteBuffer;

import com.coredex.altrender.Log;
import com.coredex.altrender.debug.GlDebug;
import com.coredex.altrender.util.LongMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.system.MemoryUtil;

/**
 * Static display lists as buffers (development.md 3.3): while a chosen list compiles (the sky's, built once), its
 * tessellator draws are recorded too; if they share one vertex format and the list sets no state (the tracker's touch
 * mask), they become one buffer, and calling the list draws that buffer with the same fixed-function pointers. The
 * game's list is still compiled (fallback). Main thread only.
 */
public final class ListCapture {

	private record StaticDraw(int buffer, boolean textured, boolean colored, boolean normals, int mode, int vertices) {
	}

	private static boolean captureNext;
	private static int capturing;
	private static ByteBuffer bytes;
	private static boolean valid;
	private static int flags = -1;
	private static int mode;
	private static int vertices;
	private static final LongMap<StaticDraw> DRAWS = new LongMap<>();

	private ListCapture() {
	}

	/** Lists compiled while this is on are captured (the world renderer's constructor: the sky lists). */
	public static void captureCompiles(boolean on) {
		captureNext = on && ImmediateStream.get() != null;
	}

	/** From the tracker's glNewList. */
	public static void onNewList(int list) {
		if (captureNext) {
			capturing = list;
			valid = true;
			flags = -1;
			vertices = 0;
			if (bytes == null) {
				bytes = MemoryUtil.memAlloc(1 << 20);
			}
			bytes.clear();
		}
	}

	/** A tessellator draw while compiling (bytes from position 0 to its limit, 32-byte vertices). */
	public static void onTesselatorDraw(ByteBuffer data, boolean textured, boolean colored, boolean normals, int drawMode,
			int count) {
		if (capturing == 0 || !valid) {
			return;
		}
		int f = (textured ? 1 : 0) | (colored ? 2 : 0) | (normals ? 4 : 0);
		if (flags >= 0 && (f != flags || drawMode != mode)) {
			valid = false;
			return;
		}
		flags = f;
		mode = drawMode;
		if (bytes.remaining() < data.limit()) {
			ByteBuffer bigger = MemoryUtil.memAlloc(Math.max(bytes.capacity() * 2, bytes.position() + data.limit()));
			bytes.flip();
			bigger.put(bytes);
			MemoryUtil.memFree(bytes);
			bytes = bigger;
		}
		data.position(0);
		bytes.put(data);
		vertices += count;
	}

	/** From the tracker's glEndList: {@code touch} is what the list changes besides drawing (0 = nothing). */
	public static void onEndList(int list, int touch) {
		if (capturing != list) {
			return;
		}
		capturing = 0;
		if (!valid || touch != 0 || vertices == 0) {
			return;
		}
		bytes.flip();
		int buffer = GL15C.glGenBuffers();
		int previous = org.lwjgl.opengl.GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, buffer);
		GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, bytes, GL15C.GL_STATIC_DRAW);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, previous);
		GlDebug.label(org.lwjgl.opengl.GL43C.GL_BUFFER, buffer, "static-list " + list);
		DRAWS.put(list, new StaticDraw(buffer, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, mode, vertices));
		Log.info("static list " + list + ": " + vertices + " vertices in one buffer");
	}

	/** Draws the list's buffer instead of the list; false if the list wasn't captured. */
	public static boolean draw(int list) {
		if (DRAWS.isEmpty()) {
			return false;
		}
		StaticDraw d = DRAWS.get(list);
		if (d == null) {
			return false;
		}
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, d.buffer());
		if (d.textured()) {
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 32, 12L);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		if (d.colored()) {
			GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 32, 20L);
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		}
		if (d.normals()) {
			GL11.glNormalPointer(GL11.GL_BYTE, 32, 24L);
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		}
		GL11.glVertexPointer(3, GL11.GL_FLOAT, 32, 0L);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glDrawArrays(d.mode(), 0, d.vertices());
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		if (d.textured()) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		if (d.colored()) {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		}
		if (d.normals()) {
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
		}
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
		return true;
	}

	/** glDeleteLists: captured buffers of those lists go too. */
	public static void onDeleteLists(int list, int range) {
		if (DRAWS.isEmpty()) {
			return;
		}
		for (int l = list; l < list + range; l++) {
			StaticDraw d = DRAWS.get(l);
			if (d != null) {
				GL15C.glDeleteBuffers(d.buffer());
				DRAWS.remove(l);
			}
		}
	}

	/** Shutdown or failure: every captured buffer goes. */
	public static void deleteAll() {
		for (StaticDraw d : DRAWS.values()) {
			GL15C.glDeleteBuffers(d.buffer());
		}
		DRAWS.clear();
		if (bytes != null) {
			MemoryUtil.memFree(bytes);
			bytes = null;
		}
	}
}
