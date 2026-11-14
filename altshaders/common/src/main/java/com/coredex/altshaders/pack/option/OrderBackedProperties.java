/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/shaderpack/option/OrderBackedProperties.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; no Minecraft, fastutil, Guava or JOML types (Java's own
 * collections and small value types instead); no GL calls (formats and blend modes are data here).
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.pack.option;

import java.util.LinkedHashMap;
import java.util.HashMap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

/**
 * Properties backed by a {@link java.util.LinkedHashMap}, in order to preserve iteration order
 */
public class OrderBackedProperties extends Properties {
	private transient final Map<Object, Object> backing = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

	@Override
	public synchronized Object put(Object key, Object value) {
		backing.put(key, value);

		return super.put(key, value);
	}

	@Override
	public synchronized void forEach(BiConsumer<? super Object, ? super Object> action) {
		this.backing.forEach(action);
	}

	@Override
	public void store(OutputStream out, String comments) throws IOException {
		customStore0(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.ISO_8859_1)),
			comments, true);
	}

	@Override
	public void store(Writer out, String comments) throws IOException {
		customStore0(new BufferedWriter(out),
			comments, true);
	}

	//Override to stop '/' or ':' chars from being replaced by not called
	//saveConvert(key, true, escUnicode)
	private void customStore0(BufferedWriter bw, String comments, boolean escUnicode)
		throws IOException {
		bw.write("#" + new Date());
		bw.newLine();
		synchronized (this) {
			for (Enumeration<?> e = keys(); e.hasMoreElements(); ) {
				String key = (String) e.nextElement();
				String val = (String) get(key);
				// Commented out to stop '/' or ':' chars being replaced
				//key = saveConvert(key, true, escUnicode);
				//val = saveConvert(val, false, escUnicode);
				bw.write(key + "=" + val);
				bw.newLine();
			}
		}
		bw.flush();
	}
}
