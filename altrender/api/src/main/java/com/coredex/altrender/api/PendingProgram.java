/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

/** A program being built by {@link ShaderPrograms#build}. Render thread only. */
public interface PendingProgram {

	/** Whether the build has finished; never waits. */
	boolean done();

	/** Whether it built; waits for the build if it hasn't finished. */
	boolean ok();

	/** The GL program name once built ({@link #ok()}), else 0; waits for the build if it hasn't finished. */
	int program();

	/** Compile and link messages; waits for the build if it hasn't finished. */
	String log();

	/** Frees the program (an unfinished build is abandoned). */
	void delete();
}
