/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.coredex.altrender.api.AltrenderApi;
import com.coredex.altrender.api.Desktop;
import com.coredex.altrender.api.shaderpacks.ShaderPackMenu;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.pack.PackLoader;
import com.coredex.altshaders.pack.ShaderPack;
import com.coredex.altshaders.pack.option.MergedBooleanOption;
import com.coredex.altshaders.pack.option.MergedStringOption;
import com.coredex.altshaders.pack.option.OptionSet;
import com.coredex.altshaders.pack.option.StringOption;
import com.coredex.altshaders.pack.option.menu.OptionMenuBooleanOptionElement;
import com.coredex.altshaders.pack.option.menu.OptionMenuElement;
import com.coredex.altshaders.pack.option.menu.OptionMenuElementScreen;
import com.coredex.altshaders.pack.option.menu.OptionMenuLinkElement;
import com.coredex.altshaders.pack.option.menu.OptionMenuProfileElement;
import com.coredex.altshaders.pack.option.menu.OptionMenuStringOptionElement;
import com.coredex.altshaders.pack.option.values.OptionValues;
import com.coredex.altshaders.pipeline.PipelineManager;

/**
 * AltShaders' packs and options for Altrender's shader pack screen (development.md 6.3; API {@link ShaderPackMenu}):
 * the packs folder, the choice in config/altshaders.properties, and the applied pack's own menu (shaders.properties'
 * screens, names from its {@code lang/en_us}, profiles). Choices and option values wait here until Apply, which
 * saves the choice, queues the values (the reload writes them to {@code <pack>.txt}) and reloads.
 */
public final class ShaderPackMenuImpl implements ShaderPackMenu {

	private final PipelineManager manager;
	private String pendingPack;
	private Boolean pendingEnabled;
	/** Option values changed on the screen, by option name (as {@code <pack>.txt} has them). */
	private final Map<String, String> pending = new LinkedHashMap<>();
	/** Values just applied, shown until the pack has reloaded with them (the reload comes on the next frame). */
	private final Map<String, String> applying = new LinkedHashMap<>();
	private PackLoader.Loaded applyingOver;

	@Override
	public int revision() {
		PackLoader.Loaded l = manager.loaded();
		if (applyingOver != null && l != applyingOver) {
			applying.clear(); // reloaded: its own values hold them now
			applyingOver = null;
		}
		return l != null ? System.identityHashCode(l) : 0;
	}

	public ShaderPackMenuImpl(PipelineManager manager) {
		this.manager = manager;
	}

	@Override
	public List<String> packs() {
		return PackLoader.list(AltShaders.shaderpacksDir());
	}

	/** The applied pack: {@code -Daltshaders.pack} (dev runs) or the config's. */
	private static String appliedPack() {
		String override = System.getProperty("altshaders.pack");
		return override != null && !override.isBlank() ? override : ShadersConfig.pack();
	}

	private static boolean appliedEnabled() {
		String override = System.getProperty("altshaders.pack");
		return override != null ? !override.isBlank() : ShadersConfig.enabled();
	}

	@Override
	public String pack() {
		return pendingPack != null ? pendingPack : appliedPack();
	}

	@Override
	public void setPack(String name) {
		pendingPack = name.equals(appliedPack()) ? null : name;
		if (pendingPack != null) {
			pending.clear(); // another pack: its own options
		}
	}

	@Override
	public boolean enabled() {
		return pendingEnabled != null ? pendingEnabled : appliedEnabled();
	}

	@Override
	public void setEnabled(boolean on) {
		pendingEnabled = on == appliedEnabled() ? null : on;
	}

	@Override
	public boolean changed() {
		return pendingPack != null || pendingEnabled != null || !pending.isEmpty();
	}

	@Override
	public void apply() {
		if (System.getProperty("altshaders.pack") != null && (pendingPack != null || pendingEnabled != null)) {
			// The screen's choice replaces -Daltshaders.pack for the rest of the session.
			ShadersConfig.setPack(appliedPack());
			ShadersConfig.setEnabled(appliedEnabled());
			System.clearProperty("altshaders.pack");
		}
		if (pendingPack != null) {
			ShadersConfig.setPack(pendingPack);
		}
		if (pendingEnabled != null) {
			ShadersConfig.setEnabled(pendingEnabled);
		}
		if (pendingPack == null) {
			PackEnv.optionQueue().putAll(pending);
			applying.putAll(pending);
			applyingOver = manager.loaded();
		}
		ShadersConfig.save();
		pendingPack = null;
		pendingEnabled = null;
		pending.clear();
		manager.requestReload();
	}

	@Override
	public void cancel() {
		pendingPack = null;
		pendingEnabled = null;
		pending.clear();
	}

	@Override
	public String problem() {
		return manager.lastError();
	}

	@Override
	public void openFolder() {
		Desktop d = AltrenderApi.desktop();
		if (d != null) {
			d.openFolder(AltShaders.shaderpacksDir());
		}
	}

	@Override
	public void addPacks() {
		Desktop d = AltrenderApi.desktop();
		if (d != null) {
			d.chooseFiles("Shader packs", List.of("zip"), ShaderPackMenuImpl::copyIn);
		}
	}

	private static void copyIn(List<Path> files) {
		Path dir = AltShaders.shaderpacksDir();
		for (Path f : files) {
			try {
				Files.createDirectories(dir);
				Files.copy(f, dir.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
				Log.info("added shader pack " + f.getFileName());
			} catch (IOException e) {
				Log.warn("couldn't add shader pack " + f + ": " + e);
			}
		}
	}

	@Override
	public Options options() {
		PackLoader.Loaded loaded = manager.loaded();
		if (loaded == null || !loaded.name().equals(ShadersConfig.selected())) {
			return null;
		}
		return new PackOptions(loaded.name(), loaded.pack());
	}

	/** The applied pack's screens over its applied values and the pending ones. */
	private final class PackOptions implements Options {
		private final String name;
		private final ShaderPack pack;
		private final OptionSet set;
		private final OptionValues applied;
		private final Map<String, String> words;

		PackOptions(String name, ShaderPack pack) {
			this.name = name;
			this.pack = pack;
			this.set = pack.getShaderPackOptions().getOptionSet();
			this.applied = pack.getShaderPackOptions().getOptionValues();
			Map<String, String> lang = pack.getLanguageMap().getTranslations("en_us");
			this.words = lang != null ? lang : Map.of();
		}

		@Override
		public String title() {
			return name.replaceAll("\\.zip$", "");
		}

		@Override
		public Screen main() {
			return screen("", pack.getMenuContainer().mainScreen);
		}

		@Override
		public Screen screen(String id) {
			OptionMenuElementScreen s = pack.getMenuContainer().subScreens.get(id);
			return s != null ? screen(id, s) : null;
		}

		private Screen screen(String id, OptionMenuElementScreen s) {
			List<Element> elements = new ArrayList<>();
			for (OptionMenuElement e : s.elements) {
				elements.add(element(e));
			}
			String title = id.isEmpty() ? title() : words.getOrDefault("screen." + id, id);
			return new Screen(id, title, Math.max(1, s.getColumnCount()), elements);
		}

		private Element element(OptionMenuElement e) {
			if (e instanceof OptionMenuBooleanOptionElement b) {
				String opt = b.optionId;
				boolean def = b.option.getDefaultValue();
				return new Toggle(opt, label(opt), words.get("option." + opt + ".comment"),
						() -> Boolean.parseBoolean(value(opt)), def, v -> set(opt, Boolean.toString(v)));
			}
			if (e instanceof OptionMenuStringOptionElement c) {
				String opt = c.optionId;
				StringOption so = c.option;
				List<String> values = so.getAllowedValues().isEmpty() ? List.of(so.getDefaultValue())
						: so.getAllowedValues();
				return new Choice(opt, label(opt), words.get("option." + opt + ".comment"), values,
						v -> words.getOrDefault("value." + opt + "." + v, v), () -> value(opt), so.getDefaultValue(),
						v -> set(opt, v), c.slider);
			}
			if (e instanceof OptionMenuLinkElement l) {
				return new Link(l.targetScreenId, words.getOrDefault("screen." + l.targetScreenId, l.targetScreenId),
						words.get("screen." + l.targetScreenId + ".comment"));
			}
			if (e instanceof OptionMenuProfileElement p) {
				Map<String, com.coredex.altshaders.pack.option.Profile> byName = new LinkedHashMap<>();
				p.profiles.forEach(byName::put);
				return new Profile(new ArrayList<>(byName.keySet()), n -> words.getOrDefault("profile." + n, n),
						() -> currentProfile(byName), n -> {
							com.coredex.altshaders.pack.option.Profile prof = byName.get(n);
							if (prof != null) {
								prof.optionValues.forEach(this::set);
							}
						});
			}
			return new Empty();
		}

		private String label(String opt) {
			return words.getOrDefault("option." + opt, opt);
		}

		/** The value now: pending, else applied, else the default. */
		private String value(String opt) {
			String p = pending.get(opt);
			if (p == null) {
				p = applying.get(opt);
			}
			if (p != null) {
				return p;
			}
			if (set.isBooleanOption(opt)) {
				return Boolean.toString(applied.getBooleanValueOrDefault(opt));
			}
			return applied.getStringValueOrDefault(opt);
		}

		private void set(String opt, String v) {
			String now = set.isBooleanOption(opt) ? Boolean.toString(applied.getBooleanValueOrDefault(opt))
					: set.getStringOptions().containsKey(opt) ? applied.getStringValueOrDefault(opt) : null;
			if (v.equals(now)) {
				pending.remove(opt);
			} else {
				pending.put(opt, v);
			}
		}

		private String defaultOf(String opt) {
			MergedBooleanOption b = set.getBooleanOptions().get(opt);
			if (b != null) {
				return Boolean.toString(b.getOption().getDefaultValue());
			}
			MergedStringOption s = set.getStringOptions().get(opt);
			return s != null ? s.getOption().getDefaultValue() : null;
		}

		/** The profile whose values all hold now (the one setting the most of them), or null (custom). */
		private String currentProfile(Map<String, com.coredex.altshaders.pack.option.Profile> byName) {
			String best = null;
			int most = -1;
			for (Map.Entry<String, com.coredex.altshaders.pack.option.Profile> e : byName.entrySet()) {
				Map<String, String> values = e.getValue().optionValues;
				boolean all = true;
				for (Map.Entry<String, String> v : values.entrySet()) {
					if (!v.getValue().equals(value(v.getKey()))) {
						all = false;
						break;
					}
				}
				if (all && values.size() > most) {
					best = e.getKey();
					most = values.size();
				}
			}
			return best;
		}

		@Override
		public void resetAll() {
			Map<String, String> all = new HashMap<>();
			set.getBooleanOptions().keySet().forEach(o -> all.put(o, defaultOf(o)));
			set.getStringOptions().keySet().forEach(o -> all.put(o, defaultOf(o)));
			all.forEach(this::set);
		}

		@Override
		public boolean anyChanged() {
			for (String o : set.getBooleanOptions().keySet()) {
				if (!value(o).equals(defaultOf(o))) {
					return true;
				}
			}
			for (String o : set.getStringOptions().keySet()) {
				if (!value(o).equals(defaultOf(o))) {
					return true;
				}
			}
			return false;
		}
	}
}
