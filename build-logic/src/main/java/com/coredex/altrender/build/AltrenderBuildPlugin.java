/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Puts Altrender's build task types on the build classpath (apply it to the root project with {@code apply false}). */
public class AltrenderBuildPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
	}
}
