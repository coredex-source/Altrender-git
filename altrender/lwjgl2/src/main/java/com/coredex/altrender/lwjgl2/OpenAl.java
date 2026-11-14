/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.lwjgl2;

import com.coredex.altrender.Log;
import org.lwjgl.LWJGLException;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

/**
 * LWJGL2's {@code AL.create()} on LWJGL 3 + OpenAL Soft: the default device and one context, current for the process.
 * The device and context are closed by a shutdown hook (LWJGL 3's own {@code AL.destroy()} only clears its AL state).
 */
public final class OpenAl {

	private static long device;
	private static long context;

	private OpenAl() {
	}

	public static synchronized void create() throws LWJGLException {
		if (context != 0) {
			throw new IllegalStateException("OpenAL already created");
		}
		device = ALC10.alcOpenDevice((CharSequence) null);
		if (device == 0) {
			throw new LWJGLException("could not open the default OpenAL device");
		}
		ALCCapabilities alc = ALC.createCapabilities(device);
		context = ALC10.alcCreateContext(device, (int[]) null);
		if (context == 0 || !ALC10.alcMakeContextCurrent(context)) {
			ALC10.alcCloseDevice(device);
			device = 0;
			context = 0;
			throw new LWJGLException("could not create an OpenAL context");
		}
		AL.createCapabilities(alc);
		Log.info("openal=[" + org.lwjgl.openal.AL10.alGetString(org.lwjgl.openal.AL10.AL_VERSION) + "] device="
				+ ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER));
		Runtime.getRuntime().addShutdownHook(new Thread(OpenAl::close, "Altrender OpenAL shutdown"));
	}

	public static synchronized boolean isCreated() {
		return context != 0;
	}

	private static synchronized void close() {
		if (context != 0) {
			ALC10.alcMakeContextCurrent(0);
			ALC10.alcDestroyContext(context);
			context = 0;
		}
		if (device != 0) {
			ALC10.alcCloseDevice(device);
			device = 0;
		}
	}
}
