# LWJGL DebugLoader output, Windows 10 x64 (b1.7.3, owner's Windows 10 LTSC, winkit, 2026-11-12)

From `launch-debug.bat` of the `tools/winkit` instance (Temurin 21.0.13, Intel UHD Graphics, driver 26.20.100.7985);
the kit folder is shown as `<kit>`. Every LWJGL native loads from Altrender's folder, not from the kit's `natives\\`
(LWJGL2's DLLs, as a launcher supplies them); `opengl32.dll` is the system's.

```
[06:36:28] [LF/INFO] Hooking Mixin transformer
[06:36:28] [LF/INFO] Patches:
[06:36:28] [LF/INFO]     java-modules - Applied
[06:36:28] [LF/INFO]     game-dir - Applied
[06:36:28] [LF/INFO]     indev-sound - Applied
[06:36:28] [LF/INFO]     screenshot - Applied
[06:36:28] [LF/INFO]     classic-resize - Unapplicable: Detected version past in-20100110
[06:36:28] [LF/INFO]     demo-patch - Applied
[Altrender] natives: windows-x64, 5 libraries (6 MB) from altrender\natives\c618ac4932341ddd, all present
[Altrender] loaded mc=b1.7.3 java=21.0.13+9-LTS loader=fabric legacyfix=26.10 mixin=OK
[Altrender] lwjgl=3.4.3-snapshot gl11=altrender_lwjgl-opengl-3_4_3-3.4.3-7b28c51f146cf516.jar candidates=[altrender_lwjgl-opengl-3_4_3-3.4.3-7b28c51f146cf516.jar, lwjgl-2.9.0.jar]
[Altrender] legacyfix: switched off patches the LWJGL2 layer replaces: [bitdepth, a1.1.1, vsync, deawt, mouse, disable-controllers, texture-pack-folder]
[LWJGL] Version: 3.4.3-snapshot
	 OS: Windows 10 v10.0
	JRE: Windows amd64 21.0.13
	JVM: OpenJDK 64-Bit Server VM v21.0.13+9-LTS by Eclipse Adoptium
[LWJGL] Loading JNI library: lwjgl
	Module: org.lwjgl
	Loaded from org.lwjgl.librarypath: <kit>\game\altrender\natives\c618ac4932341ddd\lwjgl.dll
[LWJGL] MemoryUtil backend: sun.misc.Unsafe
[LWJGL] Java 11 check intrinsics enabled
[LWJGL] Loading library: SDL3
	Module: org.lwjgl.sdl
	Loaded from org.lwjgl.librarypath: <kit>\game\altrender\natives\c618ac4932341ddd\SDL3.dll
[LWJGL] MemoryUtil allocator: org.lwjgl.system.MemoryManage$StdlibAllocator
[LWJGL] Loading JNI library: lwjgl_opengl
	Module: org.lwjgl.opengl
	Loaded from org.lwjgl.librarypath: <kit>\game\altrender\natives\c618ac4932341ddd\lwjgl_opengl.dll
[LWJGL] Loading library: opengl32
	Module: org.lwjgl.opengl
	opengl32.dll not found in org.lwjgl.librarypath=<kit>\game\altrender\natives\c618ac4932341ddd
	Loaded from system paths: C:\Windows\system32\opengl32.dll
[LWJGL] [GL] OpenGL46 was reported as available but an entry point is missing.
[Altrender] context=[4.6.0 - Build 26.20.100.7985 compat] renderer=Intel(R) UHD Graphics vendor=Intel requested=4.6 compat lwjgl=3.4.3-snapshot sdl=3.4.14 video=windows density=1.0
[LWJGL] Loading library: OpenAL
	Module: org.lwjgl.openal
	Loaded from org.lwjgl.librarypath: <kit>\game\altrender\natives\c618ac4932341ddd\OpenAL.dll
[Altrender] openal=[1.1 ALSOFT 1.25.2] device=OpenAL Soft
Stopping!
```
