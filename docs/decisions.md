# Decisions

Dated, one line each (development.md 0.6). Newest at the bottom.

## 2026-11-11 (Phase 0.1)

- **Licences (owner):** Altrender LGPL-3.0-only, AltShaders LGPL-3.0-only. Jars carry `COPYING` (GPL-3.0) and
  `COPYING.LESSER` (LGPL-3.0) under `META-INF/licenses/<modid>/`.
- **Toolchain pins** (checked against meta.ornithemc.net and the Ornithe, Fabric and KikuGie mavens): Gradle 9.8.2
  (Gradle itself runs on Java 25, which Loom 1.19 needs), Loom 1.19.4 (`net.fabricmc.fabric-loom-remap`), Ploceus 1.19.1
  (built against Loom 1.19), Stonecutter 0.9.8 (0.10 came out 2026-11-10: too fresh), Fabric Loader 0.20.1, Calamus gen2.
- **b1.7.3 builds:** feather-gen2 `b1.7.3+build.2`; raven client 2 / server 2; sparrow client 2 / server 2; nests
  client 7 / server 4. Loom's merged legacy jar (client + server); Ploceus applies raven/sparrow/nests per side, and needs
  them declared before `mappings`.
- **Stonecutter:** one tree per mod (`:altrender:mc`, `:altshaders:mc`), both with node `b1.7.3` mapped to semver
  `1.0.0-beta.7.3` (Fabric Loader's normalised version; Ornithe's manifest says `1.0.0-beta.1.7.3`, Fabric's is the one
  `depends` resolves against). Shared node setup in `gradle/mc-node.gradle`; per-version values in
  `versions/<mc>.properties`. Keep both controllers on the same active version.
- **One jar per mod:** api and common sources are compiled into the mc node and remapped with it (the
  multiloader pattern). `common`'s own compile has no Minecraft or loader on the classpath, so the "no MC imports" rule is
  enforced there.
- **Maven group per mod** (`com.coredex.altrender`, `com.coredex.altshaders`): both mods have projects named `common` and
  `b1.7.3`, and with one group Gradle resolved AltShaders' dependency on Altrender's node as a dependency on itself.
- **Mixins:** no annotation processor (Loom 1.19 default), so no refmaps; Loom remaps mixins statically at `remapJar`
  (`Fabric-Loom-Mixin-Remap-Type: static`). Compatibility level `JAVA_21`.
- **Logging:** each mod has a small log facade in its `common` (`[Altrender]` / `[AltShaders]` prefix). The mc layer
  routes it to slf4j when present; the Ornithe production profile has only ASM, Mixin, Calamus and Fabric Loader (no
  slf4j/log4j), so the fallback is stdout in Fabric's console format.
- **Banner hook:** `Minecraft#init()` HEAD (Feather name), from a mixin, so the banner also proves the mixin applied.
- **OSL: not needed.** `preLaunch` and mixins cover everything so far.
- **LegacyFix: `recommends`, not `depends`** (owner rule: "if the game boots without it then only recommends"). b1.7.3
  boots without it on Java 21 and 25, in the dev run and in a production-shaped launch of the built jars.
  - What 26.10 applies on b1.7.3: proxy (auth, skins, assets), java-modules, bitdepth, disable-controllers, deawt, mouse,
    game-dir, indev-sound, screenshot, a1.1.1, texture-pack-folder, demo-patch (classic-resize not applicable).
  - On Java 21+ it exits unless the JVM has `--add-opens=java.base/java.lang=ALL-UNNAMED`. Dev runs add it when LegacyFix
    is on; launcher users need it too.
  - Open for 0.4: bitdepth, disable-controllers, deawt, mouse and screenshot touch LWJGL2 and windowing, which our layer
    replaces. Check each against the layer; if one conflicts, switch it off with `-Dlf.<id>.disable` (set before
    LegacyFix's mixin plugin runs) or replace its fix in the layer.
- **Vanilla LWJGL 2.9.3 natives fail on Java 21** (`liblwjgl64.so` needs `libjawt.so` symbol version
  `SUNWprivate_1.1`). Ornithe's dev setup uses Legacy Fabric's `lwjgl 2.9.4+legacyfabric.15`, which works. Relevant to
  0.4: profiles that still ship vanilla LWJGL2 natives don't start on Java 21 without our LWJGL 3 layer (or a fixed
  LWJGL2).

## 2026-11-12 (Phase 0.2–0.4)

- **Dev-run switches** are `-Paltrender.*` Gradle properties (`gradle/dev-runs.gradle`); anything not build-side
  reaches the game as `-Daltrender.*`. The apitrace option uses apitrace's LD_PRELOAD wrapper (`glxtrace.so` /
  `egltrace.so`) rather than wrapping the java command.
- **LWJGL precedence, first result (0.4):** with LWJGL 3.4.3's Java jars nested in Altrender (Loom `include`, Fabric
  jar-in-jar), a production-shaped Knot launch resolves the game's `org.lwjgl.opengl.GL11` from the nested LWJGL 3 jar,
  ahead of the profile's LWJGL2 jar (both are on the loader; mods win). No `preLaunch` classpath surgery and no launcher
  change needed for classes Altrender supplies. Classes only LWJGL2 has (`Display`, `Keyboard`, …) still come from the
  profile until our layer supplies them too. To recheck on Prism and the Ornithe installer profile (0.4 drop-in test).
  LWJGL 3's `Version.getVersion()` reports `3.4.3-snapshot` from a nested jar (no manifest attributes on the package).
- **LWJGL2-only members of shared classes: build-time merge (owner, 2026-11-12).** The LWJGL2 overloads that LWJGL 3
  lacks on classes both have (`GL11`, `ARBOcclusionQuery`, `AL10`, `AL`; 15 members on b1.7.3,
  `docs/lwjgl2-surface/b1.7.3.md`) are added at build time as static bridges to copies of those LWJGL 3 classes, shipped
  as modified BSD-3 files with LWJGL's notice and a modification note. Rejected: a load-time rewrite of the game's call
  sites (needs hooking Mixin's transformer or per-version `@Redirect`s). Classes only LWJGL2 has (`Display`,
  `Keyboard`, …) are ours, in `org.lwjgl.*`, inside Altrender's jar.
- **The LWJGL2 layer** (`altrender:lwjgl2`): LWJGL2's API in `org.lwjgl.*` on LWJGL 3.4.3 + SDL3 + OpenAL Soft
  (`Display`, `DisplayMode`, `PixelFormat`, `GLContext`, a generated `ContextCapabilities` with every `GLCapabilities`
  flag, `Keyboard` with our own SDL-scancode → LWJGL2-code table, `Mouse`, `Cursor`, `Controllers` (no-op),
  `LWJGLException`, `Sys`, `GLU`), plus donors merged into LWJGL 3's `GL11`, `ARBOcclusionQuery`, `AL10`, `AL` and
  `AL.destroy()` made public (build-logic `LwjglNestTask`). Altrender nests the results (Fabric jar-in-jar); dev runs put
  them on the classpath and drop the game's LWJGL2. Context: compatibility 4.6 → 3.3 → legacy, logged as
  `context=[<GL_VERSION> compat]`. Text input: SDL's text events are attached to the key-down they follow (LWJGL2
  delivered both in one event).
- **b1.7.3 starts frameless.** LWJGL2 drew into an AWT canvas (`Display.setParent`); SDL3 can't. Fabric Loader launches
  applet-era versions through its own frame + `MinecraftApplet`, so `MinecraftApplet#init` (and the vanilla `main` path,
  `Minecraft#startAndConnect`) start a canvas-less `Minecraft` (`StandaloneMinecraft`) and hide the launcher's frame.
  Without a canvas vanilla never resizes and `Mouse#unlock` reads the AWT component: two small mixins cover both.
  Crashes go to the log and a message box, then exit.
- **LWJGL 3 aborts the JVM on GL calls without a current context** (LWJGL2 threw). Vanilla's crash panel calls
  `glGetString`; the frameless crash handler never touches GL.
- **LegacyFix compatibility:** its `bitdepth`, `a1.1.1`, `vsync`, `deawt`, `mouse`, `disable-controllers` and
  `texture-pack-folder` patches rewrite LWJGL2 classes the layer replaces (javassist fails on them) and fix things the
  layer does its own way, so Altrender sets `lf.<id>.disable` for them. It has to happen before LegacyFix registers its
  patches (in its mixin config plugin, before `preLaunch`). First attempt, a mixin config plugin with a low priority,
  was order-dependent (Mixin loads config plugins in Fabric's mod order, not by priority) and failed in some runs; now a
  language adapter (`EarlyHook`) does it, since Fabric creates language adapters before it bootstraps Mixin.
  LegacyFix's other patches keep running.
- **Natives, interim (until 0.5):** LWJGL 3 extracts its natives from the nested natives jars into
  `<gameDir>/altrender/natives/lwjgl-3.4.3/` (`org.lwjgl.system.SharedLibraryExtractPath`); `preLaunch` hides the
  launcher's `org.lwjgl.librarypath` (LWJGL2's natives, which can share LWJGL 3's file names). Linux x64 only.
- **macOS: not supported, not tested (owner, 2026-11-12: "we just ain't testing shit on macos").** The "inert on macOS"
  plan is dropped: nested classes win on every OS, so Altrender can't step aside there, and nobody tests it.
- **The launcher's applet frame is never shown.** Fabric's `AppletFrame` calls the applet's `init()` and `start()` and
  only then shows itself; hiding it from `init()` didn't stick (owner saw a second, white window, which a tiling window
  manager also tiles). `MinecraftApplet#init` now runs the game and doesn't return until it ends (the game exits the
  process itself), so the frame is never mapped.
- Observed: under niri + xwayland-satellite, SDL3 (LWJGL 3.4.3's build) chose X11 (XWayland) rather than native
  Wayland. 0.5's SDL hints decide the video driver on purpose.

## 2026-11-12 (Phase 0.5)

- **Natives live in Altrender's jar, not in nested jars:** `altrender/natives/<os>-<arch>/` (linux-x64, linux-arm64,
  windows-x64, windows-arm64; LWJGL 3.4.3's own builds of lwjgl, lwjgl_opengl, lwjgl_stb, SDL3 3.4.14, OpenAL Soft
  1.25.2) with a `natives.index` (sha256, size, name). Packed by build-logic `PackNativesTask`. LWJGL's Java jars stay
  nested. Licence texts (LWJGL, libffi, Khronos, SDL3, OpenAL Soft, stb) in `META-INF/licenses/altrender/natives/`.
- **Extraction (`NativesBoot`, preLaunch):** into `<gameDir>/altrender/natives/<first 16 hex of the index's sha256>/`,
  only missing or damaged files (size + sha256), via temp file + rename; then `org.lwjgl.librarypath` (and LWJGL's
  extract path) point there, which also hides the launcher's LWJGL2 natives. One info line. Old folders aren't cleaned
  up (a running instance may hold them on Windows).
- **GL function pointers come from SDL** (`GL.create(SDL_GL_GetProcAddress)`): LWJGL's own lookup picked EGL for SDL's
  GLX context under X11.
- **LWJGL allocator:** `system` (no jemalloc/rpmalloc/mimalloc shipped; saves LWJGL probing for them).
- **SDL3 hints:** app id `minecraft`; video driver = SDL's choice (SDL 3.4 takes Wayland only where the compositor has
  the frame-pacing protocols it wants; under niri it picks X11), overridable with `-Daltrender.videoDriver`; raw relative
  mouse (`SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE=0`); high-DPI window (`SDL_WINDOW_HIGH_PIXEL_DENSITY`): the game sees
  pixels, SDL's point coordinates are scaled, grabbed (relative) deltas aren't. The context log line now names the SDL
  version, video driver and pixel density.
- **Jar size:** 14.7 MB (14,727,699 bytes) for the b1.7.3 Altrender jar with all four platforms (one jar, owner).
- **OpenAL Soft source (LGPL): attached to every release (owner).** LWJGL 3.4.3 built OpenAL Soft from commit
  `7f9c6de1162b1d4604b9a2f8bcd6c53021566221` (the natives jars' `.git` files, same on all four platforms; the commit is
  in both LWJGL-CI/openal-soft and kcat/openal-soft, identical archives). `./gradlew :altrender:lwjgl2:openalSource`
  fetches it, checksum-pinned (`gradle.properties`); the jar's `OPENAL_SOFT_SOURCE.txt` says where it is. Added to the
  release legal checklist.
- **Windows test kit** (`tools/winkit/build.py`, owner asked for an offline instance like PaleoRender's): a folder +
  zip with `launch.bat` / `launch-debug.bat` (Java argument files), an optional bundled Temurin JRE (21 default, 25),
  Fabric Loader's libraries from the Ornithe profile, the game's own LWJGL 2.9.0 / jinput jars and Windows natives on
  `java.library.path` (as a launcher would, so the run proves Altrender's natives win), Altrender + LegacyFix, and the
  game's sounds in `game/resources` (b1.7.3 loads that folder when its dead download server fails). LegacyFix's network
  proxy is off in the kit (`-Dlf.proxy.disable`): offline it only logs errors. Checked by running the same kit contents
  with Linux Java (one window, natives from Altrender's folder, `GL11` from LWJGL 3 over the kit's LWJGL 2.9.0, clean
  close).
- **Windows x64 confirmed (owner's Windows 10 LTSC run):** natives load from Altrender's folder; a driver can report
  GL 4.6 while missing a 4.6 entry point (Intel 26.20.100.7985; LWJGL then clears `OpenGL46`). Phase 1: `GlCaps` and the
  settings label use LWJGL's capability flags, and log which flags the version string claims but the driver lacks.

## 2026-11-12 (Phase 0.6, owner)

- **Terrain core: copy selected parts of Celeritas's `:common`** (LGPL-3.0, Sodium ≤0.5.11 → Embeddium → Celeritas;
  `38ccea6c`) into `altrender:common`, adapted to our strategies, with provenance. Not a dependency. Celeritas's
  Ornithe b1.7.3 node is read for its snapshot/meshing approach.
- **GL state interception: inside our own `GL11`.** The build-time merge replaces LWJGL 3's `GL11` state-setting
  methods (which only forward to `GL11C`) with "update `GlStateTracker`, then `GL11C`". Every caller is seen (vanilla,
  other mods); no game class is transformed. The tracker knows `glNewList(GL_COMPILE)` … `glEndList` records instead of
  executing, and treats state after `glCallList(s)` as unknown until read back (vanilla's glyph lists translate without
  popping). Altrender's own code calls `GL11C` where it must bypass the tracker. Rejected: an Angelica-style bytecode
  pass (no public Fabric transformer), per-call-site `@Redirect`s (hundreds, per version).
- **Legacy block IDs: our own numeric-ID → legacy-name table** (public documentation) **+ Angelica's hand-written
  `FlatteningMap`** (LGPL-3.0, `a12d2bb4`, credited) for legacy → modern names and states. Never DataFixer data.
  (2026-11-14, legal checklist: `FlatteningMap` ended up unused; the modern names are Altrender's own table,
  `B173BlockNames`.)
- **Options screen: from Celeritas's Minecraft-free RSO derivative** (`gui/{frame,framework,theme,widgets}`; LGPL-3.0
  with RSO's MIT notice), on our own option model.
- Iris (`bff1e69c`, LGPL-3.0) is AltShaders' source for pack parsing, options, materialmap, transformers and uniform
  definitions; Sodium Extra is a design reference for the FPS counter; legacy-lwjgl3 (LGPL-2.1-only) and Pylon aren't
  used.

## 2026-11-12 (Phase 0.7–0.8)

- **Mc nodes compile against LWJGL 3 + the LWJGL2 layer, not the game's LWJGL2** (excluded from Loom's
  `minecraftLibraries`/`minecraftClientLibraries`, as from the runtime ones): `common`'s sources are compiled into each
  node, and LWJGL 3 and LWJGL2 share class names.
- **Server-only members compile but don't exist at runtime.** The merged b1.7.3 jar has `@Environment(SERVER)` members
  (e.g. `World.removeEntityNow`); Fabric strips them on the client, so calling one is a `NoSuchMethodError`. Check the
  annotation before calling anything not used by client code.
- **Parity references use an original texture pack** (`tools/packs/ar_debug`), never the game's textures: a
  screenshot of Mojang textures is a derived form (0.3). `--pack default` captures stay local.
- **No committed world templates:** scenes are a seed + a fixed anchor column + placed blocks; with b1.7.3's unseeded
  randoms pinned under the harness (spawn search, portal forcer, mob constructors), generation and rendering are
  bit-identical run to run.
- The crash message box of the frameless start is skipped under the harness (automated runs never wait on a dialog).
- **Photon v1.4** pinned for the Phase 7 target (High: one compute program → gl45). AltShaders targets modern Iris
  compatibility in general, Photon is only the performance yardstick (owner).

## 2026-11-12 (Phase 1.1–1.2)

- **Vsync follows LegacyFix's patch.** LegacyFix's `vsync` patch (switched off because it can't patch the layer's
  `Display`) calls `setVSyncEnabled(true)` before `Display.create`. Altrender now does the same when LegacyFix is
  installed and the user hadn't switched that patch off; without LegacyFix the game keeps vanilla's default (off).
  Automated runs keep vsync off. The settings screen (Phase 5) gets a vsync option.
- **Renderer install point:** right after the game's `Display.create` (the retry included); hooks are a no-op until
  then and when the tier is OFF.
- **Tier setting:** `<gameDir>/config/altrender.json`, `"tier": "auto" | "gl33"`; `-Daltrender.tier` overrides it,
  `-Daltrender.renderer=off` (dev) gives OFF without the player notice. Presence flags come from LWJGL's capabilities, so
  a driver that claims a version but lacks one of its functions counts as the lower version (the Windows Intel driver
  in 0.5 reports 4.6 but loads as 4.5: still gl45).
- **OFF notice (b1.7.3):** a red line under the version text on the title screen.
- `Json` moved to `com.coredex.altrender.util` (scenes and config).

## 2026-11-12 (Phase 1.3–1.4)

- **Debug groups and GPU timers wrap the game's own stages until Phase 2** (`ar:frame`, `ar:world`, `ar:terrain0/1`):
  a baseline for Altrender's passes. GPU timers use timestamp queries (they nest) in a 4-frame ring, read only when
  available.
- **The tracker inside LWJGL's `GL11`, how:** LWJGL 3 binds the compatibility-only functions (`glPushMatrix`,
  `glTranslatef`, `glFogf`, `glAlphaFunc`, `glNewList`, `glCallList`, ...) as JNI natives *by name*, so they can't be
  renamed and wrapped. The merge (`LwjglNestTask`, `@Replace` donors) replaces them with Java methods that update
  `GlStateTracker`, then call the driver function through LWJGL's own `JNI.callV/callI/callPV` trampolines with the
  address from `GL.getCapabilities()`. Core functions (LWJGL's `GL11` → `GL11C` forwarders) and wrappers around
  `ngl*` natives are replaced the same way, calling `GL11C`/`ngl*`. 69 methods; `GL13.glActiveTexture` and
  `ARBMultitexture.glActiveTextureARB` too.
- **Display lists:** the tracker notes which state categories a list touches while it is compiled (matrix ops inside a
  balanced push/pop don't count); calling it makes those categories unknown until read back (on demand only). The
  game's terrain lists are balanced; its glyph lists translate. `glPopAttrib` makes what it restores unknown.
- **Altrender's own GL calls bypass the tracker** (`GL11C` etc.), so they must put back what the game had bound: the
  first tracker check caught the harness's capture framebuffer leaving texture 0 bound.
- **GL call counting** (FrameStats): the merge prepends a counter to every original `gl*` method of `GL11`; the 110
  JNI natives Altrender doesn't replace can't be counted, so every `GL11` function the game calls has a (pass-through)
  replacement.
- **Cost, measured:** A/B on the bench scene, alternating, two rounds each: with tracker + KHR_debug 209.0/209.9 FPS,
  without the tracker (`-Paltrender.mergeTracker=false`) 208.3/212.5, without KHR_debug
  (`-Daltrender.debug.glDebug.disable`) 209.2/214.3, neither 214.4/212.5: within noise. (The 0.7 figure of 260.6 FPS
  was measured on a cooler machine; the CPU was at 84–94 °C during these runs.)

## 2026-11-12 (Phase 1.5)

- **Strategies from the owner's earlier GL backend** (owner's own, unpublished): `GlState`, buffer, texture, vertex input, program,
  sync and uniform (UBOs + texel buffers) strategies, gl33 and gl45, wired as its registry does (gl45: DSA
  buffers with persistent maps, immutable DSA textures, `vertex_attrib_binding` with multi-draw and indirect; uniform,
  sync and program shared with gl33). Left out: gl21, shader translation (Altrender writes GLSL 3.30+ itself), push-
  constant plumbing (per-draw data on gl33 = plain uniforms set before each draw; gl45 = buffer indexed by
  `gl_DrawID`, Phase 4), the framebuffer strategy (comes with AltShaders).
- **Pass discipline:** every Altrender pass runs between `Strategies.beginPass` (the state cache forgets everything:
  the game may have changed it) and `endPass`, which puts back what the game had for everything the pass changed:
  bindings and raster state from `GlStateTracker`, framebuffers, viewport and stencil mask from a read just before the
  pass first changed them. Object bindings are restored by "touched" flags, not cached names: GL keeps a deleted
  program current, which the first self-test run caught (every later frame drew with the test's shader).
- **Pixel-store state is tracked** too (`glPixelStorei` donor): the game's screenshot code leaves pack/unpack alignment
  at 1, so Altrender's uploads and reads restore the game's values, not GL's defaults.

## 2026-11-12 (Phase 1.3, apitrace)

- **LWJGL's GL context API follows SDL's video driver.** LWJGL 3.4 picks EGL for its GL function lookups whenever
  `WAYLAND_DISPLAY` is set, even when SDL runs on X11 (XWayland) with a GLX context; it worked only because libglvnd
  dispatches any lookup to the current context, and would break without libglvnd. `SdlWindow` sets
  `Configuration.OPENGL_CONTEXT_API` (X11 → `native`, i.e. GLX; Wayland → `EGL`) after `SDL_Init`, before LWJGL's `GL`
  class loads, unless the user set `org.lwjgl.opengl.contextAPI`. Windows is unchanged (WGL). The 0.4 code that tried
  to install SDL's lookup instead never ran (LWJGL's `GL` class creates its own provider when it loads): removed.
- `-Paltrender.apitrace=glx|egl` also hands the wrapper to LWJGL (`org.lwjgl.opengl.libname` /
  `org.lwjgl.egl.libname`) and SDL (`SDL_OPENGL_LIBRARY` / `SDL_EGL_LIBRARY`); preloading alone traced only the swaps.
  Traces go to the run's game directory.

## 2026-11-12 (Phase 2.1–2.4)

- **Snapshots** carry a 2-block margin (slab/stair/farmland neighbour light) and biome data 31 blocks further in +x/+z
  (tall grass samples its colour at a pseudo-random offset), held as references to a per-chunk cache.
- **Meshing on workers through the game's block renderer**, with per-worker copies of what the game shares: the
  tessellator, block shapes (redirected in `Block`, `PistonBaseBlock`, `BlockRenderer`: found by scanning the game's
  bytecode), the `Vec3d`/`Box` pools, the piston head's forced sprite. The main thread keeps the game's own objects.
  Verified: identical meshes to the game's single-threaded compile in six scenes; 30 minutes in flight, 0 errors.
- **Vertex format: 20 bytes** (u16 position + section index, RGBA colour, float UV); regions of 8 × 4 × 8 sections
  sharing a Celeritas-derived buffer arena.
- **Lighting stays baked for now** (exact parity; sections that read sky light rebuild when the sky darkens, like the
  game's); the shader-light alternative (2.3) is measured next.
- **Chunks are requested by the scheduler** as the game's own compiler requests them (singleplayer loads or generates
  on request), nearest sections first.
- **Draw order:** layer 0 as one multi-draw per region; layer 1 section by section, nearest first, as the game.

## 2026-11-12 (Phase 2.3, 2.6–2.8)

- **(Review) Light stays baked** (2.3): exact parity; the sky-darkening rebuilds it implies measured flat at sunset
  (p99 6.55 ms vs the game's 7.50). Light resolved in the shader needs per-sample light from inside the game's block
  renderer to match smooth lighting: Phase 6 (AltShaders' `lmcoord`).
- **(Review) No quad sorting on b1.7.3** (2.6): the game shows only the nearest translucent surface (Fancy: depth
  pre-pass; Fast: nearest sections first with depth writes), so Altrender keeps the game's order; sorting would change
  Fast's picture. Back with shader packs (Phase 6).
- **Rebuild triggers:** texture-pack reloads and the game's renderer reload rebuild everything; single-block changes
  near the camera rebuild within the frame; sky darkening rebuilds sections that read sky light.

## 2026-11-12 (Phase 3)

- **Weather reads biomes from the column cache** (an extra found by 3.1, not a plan item; owner accepted): the game asked the
  biome noise for 441 columns twice a frame; a b1.7.3 column's biome never changes, so the answer is the same.
  Weather's main-thread share 10.9% → 6.3%.
- **The game's own section culling (`WorldRenderer#cullChunks`) is skipped** while Altrender draws the terrain: it
  tested 2,312 sections a frame that nothing used (8% of the main thread).
- **The immediate-mode stream (3.2) is off by default** (owner: keep it, off by default, an option in the video
  settings, Phase 5). Measured against the game's client arrays it gains
  nothing: Intel 208.8/211.7 vs 209.3/212.0 FPS (two pairs, busy scene), NVIDIA 730 vs 782 FPS (1% low 294 vs 270);
  GL calls per frame are the same (the stream binds and uploads where client arrays point), CPU slightly higher; on
  NVIDIA it lifts the 1% low and p99. `-Daltrender.debug.stream=true` until the option exists.
- **Redundant state changes are skipped** in the state tracker (3.4): 29 of ~4,700 GL calls a frame in the busy scene;
  no measurable FPS change, kept because it's free once the tracker exists (`-Daltrender.debug.stateDedupe.disable`).
- **Sky lists → static buffers** (3.3): the stars and the two sky planes are captured when the game compiles them and
  drawn from our buffers under the game's state; clouds are immediate-mode (no lists) in b1.7.3.
- **Entities and block entities in sections the visibility walk didn't reach aren't drawn** (3.5); block entities are
  frustum-tested as well. Cave scene: identical pixels, 34–44 fewer objects, 1,069 → 1,227 FPS.
- **Frame limit:** the LWJGL2 layer's `Display.sync` sleeps to the deadline (park, then shorter parks for the last
  ~2 ms; no spinning): frame-time spread at the 40 FPS power-saver limit 249 → 19 µs (stdev). Adaptive vsync when SDL
  has it. Minimised: 10 FPS (`idleFps`); unfocused: no cap of ours (`unfocusedFps` 0; the game's own 10 ms sleep stays).
- **Texture reloads rebuild the terrain** (anaglyph toggle, texture-pack switch; owner accepted): the game keeps the old
  section lists until something else rebuilds them, so right after the switch it shows stale colours; Altrender shows
  the new ones. With a forced rebuild on both sides the shots match (anaglyph 45.19 dB, pack 49.51 dB).
- **Allocation (3.7):** no boxing in per-frame lookups (`LongMap` for sections, regions, captured lists, the biome
  cache, whose `Long` keys also collided along diagonals), one meshing scratch buffer per thread (vertex data was already
  off-heap). Busy scene: Altrender's own main-thread allocation 17.3 → 1.4 MB/s, mesh threads 32.6 → 5.6 MB/s.
- **Found by 3.1 (Phase 2 bug, fixed):** block entities (signs, chests, …) were never drawn with Altrender's terrain:
  the game draws the ones its section lists collect; Altrender now maintains that list per section.

## 2026-11-12 (Phase 4)

- **One buffer arena for all terrain** (was one per region): a pass can then be one draw. Regions remain the unit of
  positioning (the vertex format stores positions relative to an 8 × 4 × 8-section region).
- **One stream buffer per tier** (`StreamBuffer`): per-pass uniform blocks, per-draw data, indirect commands, the
  immediate-mode stream and (gl45) upload staging. gl45: 64 MB, immutable, mapped once (persistent, coherent), a
  fence per frame; gl33: `glBufferSubData`, a new buffer when it wraps. **Not client storage:** NVIDIA then keeps the
  ring in system memory, and indirect commands and per-draw data read from there cut its FPS from 968 to 175.
- **The arena isn't mapped (deviation from 4.2's wording):** persistently mapped storage lives in system memory on
  NVIDIA, where vertex fetch would cross PCIe every frame; vertex data is copied into the arena on the GPU from the
  mapped ring instead (`glCopyNamedBufferSubData`). Nothing is mapped or unmapped per frame either way.
- **Per-draw data through `baseInstance`, not `gl_DrawID` (deviation from 4.3's wording):** each indirect command's base
  instance selects a per-draw record (region offsets) read as instanced attributes; without indirect draws (gl33, or
  gl45 with them switched off) the same attributes are set as constants per region. One shader for every path and
  every value computed the same way: the picture is bit-identical across paths (checked), and
  `ARB_shader_draw_parameters` isn't needed. Locations 6 and 7: the only ones below 8 no driver aliases to
  fixed-function state (NVIDIA aliases 3 to the colour).
- **GPU-written commands (4.3)** where the driver has indirect count: the visibility walk's result (frustum included)
  selects table slots, a compute shader writes layer 0's commands and draw count. Another camera passes its own
  selection (shadow passes, Phase 6/7). In slot order (one workgroup, a prefix sum): with an atomic append the order
  changed frame to frame, and so did 2 pixels in the snow scene (equal-depth fragments where two sections meet). Translucent terrain stays CPU-ordered (nearest first), as one indirect
  multi-draw: its commands run in order. No fade-in (it would change pixels; an option later if wanted).
- **One uniform block per pass** (`TerrainFrame`: matrices, fog) on both tiers; no `glUniform*` per draw.
- **Fixed (gl33, since Phase 1's strategy port):** the gl33 vertex-array strategy bound `GL_ARRAY_BUFFER` past the state
  cache, so it stayed bound after the terrain pass and the game's next client-array draws read from the terrain
  buffer: wrong sky colours, and a crash when a sign's model compiled. Found by comparing gl33 with gl45 (4.3).
- **A run-time tier switch recomputes the tier's features** (they were copied from the old tier).
- **API 0.2.0: `ShaderPrograms`** (4.5): non-blocking program builds on Altrender's context, compiled on driver
  threads where `KHR_parallel_shader_compile` (or ARB) exists. `AltrenderApi.shaderPrograms()`; Altrender installs it
  through `AltrenderApi.Host`.
- **GPU-written commands are off by default** (owner accepted; `-Daltrender.terrain.gpuCommands=true`): 0–4% slower than
  CPU-written ones on both GPUs (a dispatch and barrier per frame for ~300 sections). Kept for cameras the visibility
  walk can't serve (shadow passes, Phase 6/7).
- **No Intel-Mesa indirect-draw rule:** the earlier backend's finding didn't reproduce here (343.5 vs 345.1 / 342.0 FPS).
- **Programs are labelled when their build finishes:** labelling one while it links made NVIDIA finish the build at
  once (parallel compilation gained nothing until then).

## 2026-11-12 (Phase 5.1)

- **The UI draws its own text** from the game's font texture and glyph widths (each version's `UiFont`): one batch
  with the panels, clipped and transformed like them, no switch back to the game's state per string (F3, 5.3, draws
  dozens a frame). Colour codes and shadows as the game draws them.
- **Celeritas's search-field model isn't taken:** it follows Minecraft's own text field closely (0.3 rule 1); our
  `TextField` is written from scratch. The framework interfaces, `Dim2i` and the flat button are ported (LGPL, RSO's
  MIT notice shipped in the jar).
- **Widgets claim focus themselves when clicked** (a text field); hit-testing focus in the screen missed widgets in
  scrolled panes (found by the gallery's input self-test).
- **The UI pass switches the game's alpha test off** (in a compatibility context it also applies to shader output and
  would drop faint panels) and back on after.

## 2026-11-12 (Phase 5.1–5.2, Sodium look)

- **Owner: the Sodium look.** The first design (rounded, pill switches, blue accent) was "too AI-like"; the toolkit
  now draws Sodium's screen as RSO and Celeritas do (flat translucent rows, square tick boxes, line sliders, cyan for
  selected), with Celeritas's controls, option page, tab frame and screen controller ported (credited).
- **The option model is ours, in the public API** (`altrender:api` `options`), Sodium ≤0.5.11's model as the
  reference: pending values until Apply, flags for what applying needs, one save per storage, pages from providers.
- **Search matches option names and value names** (every word); Celeritas's fuzzy search isn't taken.
- **Settings are live:** cave/entity culling, the upload budget and the stream are read each frame; mesh threads and
  GPU-written commands rebuild the terrain on Apply; the renderer switch uses the run-time tier switch.
- **Render distance 2–32 chunks** (owner): Altrender's terrain radius, fog end and far plane; the game's own four
  steps are exactly the game's (Tiny 2, Short 4, Normal 8, Far 13 chunks) and its step follows the setting (its sky
  and clear colour use it). Singleplayer's chunk cache has no size limit; in multiplayer the server's distance
  limits what arrives.
- **Max Framerate is Sodium's cap** (owner): 10–260 FPS or Unlimited on Altrender's precise pacer; the game's own
  Max/Balanced/Power saver is retired (Balanced only budgeted chunk building; Power saver becomes 40 FPS once).

## 2026-11-12 (Phase 5.3–5.4)

- **Modern F3 Screen setting, on by default** (owner); off, the game's own F3 exactly. F3 still toggles on press
  (the game's), so F3+S and the rest are unchanged; F3+1/2 and F3+F6 are added; number keys with F3 held don't pick
  hotbar slots.
- **The debug screen's text is made ten times a second** (the chart's bars every frame): with direct vertex writes
  and a glyph table, the whole screen with both charts costs 0.18 ms a frame (from 0.46).
- **No modern block names in F3 yet** (owner: "no need to 1:1 everything"): the targeted block shows id:meta and the
  game's name; `BlockIdMapper` comes with Phase 6, which needs it.
- **FPS counter**: Sodium Extra's design (modes, corner, contrast), our code; the GPU time it shows comes from the
  same per-pass timers, created only while something shows them.

## 2026-11-13 (Phase 6.2, pack loading)

- **Owner: `MC_VERSION` is `10710`, and `IS_IRIS` is defined.** `IRIS_VERSION` is the pinned Iris's (1.11.4 → `11104`).
- **Iris's pack code is ported** (LGPL, credited, 97 files under `altshaders/common/…/pack/`): options, profiles,
  screens, `shaders.properties`, includes, id maps, program sets. Java's own collections replace Guava and fastutil,
  small value types replace JOML. **AltShaders makes no GL calls:** formats and blend modes are data here; the
  pipeline will apply them through `altrender-api` (6.1/6.4).
- **Our own C preprocessor replaces JCPP** (no new library and no new licence). It matches GNU cpp on 46 tricky cases
  (macros, `#`/`##`, rescans, `#if` arithmetic), and all five test packs' preprocessed sources compile in glslang
  except as listed below. Like Iris with JCPP: the properties line handling, `#pragma`/`#line`/`#warning` dropped,
  and `#error` or a bad `#if` only logged (Iris loads such packs).
- **No GLSL transformer, but `#version` gets raised (for 6.4):** Iris raises every non-compute shader to at least
  `#version 330`. BSL and MakeUp use 1.30 features under `#version 120` (`flat varying`, `int` attributes,
  `gl_VertexID`) and depend on that. Raising to `#version 330 compatibility` keeps the `gl_*` built-ins and fixes
  all of those in glslang. Inputs Iris adds at compile time, which the pipeline must provide: `mc_chunkFade`
  (FADE_VARIABLE: BSL, Complementary) and the core-mode line inputs (`projectionMatrix`, `modelViewMatrix`,
  `vaPosition`, `vaNormal`: Complementary's `gbuffers_line`). Distant Horizons programs (`dh_*`) aren't loaded.
- **Feature flags:** AltShaders claims every Iris flag; the hardware ones come from the renderer's capabilities
  (6.1). For gl33: no compute, SSBOs, images or tessellation; per-buffer blending when `ARB_draw_buffers_blend` is
  present. Photon and Complementary use per-buffer blend directives without requiring the flag, and Iris then
  refuses the pack, so without that extension we refuse it too. A pack whose required features are missing is
  refused with their names (`UnsupportedPackException`), for the pack page (6.3) to show.
- **32 colour buffers** (`colortex0–31`, `MAX_COLOR_BUFFERS`), as current Iris; development.md 6.4 lists 0–15.
- **Biomes:** every modern biome name is defined (`BIOME_*`), so packs that compare against them compile. b1.7.3's
  biomes report the closest one (`Biomes.of`: rainforest → jungle, seasonal forest → flower forest, shrubland →
  sparse jungle, ice desert and tundra → snowy plains, hell → nether wastes, sky → the end) (Review).
- **`<pack>.txt`** is OptiFine's format as Iris writes it, sorted and without the date line so the file stays the
  same when nothing changes (Review).

## 2026-11-13 (Phase 6.1 API, 6.4 pipeline skeleton)

- **API 0.3.0** (docs/api.md): capabilities, a version check (`supports`), multi-stage program builds, one
  `WorldPipeline` seat, a GPU layer (`Gpu`: textures, framebuffers, buffers, state, full-screen passes, compute),
  the camera, the game state (dimension so far) and the fixed-function state. **AltShaders makes no GL calls:**
  everything goes through Altrender's state cache, which gives the game its state back after each pass.
- **The game's own draws stay fixed-function, under the pack's programs:** in the compatibility context a pack's
  `gl_Vertex`, `gl_Color`, `ftransform()` read the game's draws directly. The pipeline says where they go and with
  which program (`gameDraws`), and Altrender restores that (not the screen and program 0) after its own passes.
- **Programs follow the fixed-function state, as OptiFine's did:** the tracker sees the game's texturing, fog and
  lighting switches; in the sky and between phases the program is chosen from them (sky basic/textured; basic,
  textured, textured_lit), and the fog uniforms (`fogMode` etc.) follow every fog change. Cracks and the block
  outline get their own phases (DESTROY, OUTLINE).
- **AltShaders' own uniforms:** `fogDistanceMode` (radial or eye-plane fog, as the game's on this GPU),
  `fixedTexture`, `fixedLighting` (Review: extras beyond Iris's set; `ar_minimal` needs them to match vanilla).
- **"The screen" is the game's own framebuffer** when the pipeline takes the game's draws (the scene harness renders
  into its own).
- **Render targets:** made on first use in the pack's formats; two sides each for ping-pong; depth is 24-bit as the
  game's framebuffer; colortex0 gets the game's own clear (the fog colour), the rest Iris's default clears.
- **Known gaps for 6.6–6.8:** Altrender's terrain still draws with its own program (into the pack's buffers);
  b1.7.3 clears depth before the hand, so `depthtex0` holds only the hand by the composites (6.8: no clear, hand
  depth as Iris); alpha-test and blend overrides, mipmapped buffers, custom textures, `begin`/`prepare` passes and
  the shadow pass aren't in yet.
- **Packs' terrain (6.6):** Altrender meshes an extended vertex (48 bytes: light as modern lightmap coordinates out
  of the colour, normal, `mc_Entity`, `mc_midTexCoord`, `at_tangent`, `at_midBlock`); a pack's `gbuffers_terrain`/
  `gbuffers_water` vertex shader reads it after a token rename of the built-ins (`gl_Vertex` …) and a prologue from
  the API. Smooth light: the average over the open cells in front of each face corner. A pack without its own
  terrain program keeps Altrender's terrain (the game's look) in its buffers. Block ids: the version's blocks by
  modern name and state (b1.7.3's table, ours), matched to block.properties in file order; no block tags yet (so
  `IRIS_TAG_SUPPORT` isn't defined). The `lightmap` sampler is b1.7.3's light curve with the sky's darkness.
- **Iris's hand depth:** with a pack, the game's depth clear before the hand is skipped and the hand's clip-space
  depth is scaled by 0.125 (depths 0.4375–0.5625, as packs expect), so composites see the world's depth.

## 2026-11-13 (Phase 6.6/6.8, the five packs drawing right)

- **Terrain takes Iris's fallback chain** (`gbuffers_terrain` → `textured_lit` → `textured` → `basic`, as
  development.md 6.4 lists): Sildur's has no `gbuffers_terrain` and fills its gbuffers from `gbuffers_textured`.
  This replaces "a pack without its own terrain program keeps Altrender's terrain" above. **(Review)** A pack can ask
  for Altrender's own terrain in its buffers with `altshaders.vanillaTerrain=true` in shaders.properties; `ar_minimal`
  does, so it stays a pixel test of the rest of the pipeline (b1.7.3 bakes light into the colour; the pack terrain
  path's light comes from light coordinates, which can't reproduce it exactly).
- **block/item/entity.properties are read with `MC_VERSION` 12111 (Review),** GLSL keeps the owner's 10710. Their
  entries are matched to b1.7.3's blocks by modern names (1.20.3+, `short_grass`), so the files' modern branch is the
  one that fits; Photon's pre-1.13 branch is empty (no water, no leaves), other packs' list numeric ids or old names.
- **Passes see the terrain's fog,** as Iris (its "environmental" fog): composite, deferred and compute programs get
  `fogMode` 0, `fogShape` -1 and the start, end, density and colour the terrain was drawn with (Sildur's fades land
  into the sky with them; 0 fogged everything).
- **Buffers that aren't cleared keep their latest side across frames:** after `final`, a buffer left on its second
  side is copied to the first, where the next frame reads (Iris's swap passes).
- **b1.7.3's fancy water is two draws** (depth only inside the layer's `render`, colour in `renderLastChunks`): the
  colour draw now runs inside the TERRAIN_TRANSLUCENT phase too. Before, it went to whatever program's buffers were
  bound (BSL's reflections read the wrong textures and blew out).
- **Debug view:** `-Daltshaders.debug.view` takes up to four names (comma-separated, a 2×2 grid) and `shadowtexN`,
  `shadowcolorN` as well as `colortexN`, `depthtexN`.
- **Entities under a pack draw white** and the pipeline gets each entity's (and block entity's) modern type or block
  and the light at it (API `WorldPipeline.entity`/`blockEntity`): Iris packs light entities from light coordinates,
  b1.7.3's brightness in the colour would light them twice. AltShaders sets `entityId`/`blockEntityId` and the light
  coordinates per draw. Entity names: b1.7.3's keys and the unregistered ones (player, lightning, …) under modern
  names (`B173EntityNames`); items 256–359 and the two records by modern name (`BlockNames.itemName`), so
  `heldItemId` comes from item.properties and `heldBlockLightValue` is the held block's light.
- **`ftransform()` in a pack's terrain vertex shader** transforms the decoded terrain position (it would read the
  unbound built-in `gl_Vertex`).
- **The shader pack page (6.3)** is AltShaders' own settings pages on Altrender's options API: the pack, its state
  (the last load or build error, with pack file and line, as the tooltip), reload, the packs folder and "Add packs",
  then one page per screen of the pack's menu (names from its `lang/en_us`), profiles as a cycling option. Applying
  queues the changes, which the reload writes to `<pack>.txt`. R in the world reloads (as Iris). API additions for it:
  `Control.Action` (a button row) and `Desktop` (SDL3's `SDL_OpenURL` for the folder, `SDL_ShowOpenFileDialog` for
  zips; answers run on the render thread). AltShaders' strings are plain English for now (Review: it has no lang
  file of its own).
- **Uniforms (6.5):** `-Daltshaders.debug.uniformDump[=N]` logs the standard set every N frames (100); uniforms b1.7.3
  can't have keep neutral values (`StandardUniforms.UNAVAILABLE`) and a pack that reads one is told once.
- **F3's GPU breakdown** has "shadow" and "pack" rows (API `TimedPass`): the pipeline's shadow pass and its
  full-screen and compute passes.
- **No fixed-function alpha test or blend under a pack's programs** (as Iris): in the compatibility profile GL's alpha
  test still cuts a program's first output (Photon packs data there; underground its alpha is ~0 and caves drew
  nothing). During a pack frame the game's alpha test is kept from the driver (`GlStateTracker.suppressAlphaTest`,
  API `Gpu.gameAlphaTest`); programs writing `gl_FragData[0]`/`gl_FragColor` get Iris's injected test with the
  pack's `alphaTest.<program>` or the game's own test for the draw (`altshaders_alphaTestRef`, `alphaTestRef`);
  Altrender's own terrain lifts the suppression for its cut-outs. A program's `blend.<program>` (or Iris's default)
  replaces the game's blending while it draws (`GlStateTracker.overrideBlend`, API `Gpu.gameBlend`; `Blend.NONE` =
  off); the game's blend calls are still tracked. Not yet: per-buffer `blend.<program>.<buffer>`.
- **Exponential fog as a range:** under water and in lava b1.7.3's fog is `GL_EXP` (density 0.1, 2.0); packs get
  `fogStart` 0 and `fogEnd` where it reaches 99 % (ln 100 / density), since Iris's are always linear.
- **Entity light, refined:** `WorldPipeline.entity`/`blockEntity` return whether the pipeline took the light (no pack
  program bound, or no pack: the game's brightness stays in the colour). The spider's eyes, blob shadows and the fire
  overlay get full light (they carry their own brightness in the game).
- **Dev switches:** `-Daltrender.debug.objectCounts=N` logs GL object counts every N frames;
  `-Daltshaders.debug.reloadTest=N` reloads the pack N times, every 60 frames (the 6.3 leak check).
- **The solid hand draws before the translucent terrain under a pack** (Iris: before the deferred passes, which light
  it; Photon and other deferred packs drew no hand at the end of the frame). API `WorldPipeline.handFirst`; the
  b1.7.3 node draws what `renderItemInHand` draws for the hand (identity modelview, hurt shake, bobbing) at the
  start of the translucent terrain, with the state the hand has at the end of the world, and skips the late hand
  (the screen overlays stay). The hand takes the player's light like an entity.
- **Harness hand check:** scenes with `"hand": true` show the hand (and HUD); after shots a and b the harness hides
  it and takes shot c, and passes the hand when its corner changes from a to c at least 3 × as much as from a to b
  (the pack's own noise). capture-set prints the result. Scenes `hand` and `hand_night`.
- **The shader pack screen is Iris's** (owner, 2026-11-13: "should be like Iris"), replacing the settings pages: the
  pack list ("Shaders: ON/OFF", the packs with the chosen one outlined, "+ Add Shader Packs..."), the pack's own
  screens (header with Back, name and Reset with Shift; "Name: Value" cells in the pack's columns: click next,
  right-click previous, Shift+click default; sliders for the pack's `sliders`; links "Screen >"; the profile), and
  "Open Shader Pack Folder", "Shader Pack Settings..."/"Shader Pack List", Cancel, Apply, Done. Opened from a
  "Shader Packs..." button in Altrender's video settings and the O key in the world (Iris's key). Drawn by Altrender
  (`ui/shaderpacks/ShaderPackScreen`) from AltShaders' model (API `ShaderPackMenu`, `registerShaderPackMenu`);
  nothing applies before Apply/Done. Strings are plain English (Review: no lang files yet).
- **Layer 0 takes the pack's cut-out program** (Iris's `terrain_cutout` → `terrain` chain; shadow: `shadow_cutout`):
  b1.7.3 draws solid and cut-out blocks in one pass, and a pack's solid-only program (Photon's
  `gbuffers_terrain_solid`) drew transparent texels black (the grass side's overlay over the dirt, above tall
  grass, leaves). Its alpha reference is the game's terrain cut-off (0.1). A separate cut-out pass (cheaper for the
  solid blocks' early depth test) is a Phase 7 question.
- **The player in the shadow map** (Iris's `shadowPlayer`): the API's entity view takes `entities` and `player`
  flags; the b1.7.3 node shows the first-person player to that view and, with `shadowEntities=false`, only the
  player (Photon). Scene `player_shadow` (noon, looking down) shows it; `-Daltshaders.debug.noShadowPlayer` turns it
  off for comparisons.
- **The shader pack screen follows reloads:** the menu has a revision that changes when the pack reloads; the screen
  shows itself anew then, and the values just applied until the reload lands (the profile showed the old one).
  "Shader Packs..." is also at the bottom of the video settings' side bar.

## 2026-11-13 (owner)

- **Owner approved every (Review) item above** ("I approve the Review stuff"): MC_VERSION 12111 for the id files,
  `altshaders.vanillaTerrain`, plain-English shader pack screen strings, the biome mapping, sorted `<pack>.txt`,
  the AltShaders uniforms `fixedTexture`/`fixedLighting`/`fogDistanceMode`, the "less than" alpha tests read as off.

## 2026-11-13 (the known gaps, owner asked)

- **Per-buffer blend** (`blend.<program>.<buffer>`, Iris): the buffer's place in the program's draw buffers gets the
  blend (buffers it doesn't write are ignored). API `Gpu.gameBufferBlends`; Altrender's GL tracker sets them per
  attachment (`glEnablei`/`glBlendFuncSeparatei`) after the program-wide blend and again whenever the game's own
  blend call reaches the driver (a global call resets every buffer). Functions per buffer need GL 4.0 or
  `ARB_draw_buffers_blend`; on/off works on every tier. Not yet in the shadow pass.
- **shadowcomp fragment passes** (Iris's shadow composite): each `shadowcolor` gets a second side when a pass first
  writes it; a pass reads one side and writes the other at the shadow map's resolution, then the written ones flip
  (the pack's `flip.*` applies, as in composite passes). The shadow pass resets the sides each frame.
- **centerDepthSmooth** as Iris gives it: `depthtex0` at the centre, smoothed with Iris's decay
  (`ln 2 / (halfLife × 0.1)` per second) in a 1×1 R32F texture on the GPU, before the composite passes, only when a
  program reads it. `uniform float centerDepthSmooth` in a pack's source becomes a read of that texture (not where
  the source also declares its own variable of the name). Portions from Iris's `CenterDepthSampler` (LGPL-3.0,
  credited in THIRD_PARTY.md and provenance.md).
- **Block tags** (`IRIS_TAG_SUPPORT` 2): API `BlockNames.tags`; the b1.7.3 node lists the modern tags its blocks are
  in (`B173BlockTags`: an original list of facts, as the name table; only tags with b1.7.3 members, no tool, spawning
  or sound tags). As Iris, name entries win and tags fill in, in the file's order. Owner asked for a check against
  Mojang's data: compared locally with 1.21.11, 26.1.2 and 26.3's block tags (read from the jars, nothing copied):
  every tag matches. 26.1 moved `grass_block` from `#dirt` to `#grass_blocks`; packs written for 1.21 (Photon 1.4)
  reach grass blocks through `%dirt` only, so a block is in a tag if it is there in 1.21.11 or 26.x (`grass_block` in
  both; `#ores` is 26.3's). Added `ores`, `overworld_natural_logs`, `replaceable`, `replaceable_by_trees`,
  `grass_blocks`.
- **entityColor** (API `WorldPipeline.entityColor`): the hurt red (b1.7.3's 0.4; spiders twice, since the game's red
  pass also draws their eye layer's body-shaped model) and the renderer's flash (a creeper's white) as one colour;
  taken, the game's overlay pass isn't drawn. ar_minimal applies it before lighting, as the game's second pass
  looked. Harness entity `state`: `hurt`, `swell`; scene `entity_flash`.
- **Spider eyes program** (API `WorldPipeline.entityEyes`): `gbuffers_spidereyes` (its fallbacks; Iris's default
  additive blend) while the eye layer draws; the next entity ends it in any case (the game's red pass starts the eye
  layer without drawing it).
- **Particle order** (API `WorldPipeline.particlesLate`): Iris's rule (the pack's `particles.ordering`, else `after`
  with deferred passes and no separate entity draws, else `mixed`). b1.7.3's particles are all opaque, so only
  `after` moves them: after the translucent terrain, before the weather, with the lighting and fog the game gives
  them. Harness `particles` (spawned once, frozen); scene `particles` (its particles' sizes and colours are the game's
  `Math.random`: compare shots within a run only).
- **Particles get a normal under a pipeline** (Review): b1.7.3's particles have none and take GL's leftover one,
  which drew them black under Photon (it lights from `gl_Normal`); (0, 0, 1) at the particle stage, only with a world
  pipeline installed.
- **The in-wall, water and fire screen overlays get full light** (240, 240, as Iris gives draws without light
  coordinates): ar_minimal's hand program darkened the nether's in-wall overlay with the lightmap at (0, 240).
- **A phase without any pack program** leaves no program of the pack current: fixed-function changes no longer set
  uniforms with no program bound (driver errors with packs lacking `gbuffers_basic`).
- **Blend in the shadow pass** (owner asked): the shadow programs' `blend.*` (Iris's default for them: off), whole and
  per buffer, for the terrain (API: `TerrainView.draw` takes them) and the entities (the game's draws, as
  `gameBlend`/`gameBufferBlends`). The entities had drawn into the shadow map with whatever blend the game had left
  on; a Sildur's run had its whole ground in shadow from it, gone with this.
- **Vanilla blob shadows are hidden under packs with a shadow map** (API `VanillaPart.ENTITY_SHADOWS`; Iris and
  OptiFine do the same): at full light (the entity overlay rule above) Photon lit them as emissive, glowing patches
  under every mob. Packs without shadows (ar_minimal) keep them.
- **Terrain normals follow the quad's winding** (owner: "snow layers look too weirdly dark in Photon"): the extended
  terrain's normal (and the side the mesher reads a face's light from) was the quad's plane facing away from its
  block's centre, which pointed thin blocks' faces the wrong way (a snow layer's top sits below the centre: its
  normal pointed down, so packs lit it as an underside; also ladders, door panels, rails, plates, redstone, and half
  the crossed sprites). The game winds every face to face out (it culls back faces), so the winding is the side.
  Scene `snow_layers`. Full blocks are unchanged (normals compared before and after: identical).
- **Textures over 512×512 load** (owner's crash loading a texture pack): b1.7.3 copies every texture through one
  1 MB buffer (`BufferOverflowException` from `TextureManager.load`); the buffer grows to fit. HD packs then load,
  but b1.7.3's animated textures (water, lava, fire, portal) stay 16×16 tiles, as without MCPatcher.

## 2026-11-14 (Phase 7: Altrender under shader packs)

- **The plan** (owner approved before any code): baseline first; then the fancy-water depth pass with a cheap
  program, Iris's shadow culling, the shadow pass on the GPU-driven path, the solid/cut-out split of the opaque layer,
  the per-entity light handoff; a smaller pack vertex only if the passes turn out vertex-bound. AltShaders' own
  optimization pass is development.md 7.4, after Altrender's.
- **Bench additions:** scene `bench_target` (the Phase 7 setup: the orbit at 1920×1080, render distance 16 through
  the new scene option `altrender.renderDistance`); the render thread's CPU time per frame in the meta. Frame time
  minus the buffer swap (also recorded) isn't the CPU's share on NVIDIA via PRIME: its driver waits elsewhere.
- **Fancy water's depth draw takes a depth-only twin of the pack's water program** (API
  `TerrainPipeline.Pass.TRANSLUCENT_DEPTH`): b1.7.3 draws the translucent layer twice (depth with colour writes off,
  then colour), and both ran the pack's full water shading. The twin keeps the pack's vertex shader (moving water
  keeps its depth) and only tests alpha (0.1, as the packs' own water discards); packs whose water program has
  geometry or tessellation stages keep the full program. The colour draw reuses the depth draw's sorted sections.
- **Iris's shadow culling** (API `ViewCulling` on `TerrainView.draw`): `shadow.culling` as Iris reads it —
  `false` (or the default for a pack whose shadow programs voxelize) culls by distance only; otherwise only sections
  whose shadow can fall into the player's view are drawn (the view frustum swept towards the light: its planes the
  sweep doesn't cross plus planes through the silhouette edges along the light; the technique of Iris's advanced
  shadow culling, implemented anew in `terrain/ShadowCuller`); `reversed` keeps everything within the shadow and
  voxel distances (more than Iris's safe zone may keep: conservative). The player's view is the camera's matrices
  at the shadow pass.
- **The shadow pass's opaque terrain goes through the GPU-driven path** (`GpuCuller`: the CPU sets a bit per
  section, one indirect draw), and the view keeps its per-region lists between frames instead of making them.
- **The lightmap texture matrix for the game's draws is set only when it changes** (on/off), not per entity, and
  no longer resets the whole GL state cache each time.
- **The opaque layer is split into solid and cut-out quads under a pack** (API `TerrainPipeline.Pass.CUTOUT`): a
  quad is solid if every texel of its atlas sprite passes the 0.1 alpha test (read from the texture pack's
  terrain.png); the mesher puts a section's solid quads first (each part keeps its order: a grass side's overlay
  still follows the side). Solid quads draw with the pack's solid program (Iris's `terrain_solid` chain) without
  AltShaders' alpha test, so the early depth test stays on; cut-out quads with the cut-out program as before. Both
  programs share their texture units by name (the solid one takes the cut-out one's units, extra ones after). When
  the two programs draw into different buffers, the cut-out program draws the whole layer. The shadow pass does the
  same with `shadow_solid` / `shadow_cutout`. Debug switches for comparisons: `-Daltshaders.debug.noWaterDepth`,
  `-Daltrender.debug.shadowCulling.disable`, `-Daltrender.debug.cutoutSplit.disable`,
  `-Daltshaders.debug.noSolidSplit`.
- **Terrain colours for packs as Iris gives them** (owner: "photon's shadows are too dark"): the packs say
  `oldLighting=false` (Iris's default), under which Iris leaves the game's per-face shading out of the vertex colour;
  we kept b1.7.3's (sides 0.8 and 0.6, bottom 0.5), which packs shaded again. Now `TerrainPipeline.faceShading`
  (the pack's `oldLighting`) drops those multipliers while meshing for a pack (b1.7.3's block, fluid, cactus, door
  and bed paths). Ambient occlusion, which b1.7.3 only has inside its brightness (1 when meshing for a pack), is
  added as modern smooth lighting does (each solid cell of the four in front of a corner: -0.2), in the colour's
  alpha for `separateAo` packs (`TerrainPipeline.separateAo`) or in its colour; opaque layer, smooth lighting on.
  Photon's shaded sides 12–18 % brighter; lit tops unchanged.
- **The player in the shadow map with entity shadows on** (owner: player shadows only in Photon): Iris's shadow pass
  draws every entity, the first-person player included; `shadowPlayer` adds the player when entity shadows are
  off. We only honoured `shadowPlayer`.
- **Shadow programs get their own textures and images** (owner: torches don't work in Refrax): the shadow pass never
  bound a shadow program's samplers or images (only the gbuffers programs' and the passes'), so packs that voxelize
  in their shadow programs (Refrax's colored light writes `voxelImg` there) wrote nowhere. Now bound as for the
  gbuffers programs.

## 2026-11-14 (Iris comparison pass, 7.4)

- **AltShaders follows Iris's behaviour, not its implementation** (owner: "do NOT override any of our
  optimizations, only behaviour"): each difference found against Iris (bff1e69c) was fixed only where a pack sees a
  different result; Altrender's and AltShaders' optimizations stay (GPU-driven and culled shadow terrain, the water
  depth twin, the solid/cut-out split, one memory barrier per compute, …).
- **Fixed to match Iris:** a buffer the pack keeps (`colortexNClear=false`, `shadowcolorNClear=false`) is cleared
  once after it's made or resized; `colortex0Clear=false` keeps colortex0 (the game's clear then reaches the depth
  only); colortex0's fog-colour clear has alpha 1 (b1.7.3's has 0; Iris notes Sildur's needs 1); shadowcolors are
  cleared on both sides, at the frame's start before the begin passes; composite-type passes (begin, prepare,
  deferred, composite, shadowcomp) take `blend.<pass>`, the final pass never blends; the stages' pre-flips
  (`flip.<stage>_pre.*`); explicit flips toggle as Iris's (a written buffer with `=true` flips twice); the setup
  programs run again after a resize; `centerDepthSmooth` is sampled from the depth before the hand; mipmap filtering
  stays on a buffer side for the rest of the frame once a pass asked for it (Iris's, as OptiFine's), and the final
  pass's `colortexNMipmapEnabled` work; shadow mipmaps (`shadowtexNMipmap`); the shadow maps' filtering as Iris's
  samplers (linear unless `shadowtexNNearest`; shadowcolors always linear, integer formats nearest); colour buffers as
  images (`colorimgN`, `shadowcolorimgN`, the side read now: Photon writes its sky's light into colortex4 so); entities
  drawn into the shadow map and the translucent terrain drawn even with `shadowTerrain=false`; uniforms `pi`,
  `constantMood`, `anisotropicFiltering`. API: `Gpu.clearAlpha`.
- **Kept different (Review):** a `sampler2DShadow` compares and a `sampler2D` doesn't, whatever
  `shadowHardwareFiltering` (Iris sets the compare mode on the texture: a plain sampler then reads undefined values);
  shadowcolors with no shadow pass read white (Iris: their clear colour). Not provided: `blendFunc`, `gtextureSize`,
  `gtextureId`, `textureReloadCount` (none of the tested packs reads them; they need the game's blend and bound
  texture through the API); the shadow depth's RRR1 swizzle for old packs.
- **Fancy water keeps its depth draw under packs** (owner: "do it if it helps"): skipping it (API
  `TerrainPipeline.translucentDepthDraw`, `-Daltshaders.debug.skipWaterDepthDraw`) left the translucent terrain's
  GPU time the same (2.91 ms both ways), the saved draw coming back as more water shading; not worth the change in
  the picture.
- **Shadow back-face culling is an option, off by default** (owner: "make it optional"; Iris draws both sides):
  AltShaders' settings page, `shadowBackFaceCulling` in `config/altshaders.properties` (API `ViewCulling.backFaces`).
  Only the solid quads: culling the cut-outs too let light through leaves' clear texels (speckled tree shadows);
  for closed opaque shapes the back is never the nearest to the light, so the shadow map stays the same.
- **7.4:** uniforms sent only on change (per program and location), shadowcolor clear framebuffers made once, mipmaps
  made only for sides written since, image bindings cached. Not done: the side trade instead of the copy after
  `final` (not the same picture where a pass leaves pixels unwritten) and per-frame uniform buffers (packs declare
  plain uniforms).

## 2026-11-14 (release workflow)

- **Releases from a tag** (owner: "a workflow that builds and publishes the jars and attaches the required sources"):
  `.github/workflows/release.yml` on `v*` tags (or by hand for an existing tag). The tag must be `v<mod_version>`.
  One GitHub Release per version for both mods and every Minecraft version, carrying the jars, their `-sources` jars
  (our LGPL code), OpenAL Soft's source archive (LGPL; checksum-pinned task), `THIRD_PARTY.md` and `SHA256SUMS.txt`
  (Review: the checksums file). Actions as in the owner's other repos (`checkout`, `setup-java` Temurin 21,
  `gradle/actions/setup-gradle`, all `@v4`); the release itself through the runner's `gh`, no third-party release
  action. Not a Modrinth/CurseForge upload (needs the owner's project ids and tokens).
- **`.gitignore` let `build-logic`'s plugin sources through:** `build/` also matched the source package
  `com/coredex/altrender/build/`, so a plain `git add` skipped `build-logic`'s four classes and a clean checkout
  couldn't build; `!**/src/**/build/` re-includes source directories of that name.

## 2026-11-14 (unpublished GL backend not named)

- **The owner's earlier GL backend isn't named anywhere** (owner: not released yet). Its 33 files (the GL strategies,
  `debug/GlObjectCounts`) lost their derived-from headers and their 35 provenance rows (with `tools/imgdiff` and the
  `GlState` additions row); the README and THIRD_PARTY.md credits and the MIT notice in Altrender's jar
  (`META-INF/licenses/altrender/`) are gone; comments say "Altrender" or nothing. Fine legally: the owner holds the
  copyright to both, so the code needs no notice in Altrender. Credits can come back once the backend is published.
