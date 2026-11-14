/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import com.coredex.altrender.api.Desktop;

/**
 * The API's {@link Desktop} on SDL3 (development.md 6.3): {@code SDL_OpenURL} with a {@code file://} URL for a
 * folder, {@code SDL_ShowOpenFileDialog} for files. SDL may answer a dialog on another thread: the answer waits in a
 * queue for {@link #poll} on the render thread (SdlWindow.pump, every frame).
 */
public final class SdlDesktop implements Desktop {

	private final Queue<Runnable> answers = new ConcurrentLinkedQueue<>();

	@Override
	public boolean openFolder(Path folder) {
		return SDLMisc.SDL_OpenURL(folder.toAbsolutePath().toUri().toString());
	}

	@Override
	public void chooseFiles(String title, List<String> extensions, Consumer<List<Path>> chosen) {
		SDL_DialogFileFilter.Buffer filters = null;
		java.nio.ByteBuffer name = null;
		java.nio.ByteBuffer pattern = null;
		if (!extensions.isEmpty()) {
			name = MemoryUtil.memUTF8(title);
			pattern = MemoryUtil.memUTF8(String.join(";", extensions));
			filters = SDL_DialogFileFilter.calloc(1);
			filters.get(0).name(name).pattern(pattern);
		}
		java.nio.ByteBuffer n = name;
		java.nio.ByteBuffer pt = pattern;
		SDL_DialogFileFilter.Buffer f = filters;
		SDL_DialogFileCallback[] holder = new SDL_DialogFileCallback[1];
		holder[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			List<Path> files = new ArrayList<>();
			if (filelist != 0) {
				PointerBuffer list = MemoryUtil.memPointerBuffer(filelist, Integer.MAX_VALUE);
				for (int i = 0; list.get(i) != 0; i++) {
					files.add(Path.of(MemoryUtil.memUTF8(list.get(i))));
				}
			}
			answers.add(() -> {
				chosen.accept(files);
				holder[0].free();
				if (f != null) {
					MemoryUtil.memFree(n);
					MemoryUtil.memFree(pt);
					f.free();
				}
			});
		});
		SDLDialog.SDL_ShowOpenFileDialog(holder[0], 0, 0, filters, (CharSequence) null, true);
	}

	/** Runs the dialogs' answers (render thread, once a frame). */
	public void poll() {
		Runnable r;
		while ((r = answers.poll()) != null) {
			r.run();
		}
	}
}
