# LWJGL DebugLoader output, Linux x64 (b1.7.3, production-shaped Knot launch, 2026-11-12)

Run with `-Dorg.lwjgl.util.Debug=true -Dorg.lwjgl.util.DebugLoader=true`; paths shortened to `<gameDir>`.
Every LWJGL native loads from Altrender's folder; `libEGL.so.1` is the system's (LWJGL probes it; GL entry points
come from SDL). `lwjgl_stb` isn't loaded until something uses stb.

```
[Altrender] natives: linux-x64, 5 libraries (8 MB) from altrender/natives/fecad1c4a7b19f48, all present
[LWJGL] Version: 3.4.3-snapshot
[LWJGL] Loading JNI library: lwjgl
	Module: org.lwjgl
	Loaded from org.lwjgl.librarypath: <gameDir>/altrender/natives/fecad1c4a7b19f48/liblwjgl.so
[LWJGL] MemoryUtil backend: sun.misc.Unsafe
[LWJGL] Java 11 check intrinsics enabled
[LWJGL] Loading library: SDL3
	Module: org.lwjgl.sdl
	Loaded from org.lwjgl.librarypath: <gameDir>/altrender/natives/fecad1c4a7b19f48/libSDL3.so
[LWJGL] MemoryUtil allocator: org.lwjgl.system.MemoryManage$StdlibAllocator
[LWJGL] Loading JNI library: lwjgl_opengl
	Module: org.lwjgl.opengl
	Loaded from org.lwjgl.librarypath: <gameDir>/altrender/natives/fecad1c4a7b19f48/liblwjgl_opengl.so
[LWJGL] Loading library: libEGL.so.1
	Module: org.lwjgl.opengl
	libEGL.so.1 not found in org.lwjgl.librarypath=<gameDir>/altrender/natives/fecad1c4a7b19f48
	Loaded from system paths: /usr/lib64/libEGL.so.1
[LWJGL] Loading library: openal
	Module: org.lwjgl.openal
	Loaded from org.lwjgl.librarypath: <gameDir>/altrender/natives/fecad1c4a7b19f48/libopenal.so
```
