/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.natives;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.coredex.altrender.Log;

/**
 * Supplies LWJGL 3's natives at {@code preLaunch}, before any LWJGL class loads (development.md 0.5).
 * <p>
 * Altrender's jar carries every platform's libraries under {@code altrender/natives/<os>-<arch>/} with a
 * {@code natives.index} ({@code <sha256> <size> <file>} lines). For the running platform they are extracted into
 * {@code <gameDir>/altrender/natives/<id>/}, where {@code id} is a hash of that index, so each Altrender build gets its
 * own folder and an unchanged one is reused. Only missing or damaged files are written (each through a temporary file
 * and a rename, so instances starting together don't see half-written libraries). Then {@code org.lwjgl.librarypath}
 * points at that folder: LWJGL 3 looks there first, so the launcher's LWJGL2 natives (on {@code java.library.path},
 * sometimes with the same file names) are never picked up.
 */
public final class NativesBoot {

	public static final String LWJGL_VERSION = "3.4.3";
	private static final String RESOURCE_ROOT = "altrender/natives/";

	private NativesBoot() {
	}

	/** {@code <os>-<arch>} as packed in the jar, or null when Altrender ships nothing for this system. */
	public static String platform() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		String o = os.startsWith("linux") ? "linux" : os.startsWith("windows") ? "windows" : null;
		String a = switch (arch) {
			case "amd64", "x86_64" -> "x64";
			case "aarch64", "arm64" -> "arm64";
			default -> null;
		};
		return o != null && a != null ? o + "-" + a : null;
	}

	/** Returns the folder LWJGL loads from, or null if this platform has no natives (the game will fail to start). */
	public static Path prepare(Path gameDir) {
		String platform = platform();
		if (platform == null) {
			Log.error("natives: no natives for " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
					+ " (Altrender supports Linux and Windows, x64 and arm64)", null);
			return null;
		}
		try {
			byte[] index = resource(platform + "/natives.index");
			if (index == null) {
				Log.error("natives: " + platform + " index missing from Altrender's jar", null);
				return null;
			}
			String id = hex(sha256(index)).substring(0, 16);
			Path dir = gameDir.resolve("altrender").resolve("natives").resolve(id);
			Files.createDirectories(dir);
			List<String> written = new ArrayList<>();
			long total = 0;
			int count = 0;
			for (String line : new String(index, StandardCharsets.UTF_8).split("\n")) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.trim().split(" ", 3);
				String sha = parts[0];
				long size = Long.parseLong(parts[1]);
				String name = parts[2];
				total += size;
				count++;
				Path file = dir.resolve(name);
				if (matches(file, size, sha)) {
					continue;
				}
				byte[] bytes = resource(platform + "/" + name);
				if (bytes == null || bytes.length != size || !hex(sha256(bytes)).equals(sha)) {
					throw new IOException("Altrender's jar has a damaged native: " + platform + "/" + name);
				}
				write(dir, file, bytes);
				written.add(name);
			}
			String launcherPath = System.getProperty("org.lwjgl.librarypath");
			System.setProperty("org.lwjgl.librarypath", dir.toString());
			// Anything LWJGL would still extract from the class path lands in the same folder.
			System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", dir.toString());
			// The C allocator: Altrender ships no jemalloc/rpmalloc/mimalloc, so LWJGL needn't probe for them.
			if (System.getProperty("org.lwjgl.system.allocator") == null) {
				System.setProperty("org.lwjgl.system.allocator", "system");
			}
			Log.info("natives: " + platform + ", " + count + " libraries (" + (total + 524_288) / 1_048_576 + " MB) from "
					+ gameDir.relativize(dir) + (written.isEmpty() ? ", all present" : ", extracted " + written)
					+ (launcherPath != null ? "; the launcher's org.lwjgl.librarypath is hidden" : ""));
			return dir;
		} catch (IOException | RuntimeException e) {
			Log.error("natives: could not prepare " + platform, e);
			return null;
		}
	}

	private static boolean matches(Path file, long size, String sha) throws IOException {
		return Files.isRegularFile(file) && Files.size(file) == size && hex(sha256(Files.readAllBytes(file))).equals(sha);
	}

	private static void write(Path dir, Path file, byte[] bytes) throws IOException {
		Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			Files.write(tmp, bytes);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static byte[] resource(String path) throws IOException {
		try (InputStream is = NativesBoot.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + path)) {
			return is != null ? is.readAllBytes() : null;
		}
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] bytes) {
		return HexFormat.of().formatHex(bytes);
	}
}
