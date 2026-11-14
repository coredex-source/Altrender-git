/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.api;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The desktop around the game (since 0.3.0): opening a folder in the file manager and the system's file chooser,
 * through SDL3 (no extra native). Results come back on the render thread.
 */
public interface Desktop {

	/** Shows {@code folder} in the system's file manager; false if that failed. */
	boolean openFolder(Path folder);

	/**
	 * The system's open-file dialog for files with one of {@code extensions} (without dots; empty: any), several at
	 * once; {@code chosen} gets the picked files (empty if cancelled).
	 */
	void chooseFiles(String title, List<String> extensions, Consumer<List<Path>> chosen);
}
