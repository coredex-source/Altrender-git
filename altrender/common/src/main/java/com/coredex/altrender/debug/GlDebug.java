/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.debug;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.coredex.altrender.Log;
import com.coredex.altrender.caps.GlCaps;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLDebugMessageCallback;

/**
 * KHR_debug (development.md 1.3): the driver's messages in Altrender's log, filtered by severity, plus {@code ar:}
 * object labels and debug groups for apitrace/RenderDoc. Everything is a no-op without KHR_debug.
 * <p>
 * Severity: {@code -Daltrender.glDebug.severity=high|medium|low|notification}; default {@code medium} in a debug
 * context ({@code -Daltrender.glDebug}, which also makes output synchronous so each line names its Java call site),
 * {@code high} otherwise. Each message id is logged 10 times, then every 1000th time.
 */
public final class GlDebug {

	private static final int PER_ID = 10;
	private static final String[] SEVERITIES = {"notification", "low", "medium", "high"};
	private static final int[] SEVERITY_ENUMS = {GL43C.GL_DEBUG_SEVERITY_NOTIFICATION, GL43C.GL_DEBUG_SEVERITY_LOW,
			GL43C.GL_DEBUG_SEVERITY_MEDIUM, GL43C.GL_DEBUG_SEVERITY_HIGH};
	private static final StackWalker WALKER = StackWalker.getInstance();

	private static boolean available;
	private static boolean synchronous;
	private static int maxLabel;
	private static GLDebugMessageCallback callback;
	private static final Map<Long, Integer> COUNTS = new ConcurrentHashMap<>();
	private static volatile int errors;

	private GlDebug() {
	}

	public static void install(GlCaps caps) {
		if (Boolean.getBoolean("altrender.debug.glDebug.disable")) {
			Log.info("gl debug: off (-Daltrender.debug.glDebug.disable)");
			return;
		}
		available = caps.khrDebug;
		if (!available) {
			Log.info("gl debug: no KHR_debug, no driver messages, labels or groups");
			return;
		}
		maxLabel = GL11C.glGetInteger(GL43C.GL_MAX_LABEL_LENGTH);
		synchronous = (GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS) & GL43C.GL_CONTEXT_FLAG_DEBUG_BIT) != 0;
		String wanted = System.getProperty("altrender.glDebug.severity", synchronous ? "medium" : "high");
		int level = indexOf(wanted);
		if (level < 0) {
			Log.warn("-Daltrender.glDebug.severity=" + wanted + " ignored (high, medium, low or notification)");
			level = synchronous ? 2 : 3;
		}
		callback = GLDebugMessageCallback.create(GlDebug::message);
		GL43C.glDebugMessageCallback(callback, 0L);
		GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT);
		if (synchronous) {
			GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT_SYNCHRONOUS);
		}
		// The driver drops what's below the level, so filtered messages cost nothing.
		GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, (IntBuffer) null, false);
		for (int i = level; i < SEVERITIES.length; i++) {
			GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, SEVERITY_ENUMS[i], (IntBuffer) null, true);
		}
		Log.info("gl debug: driver messages at severity " + SEVERITIES[level] + " and above"
				+ (synchronous ? " (debug context, synchronous)" : ""));
		if (Boolean.getBoolean("altrender.debug.glDebugTest")) {
			Log.info("gl debug: test, binding buffer target 0xDEAD (expect one error line)");
			GL15C.glBindBuffer(0xDEAD, 0);
			int query = GL15C.glGenQueries();
			GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP);
			label(GL43C.GL_QUERY, query, "debug-test");
			Log.info("gl debug: test, a labelled query reads back as \"" + GL43C.glGetObjectLabel(GL43C.GL_QUERY, query) + "\"");
			GL15C.glDeleteQueries(query);
		}
	}

	public static void shutdown() {
		if (callback != null) {
			GL43C.glDebugMessageCallback(null, 0L);
			callback.free();
			callback = null;
		}
	}

	/** Error messages the driver reported so far (self-tests compare before and after). */
	public static int errors() {
		return errors;
	}

	/** Labels a GL object {@code ar:<label>} (identifier: {@code GL_BUFFER}, {@code GL_QUERY}, …). */
	public static void label(int identifier, int name, String label) {
		if (available) {
			String l = "ar:" + label;
			GL43C.glObjectLabel(identifier, name, l.length() < maxLabel ? l : l.substring(0, maxLabel - 1));
		}
	}

	/** Opens debug group {@code ar:<name>}; pair with {@link #pop}. */
	/** "ar:" + name, made once per name (pushed every frame). */
	private static final java.util.Map<String, String> LABELS = new java.util.HashMap<>();

	public static void push(String name) {
		if (available) {
			GL43C.glPushDebugGroup(GL43C.GL_DEBUG_SOURCE_APPLICATION, 0, LABELS.computeIfAbsent(name, n -> "ar:" + n));
		}
	}

	public static void pop() {
		if (available) {
			GL43C.glPopDebugGroup();
		}
	}

	private static void message(int source, int type, int id, int severity, int length, long message, long user) {
		if (type == GL43C.GL_DEBUG_TYPE_ERROR) {
			errors++;
		}
		long key = (long) source << 48 ^ (long) type << 32 ^ (id & 0xFFFFFFFFL);
		int n = COUNTS.merge(key, 1, Integer::sum);
		if (n > PER_ID && n % 1000 != 0) {
			return;
		}
		StringBuilder line = new StringBuilder("gl ").append(severityName(severity)).append(' ').append(typeName(type))
				.append(" (").append(sourceName(source)).append(" #").append(Integer.toUnsignedString(id)).append("): ")
				.append(GLDebugMessageCallback.getMessage(length, message).trim());
		if (n == PER_ID) {
			line.append(" [further ones with this id: every 1000th]");
		} else if (n > PER_ID) {
			line.append(" [").append(n).append(" so far]");
		}
		if (synchronous) {
			callSite().ifPresent(site -> line.append(" at ").append(site));
		}
		if (severity == GL43C.GL_DEBUG_SEVERITY_HIGH || type == GL43C.GL_DEBUG_TYPE_ERROR) {
			Log.warn(line.toString());
		} else {
			Log.info(line.toString());
		}
	}

	/** The first Java frame that isn't LWJGL or this class: the code that made the GL call. */
	private static Optional<String> callSite() {
		return WALKER.walk(frames -> frames
				.filter(f -> !f.getClassName().startsWith("org.lwjgl.") && !f.getClassName().equals(GlDebug.class.getName()))
				.findFirst()
				.map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber()));
	}

	private static int indexOf(String severity) {
		for (int i = 0; i < SEVERITIES.length; i++) {
			if (SEVERITIES[i].equals(severity)) {
				return i;
			}
		}
		return -1;
	}

	private static String severityName(int s) {
		return switch (s) {
			case GL43C.GL_DEBUG_SEVERITY_HIGH -> "HIGH";
			case GL43C.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM";
			case GL43C.GL_DEBUG_SEVERITY_LOW -> "LOW";
			default -> "NOTE";
		};
	}

	private static String typeName(int t) {
		return switch (t) {
			case GL43C.GL_DEBUG_TYPE_ERROR -> "error";
			case GL43C.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "deprecated";
			case GL43C.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "undefined behaviour";
			case GL43C.GL_DEBUG_TYPE_PORTABILITY -> "portability";
			case GL43C.GL_DEBUG_TYPE_PERFORMANCE -> "performance";
			case GL43C.GL_DEBUG_TYPE_MARKER -> "marker";
			case GL43C.GL_DEBUG_TYPE_PUSH_GROUP, GL43C.GL_DEBUG_TYPE_POP_GROUP -> "group";
			default -> "other";
		};
	}

	private static String sourceName(int s) {
		return switch (s) {
			case GL43C.GL_DEBUG_SOURCE_API -> "api";
			case GL43C.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "window system";
			case GL43C.GL_DEBUG_SOURCE_SHADER_COMPILER -> "shader compiler";
			case GL43C.GL_DEBUG_SOURCE_THIRD_PARTY -> "third party";
			case GL43C.GL_DEBUG_SOURCE_APPLICATION -> "application";
			default -> "other";
		};
	}
}
