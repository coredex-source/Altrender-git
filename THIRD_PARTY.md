# Third-party software

## Shipped inside the mod jars

Altrender's jar contains:

| Project | Licence | What | Where | Modified |
|---|---|---|---|---|
| [LWJGL](https://www.lwjgl.org) 3.4.3: `lwjgl`, `lwjgl-opengl`, `lwjgl-openal`, `lwjgl-sdl`, `lwjgl-stb` | BSD-3-Clause | Java bindings | nested jars, `META-INF/jars/` | yes: LWJGL2-signature bridges added to `org.lwjgl.opengl.GL11`, `ARBOcclusionQuery`, `org.lwjgl.openal.AL10`, `AL`; `AL.destroy()` made public; state-setting methods of `GL11`, `GL13`, `ARBMultitexture` replaced to also update Altrender's GL state tracker, and `GL11` calls counted (listed in each jar's `META-INF/altrender-modified.txt`) |
| LWJGL natives (`lwjgl`, `lwjgl_opengl`, `lwjgl_stb`), incl. libffi | BSD-3-Clause; libffi MIT | JNI libraries | `altrender/natives/<os>-<arch>/` | no |
| Khronos OpenGL headers (in `lwjgl-opengl`) | Khronos (MIT-style) | API definitions | (compiled in) | no |
| [SDL3](https://libsdl.org) 3.4.14 | zlib | window, context, input | `altrender/natives/<os>-<arch>/` | no |
| [OpenAL Soft](https://openal-soft.org) 1.25.2 | LGPL-2.0-or-later | sound | `altrender/natives/<os>-<arch>/` | no (shipped unmodified, as LWJGL 3.4.3 builds it) |
| [stb](https://github.com/nothings/stb) (in `lwjgl_stb`) | MIT or public domain | image decode/encode | `altrender/natives/<os>-<arch>/` | no |

Platforms: `linux-x64`, `linux-arm64`, `windows-x64`, `windows-arm64` (no macOS, owner). Natives are LWJGL 3.4.3's
own builds from Maven Central (`org.lwjgl:<module>:3.4.3:natives-<platform>`). Licence texts:
`META-INF/licenses/altrender/natives/` (sources: `altrender/lwjgl2/licenses/README.md`).

**OpenAL Soft source (LGPL):** attached to every release (owner, 2026-11-12): the exact commit LWJGL built
(`7f9c6de1162b…`, checksum-pinned; `./gradlew :altrender:lwjgl2:openalSource`). The jar's
`META-INF/licenses/altrender/natives/OPENAL_SOFT_SOURCE.txt` says where it is and how to swap in another build.

Code from [Celeritas](https://git.taumc.org/embeddedt/celeritas) (LGPL-3.0), which derives from
[Sodium](https://github.com/CaffeineMC/sodium)'s (CaffeineMC) LGPL-3.0 releases, 0.5.11 and earlier, through Embeddium:
the terrain buffer arena in `altrender/common/…/terrain/arena/`, and the UI framework and options screen (`ui/framework/`,
`ui/widgets/Button`, `ui/widgets/Tooltip`, `ui/options/`), whose
options GUI derives from [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options) (MIT, Copyright
(c) 2021 FlashyReese; notice in `META-INF/licenses/altrender/reeses-sodium-options.txt`) (`docs/provenance.md`).
Altrender's terrain renderer follows Sodium 0.5's design (regions sharing a buffer arena, the visibility graph,
multi-draw). No code from Sodium 0.5.12 or later (PolyForm Shield 1.0.0) is used.

AltShaders' jar contains code from [Iris](https://github.com/IrisShaders/Iris) (LGPL-3.0, commit `bff1e69c`): the
shader pack format (options, `shaders.properties`, includes, id maps, program sets) in `altshaders/common/…/pack/`,
with Java's own collections in place of Guava, fastutil and JOML (`docs/provenance.md`). The C preprocessor that
replaces Iris's JCPP is AltShaders' own; no JCPP code is used. The custom-uniform expression engine in `altshaders/common/…/uniform/` (`stareval`,
`parsing`, `custom`) is Iris's too (its bundled `kroppeb.stareval` parser and its uniform functions), with own small
vector types in place of JOML. Two pipeline files carry portions of Iris's: the shadow camera in
`altshaders/common/…/pipeline/ShadowPass.java` and the `centerDepthSmooth` smoothing in `…/pipeline/CenterDepth.java`.
The rest of AltShaders' pipeline is its own code, matching Iris's behaviour (clears, flips, blending, uniforms).

## Required or recommended at runtime (not bundled)

| Project | Licence | Role |
|---|---|---|
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | Apache-2.0 | mod loader (via Ornithe gen2) |
| [LegacyFix](https://github.com/betacraftuk/legacyfix) | GPL-3.0 | recommended: fixes for old versions on modern Java; never bundled or linked |

## In the repository, build only

| Project | Licence | Files |
|---|---|---|
| [Gradle](https://gradle.org) wrapper | Apache-2.0 | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` |

Build-time tools fetched by Gradle (not in the repo): Fabric Loom (MIT), Ornithe Ploceus (Apache-2.0), Stonecutter
(LGPL-3.0), Feather and Calamus mappings (CC0-1.0).
