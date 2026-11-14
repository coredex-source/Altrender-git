/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Repacks LWJGL's natives jars into Altrender's resource layout (development.md 0.5):
 * {@code altrender/natives/<os>-<arch>/<library>} plus {@code altrender/natives/<os>-<arch>/natives.index}
 * ({@code <sha256> <size> <file>} per line, sorted), and the licence texts into
 * {@code META-INF/licenses/altrender/natives/}. Fails if a platform listed in {@code platforms} gets no libraries.
 */
public abstract class PackNativesTask extends DefaultTask {

	/** LWJGL's natives layout: {@code <os>/<arch>/org/lwjgl/.../<lib>}. */
	private static final Pattern NATIVE = Pattern.compile("^(linux|windows|macos|freebsd)/(x64|arm64|arm32|x86|ppc64le|riscv64)/.+/([^/]+\\.(so|dll|dylib))$");

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getNativeJars();

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getLicenses();

	/** Expected {@code <os>-<arch>} directories, e.g. {@code linux-x64}. */
	@Input
	public abstract SetProperty<String> getPlatforms();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void run() throws IOException, NoSuchAlgorithmException {
		Path out = getOutputDirectory().get().getAsFile().toPath();
		if (Files.exists(out)) {
			try (var walk = Files.walk(out)) {
				for (Path p : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
					Files.delete(p);
				}
			}
		}
		Map<String, Map<String, String>> index = new TreeMap<>(); // platform -> file -> "sha size"
		for (File jar : getNativeJars().getFiles()) {
			try (ZipFile zip = new ZipFile(jar)) {
				for (ZipEntry e : java.util.Collections.list(zip.entries())) {
					Matcher m = NATIVE.matcher(e.getName());
					if (e.isDirectory() || !m.matches()) {
						continue;
					}
					String platform = m.group(1) + "-" + m.group(2);
					String name = m.group(3);
					byte[] bytes;
					try (InputStream is = zip.getInputStream(e)) {
						bytes = is.readAllBytes();
					}
					Path target = out.resolve("altrender/natives").resolve(platform).resolve(name);
					if (Files.exists(target)) {
						throw new GradleException("two natives jars supply " + platform + "/" + name);
					}
					Files.createDirectories(target.getParent());
					Files.write(target, bytes);
					String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
					index.computeIfAbsent(platform, k -> new TreeMap<>()).put(name, sha + " " + bytes.length);
				}
			}
		}
		for (String platform : getPlatforms().get()) {
			if (!index.containsKey(platform)) {
				throw new GradleException("no natives for " + platform + " in " + getNativeJars().getFiles());
			}
		}
		for (var p : index.entrySet()) {
			StringBuilder sb = new StringBuilder();
			p.getValue().forEach((name, shaSize) -> sb.append(shaSize).append(' ').append(name).append('\n'));
			Files.writeString(out.resolve("altrender/natives").resolve(p.getKey()).resolve("natives.index"), sb.toString(),
					StandardCharsets.UTF_8);
			getLogger().lifecycle("natives {}: {}", p.getKey(), p.getValue().keySet());
		}
		Path licenses = out.resolve("META-INF/licenses/altrender/natives");
		Files.createDirectories(licenses);
		for (File f : getLicenses().getFiles()) {
			Files.copy(f.toPath(), licenses.resolve(f.getName()));
		}
	}
}
