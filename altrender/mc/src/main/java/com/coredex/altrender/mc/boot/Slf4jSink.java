/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.mc.boot;

import com.coredex.altrender.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes {@link Log} to slf4j. Ornithe's dev setup and some launchers provide slf4j; the plain Ornithe profile doesn't,
 * so check {@link #available()} first (this class must not load without slf4j).
 */
final class Slf4jSink {

	private Slf4jSink() {
	}

	static boolean available() {
		try {
			Class.forName("org.slf4j.LoggerFactory", false, Slf4jSink.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	static Log.Sink create(String name) {
		Logger logger = LoggerFactory.getLogger(name);
		return (level, message, error) -> {
			switch (level) {
				case INFO -> logger.info(message, error);
				case WARN -> logger.warn(message, error);
				case ERROR -> logger.error(message, error);
			}
		};
	}
}
