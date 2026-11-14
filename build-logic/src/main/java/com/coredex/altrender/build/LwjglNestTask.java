/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Prepares the jars Altrender nests (Fabric jar-in-jar): LWJGL 3's jars and the LWJGL2 layer's jar.
 * <ul>
 * <li>Merge (development.md 0.4, owner 2026-11-12): every class under {@code donorPackage} in {@code donors} is a donor
 * for the LWJGL 3 class of the same name below that package ({@code <donorPackage>/org/lwjgl/opengl/GL11} →
 * {@code org/lwjgl/opengl/GL11}). Its public static methods are copied into that class. A method annotated
 * {@code @<donorPackage>.Replace} replaces the existing method with its signature (development.md 1.4: the state
 * tracker); any other clash is an error, as is a {@code @Replace} with nothing to replace. Donors must not refer to
 * themselves or use invokedynamic; every donor must find its class.</li>
 * <li>Count: in each class listed in {@code countCalls} ({@code owner=counterOwner.field}), every original public static
 * method whose name starts with {@code gl} first increments the static int counter (FrameStats' GL calls, 1.3).
 * Methods a donor added aren't counted (they forward to counted ones); replaced ones are. JNI natives have no body
 * and stay uncounted unless a donor replaces them.</li>
 * <li>Publicize: methods listed as {@code owner.name(desc)} (internal names) are made public. For LWJGL2 calls whose
 * LWJGL 3 counterpart exists but isn't public (and so can't be redefined), e.g. {@code AL.destroy()}.</li>
 * <li>Each output jar gets a {@code fabric.mod.json} (nested jars must be mods); a modified jar also gets
 * {@code META-INF/altrender-modified.txt} naming the classes and members added.</li>
 * </ul>
 */
public abstract class LwjglNestTask extends DefaultTask {

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getJars();

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getDonors();

	@Input
	public abstract Property<String> getDonorPackage();

	@Input
	public abstract ListProperty<String> getPublicize();

	/** False leaves {@code @Replace} donors out (dev A/B of the state tracker's cost; default true). */
	@Input
	public abstract Property<Boolean> getMergeReplacements();

	/** {@code org/lwjgl/opengl/GL11=com/coredex/altrender/debug/FrameStats.glCalls} (internal names). */
	@Input
	public abstract ListProperty<String> getCountCalls();

	/** Prefix of the mod ids given to nested jars that have no fabric.mod.json (id = prefix + file name). */
	@Input
	public abstract Property<String> getModIdPrefix();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void run() throws IOException {
		String donorPkg = getDonorPackage().get();
		Map<String, ClassNode> donors = loadDonors(donorPkg);
		Set<String> unused = new LinkedHashSet<>(donors.keySet());
		Map<String, String> countCalls = new HashMap<>();
		for (String entry : getCountCalls().get()) {
			int eq = entry.indexOf('=');
			countCalls.put(entry.substring(0, eq), entry.substring(eq + 1));
		}
		Set<String> unusedCount = new LinkedHashSet<>(countCalls.keySet());
		Map<String, List<String>> publicize = new HashMap<>();
		Set<String> unusedPublicize = new LinkedHashSet<>(getPublicize().get());
		for (String entry : getPublicize().get()) {
			int dot = entry.lastIndexOf('.', entry.indexOf('('));
			publicize.computeIfAbsent(entry.substring(0, dot), k -> new ArrayList<>()).add(entry.substring(dot + 1));
		}

		Path out = getOutputDirectory().get().getAsFile().toPath();
		if (Files.isDirectory(out)) {
			try (Stream<Path> old = Files.list(out)) {
				for (Path p : (Iterable<Path>) old::iterator) {
					Files.delete(p);
				}
			}
		}
		Files.createDirectories(out);

		for (File jar : getJars().getFiles()) {
			List<String> changes = new ArrayList<>();
			Path target = out.resolve(jar.getName());
			try (ZipFile in = new ZipFile(jar); OutputStream os = Files.newOutputStream(target);
					ZipOutputStream zos = new ZipOutputStream(os)) {
				boolean hasModJson = false;
				for (ZipEntry e : java.util.Collections.list(in.entries())) {
					byte[] bytes = read(in, e);
					String name = e.getName();
					if (name.equals("fabric.mod.json")) {
						hasModJson = true;
					}
					if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
						String internal = name.substring(0, name.length() - ".class".length());
						ClassNode donor = donors.get(internal);
						List<String> pub = publicize.get(internal);
						String counter = countCalls.get(internal);
						if (donor != null || pub != null || counter != null) {
							bytes = merge(bytes, donor, pub, counter, changes);
							unusedCount.remove(internal);
							unused.remove(internal);
							if (pub != null) {
								pub.forEach(m -> unusedPublicize.remove(internal + "." + m));
							}
						}
					}
					put(zos, name, bytes);
				}
				if (!hasModJson) {
					put(zos, "fabric.mod.json", modJson(jar.getName()).getBytes(StandardCharsets.UTF_8));
				}
				if (!changes.isEmpty()) {
					String note = "Modified by Coredex for Altrender (build-time merge, development.md 0.4 and 1.4):\n"
							+ "LWJGL2 signatures added as static bridges; state-setting methods replaced by versions that\n"
							+ "also update Altrender's GL state tracker; GL11 calls counted for Altrender's frame stats.\n"
							+ "Original code: LWJGL 3, BSD-3-Clause (see META-INF/licenses/altrender/ in Altrender's jar).\n\n"
							+ String.join("\n", changes) + "\n";
					put(zos, "META-INF/altrender-modified.txt", note.getBytes(StandardCharsets.UTF_8));
				}
			}
			if (!changes.isEmpty()) {
				getLogger().lifecycle("Merged into {}: {} changes", jar.getName(), changes.size());
			}
		}
		if (!unused.isEmpty()) {
			throw new GradleException("LWJGL merge donors found no target class: " + unused);
		}
		if (!unusedPublicize.isEmpty()) {
			throw new GradleException("LWJGL publicize entries found no target: " + unusedPublicize);
		}
		if (!unusedCount.isEmpty()) {
			throw new GradleException("LWJGL countCalls entries found no target: " + unusedCount);
		}
	}

	private Map<String, ClassNode> loadDonors(String donorPkg) throws IOException {
		Map<String, ClassNode> donors = new TreeMap<>();
		String prefix = donorPkg.replace('.', '/') + "/";
		for (File root : getDonors().getFiles()) {
			if (!root.isDirectory()) {
				continue;
			}
			Path base = root.toPath();
			try (Stream<Path> walk = Files.walk(base)) {
				for (Path p : (Iterable<Path>) walk::iterator) {
					if (!p.toString().endsWith(".class")) {
						continue;
					}
					String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
					if (!rel.startsWith(prefix)) {
						continue;
					}
					ClassNode node = new ClassNode();
					new ClassReader(Files.readAllBytes(p)).accept(node, 0);
					if ((node.access & Opcodes.ACC_ANNOTATION) != 0) {
						continue; // the @Replace marker, not a donor
					}
					String target = rel.substring(prefix.length(), rel.length() - ".class".length());
					donors.put(target, node);
				}
			}
		}
		return donors;
	}

	private byte[] merge(byte[] targetBytes, ClassNode donor, List<String> publicize, String counter,
			List<String> changes) {
		ClassNode target = new ClassNode();
		new ClassReader(targetBytes).accept(target, 0);
		Map<String, MethodNode> existing = new HashMap<>();
		for (MethodNode m : target.methods) {
			existing.put(m.name + m.desc, m);
		}
		for (String sig : publicize != null ? publicize : List.<String>of()) {
			MethodNode m = existing.get(sig);
			if (m == null) {
				throw new GradleException(target.name + " has no method " + sig + " to make public");
			}
			m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
			changes.add(target.name + "." + sig + " made public");
		}
		String replaceDesc = "L" + getDonorPackage().get().replace('.', '/') + "/Replace;";
		Set<MethodNode> added = new java.util.HashSet<>();
		for (MethodNode m : donor != null ? donor.methods : List.<MethodNode>of()) {
			boolean publicStatic = (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
					== (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
			if (!publicStatic || m.name.startsWith("<")) {
				continue;
			}
			boolean replace = hasAnnotation(m.invisibleAnnotations, replaceDesc);
			if (replace && !getMergeReplacements().getOrElse(true)) {
				continue;
			}
			MethodNode old = existing.get(m.name + m.desc);
			if (replace && old == null) {
				throw new GradleException(donor.name + "." + m.name + m.desc + " is @Replace but " + target.name
						+ " has no such method");
			}
			if (!replace && old != null) {
				throw new GradleException(target.name + " already has " + m.name + m.desc + " (donor " + donor.name + ")");
			}
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof InvokeDynamicInsnNode) {
					throw new GradleException("donor " + donor.name + "." + m.name + " uses invokedynamic");
				}
				String owner = insn instanceof MethodInsnNode mi ? mi.owner
						: insn instanceof FieldInsnNode fi ? fi.owner : null;
				if (donor.name.equals(owner)) {
					throw new GradleException("donor " + donor.name + "." + m.name + " refers to its own class");
				}
			}
			m.invisibleAnnotations = null;
			m.visibleAnnotations = null;
			if (replace) {
				// Keep the original's other flags (synthetic, varargs), but never native: the donor has a body.
				m.access = old.access & ~Opcodes.ACC_NATIVE;
				target.methods.set(target.methods.indexOf(old), m);
				changes.add(target.name + "." + m.name + m.desc + " replaced");
			} else {
				target.methods.add(m);
				added.add(m);
				changes.add(target.name + "." + m.name + m.desc);
			}
		}
		if (counter != null) {
			int dot = counter.lastIndexOf('.');
			String owner = counter.substring(0, dot);
			String field = counter.substring(dot + 1);
			int counted = 0;
			int skipped = 0;
			for (MethodNode m : target.methods) {
				boolean publicStatic = (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
						== (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
				if (!publicStatic || !m.name.startsWith("gl") || added.contains(m)) {
					continue;
				}
				if ((m.access & Opcodes.ACC_NATIVE) != 0) {
					// No body to prepend to; a donor that replaces it is counted.
					skipped++;
					continue;
				}
				InsnList count = new InsnList();
				count.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, field, "I"));
				count.add(new InsnNode(Opcodes.ICONST_1));
				count.add(new InsnNode(Opcodes.IADD));
				count.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field, "I"));
				m.instructions.insert(count);
				m.maxStack = Math.max(m.maxStack, 2);
				counted++;
			}
			changes.add(target.name + ": " + counted + " gl* methods count calls in " + counter + " (" + skipped
					+ " JNI natives not replaced, not counted)");
		}
		ClassWriter cw = new ClassWriter(0);
		target.accept(cw);
		return cw.toByteArray();
	}

	private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
		if (annotations != null) {
			for (AnnotationNode a : annotations) {
				if (a.desc.equals(desc)) {
					return true;
				}
			}
		}
		return false;
	}

	private String modJson(String fileName) {
		String base = fileName.substring(0, fileName.length() - ".jar".length());
		String id = (getModIdPrefix().get() + base).toLowerCase().replaceAll("[^a-z0-9_-]", "_");
		if (id.length() > 64) {
			id = id.substring(0, 64);
		}
		return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version(base) + "\",\"name\":\"" + base
				+ "\",\"custom\":{\"fabric-loom:generated\":true}}\n";
	}

	/** The first dash-separated part that starts with a digit (lwjgl-opengl-3.4.3-natives-linux → 3.4.3). */
	private static String version(String base) {
		for (String part : base.split("-")) {
			if (!part.isEmpty() && Character.isDigit(part.charAt(0))) {
				return part;
			}
		}
		return "0.0.0";
	}

	private static byte[] read(ZipFile zip, ZipEntry e) throws IOException {
		try (InputStream is = zip.getInputStream(e)) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			is.transferTo(bos);
			return bos.toByteArray();
		}
	}

	private static void put(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
		ZipEntry e = new ZipEntry(name);
		e.setTime(315532800000L); // 1980-01-01: reproducible
		zos.putNextEntry(e);
		zos.write(bytes);
		zos.closeEntry();
	}
}
