/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import com.coredex.altshaders.pack.helpers.StringPair;
import com.coredex.altshaders.pack.option.values.MutableOptionValues;
import com.coredex.altshaders.pack.option.values.OptionValues;

/**
 * The {@code shaderpacks/} folder (development.md 6.2), as Iris and OptiFine use it: a pack is a folder with a
 * {@code shaders/} folder in it, or a zip with a {@code shaders/} folder at its top or deeper (some zips wrap the pack
 * in a folder of its own). A pack's changed options are kept in {@code shaderpacks/<pack>.txt} (OptiFine's format,
 * ISO-8859-1 properties); the file is removed when no option differs from the pack's default.
 */
public final class PackLoader {

	private PackLoader() {
	}

	/** A loaded pack; {@link #close} it when it's replaced (a zip pack keeps its zip open while loaded). */
	public record Loaded(String name, ShaderPack pack, FileSystem zip) implements AutoCloseable {
		@Override
		public void close() {
			if (zip != null) {
				try {
					zip.close();
				} catch (IOException e) {
					PackEnv.LOG.warn("can't close " + name + ": " + e.getMessage());
				}
			}
		}
	}

	/** The packs in {@code dir}, by name (folder or zip file name), sorted case-insensitively. */
	public static List<String> list(Path dir) {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return names;
		}
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
			for (Path p : entries) {
				if (isPack(p)) {
					names.add(p.getFileName().toString());
				}
			}
		} catch (IOException e) {
			PackEnv.LOG.warn("can't list " + dir + ": " + e.getMessage());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static boolean isPack(Path p) {
		if (Files.isDirectory(p)) {
			return Files.isDirectory(p.resolve("shaders"));
		}
		if (!p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
			return false;
		}
		try (FileSystem zip = FileSystems.newFileSystem(p)) {
			return findShaders(zip).isPresent();
		} catch (ZipException e) {
			PackEnv.LOG.warn("the zip " + p + " is corrupt");
		} catch (IOException | RuntimeException e) {
			// not a pack
		}
		return false;
	}

	/** {@code shaders/} at the zip's top, else the first folder named {@code shaders} in it. */
	private static Optional<Path> findShaders(FileSystem zip) throws IOException {
		Path top = zip.getPath("shaders");
		if (Files.isDirectory(top)) {
			return Optional.of(top);
		}
		Path root = zip.getRootDirectories().iterator().next();
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(Files::isDirectory)
					.filter(p -> p.getFileName() != null && p.getFileName().toString().equals("shaders"))
					.min(Comparator.comparingInt(Path::getNameCount));
		}
	}

	/**
	 * Loads pack {@code name} from {@code dir} with its saved options, then {@code queued} changes on top (the pack
	 * screen's unsaved ones), or none of either if {@code reset}; saves the options it ends up with.
	 *
	 * @throws IOException                on unreadable files or a pack without a {@code shaders/} folder
	 * @throws UnsupportedPackException   if the pack requires features this GPU or AltShaders lacks
	 */
	public static Loaded load(Path dir, String name, Map<String, String> queued, boolean reset,
			List<StringPair> defines) throws IOException {
		Path packPath;
		Path txt;
		try {
			packPath = dir.resolve(name);
			txt = dir.resolve(name + ".txt");
		} catch (InvalidPathException e) {
			throw new IOException("the pack name \"" + name + "\" isn't a valid path");
		}
		Map<String, String> changed = new LinkedHashMap<>();
		if (!reset) {
			changed.putAll(readOptions(txt));
			changed.putAll(queued);
		}
		FileSystem zip = null;
		Path shaders;
		if (Files.isDirectory(packPath)) {
			shaders = packPath.resolve("shaders");
			if (!Files.isDirectory(shaders)) {
				throw new IOException("the pack \"" + name + "\" has no shaders folder");
			}
		} else {
			try {
				zip = FileSystems.newFileSystem(packPath);
			} catch (ZipException e) {
				throw new IOException("the pack \"" + name + "\" is a corrupt zip; download it again", e);
			}
			Optional<Path> found = findShaders(zip);
			if (found.isEmpty()) {
				zip.close();
				throw new IOException("the pack \"" + name + "\" has no shaders folder");
			}
			shaders = found.get();
		}
		try {
			ShaderPack pack = new ShaderPack(shaders, changed, defines, zip != null);
			saveOptions(txt, pack.getShaderPackOptions().getOptionValues());
			PackEnv.LOG.info("using shader pack " + name);
			return new Loaded(name, pack, zip);
		} catch (IOException | RuntimeException e) {
			if (zip != null) {
				zip.close();
			}
			throw e;
		}
	}

	/** {@code <pack>.txt}; missing or unreadable is none. */
	public static Map<String, String> readOptions(Path txt) {
		Map<String, String> m = new LinkedHashMap<>();
		if (!Files.exists(txt)) {
			return m;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(txt)) {
			p.load(in); // ISO-8859-1, as OptiFine specifies
		} catch (IOException | IllegalArgumentException e) {
			PackEnv.LOG.warn("can't read " + txt + " (" + e.getMessage() + "); using the pack's defaults");
			return m;
		}
		p.stringPropertyNames().stream().sorted().forEach(k -> m.put(k, p.getProperty(k)));
		return m;
	}

	/** The options that differ from the pack's defaults, sorted; the file is removed when there are none. */
	public static void saveOptions(Path txt, OptionValues values) {
		Map<String, String> changed = new TreeMap<>();
		MutableOptionValues copy = values.mutableCopy();
		copy.getBooleanValues().forEach((k, v) -> changed.put(k, Boolean.toString(v)));
		changed.putAll(copy.getStringValues());
		try {
			if (changed.isEmpty()) {
				Files.deleteIfExists(txt);
				return;
			}
			Properties p = new Properties();
			p.putAll(changed);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			p.store(bytes, null); // ISO-8859-1, other characters escaped
			// Properties writes a date line and hash order; keep the file stable instead.
			List<String> lines = bytes.toString(StandardCharsets.ISO_8859_1).lines().filter(l -> !l.startsWith("#"))
					.sorted().toList();
			Files.writeString(txt, String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			PackEnv.LOG.warn("can't write " + txt + ": " + e.getMessage());
		}
	}
}
