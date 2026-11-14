# Altrender (+ AltShaders)

**Disclaimer: This mod was made using the "help" of claude. Do not worry, all of the "generated" code is manually reviewed by me and all sources are credited properly.**

**Altrender** is a client mod for Ornithe (Fabric Loader, gen2) that gives old Minecraft versions a modern, fast
renderer: LWJGL 3 with SDL3 and OpenAL Soft, GL 3.3 and GL 4.5+ tiers, a modern video settings screen and a modern debug
screen. It starts at b1.7.3 and climbs to 1.14.4.

**AltShaders** is its addon for Iris-format shader packs. It requires Altrender.

## Building

```
./build.sh
```

Runs `./gradlew build` and copies the mod jars to `libs/` (`--sources` adds the sources jars; other arguments go to
Gradle). They're built in `altrender/mc/versions/<mc>/build/libs/` and `altshaders/mc/versions/<mc>/build/libs/`.

Dev client: `./gradlew :altshaders:mc:b1.7.3:runClient` (both mods) or `./gradlew :altrender:mc:b1.7.3:runClient`.
Options (`gradle/dev-runs.gradle`): `-Paltrender.runJava=25` (default 21), `-Paltrender.legacyfix=false`,
`-Paltrender.gpu=nvidia|llvmpipe`, `-Paltrender.apitrace=glx|egl|<wrapper>`, `-Paltrender.mixinExport=true`,
`-Paltrender.tier=gl33|gl45`, `-Paltrender.renderer=off`. Optional Nix shell: `nix develop`.

## Licence

Both mods: LGPL-3.0-only (`COPYING.LESSER`, on top of `COPYING`).

## Credits

- [LWJGL](https://www.lwjgl.org) 3.4.3 (BSD-3-Clause), embedded; its `GL11` and a few other classes are extended at
  build time.
- [Sodium](https://github.com/CaffeineMC/sodium) by CaffeineMC (LGPL-3.0 up to 0.5.11): Altrender's terrain renderer
  follows Sodium 0.5's design (regions sharing a buffer arena, the visibility graph, multi-draw), and its buffer arena
  is Sodium's, through Celeritas. Later Sodium versions (0.5.12+, 0.6+) are PolyForm Shield; none of their code is used.
- [Celeritas](https://git.taumc.org/embeddedt/celeritas) (LGPL-3.0, Sodium/Embeddium lineage): the terrain buffer
  arena and the options screen.
- [Iris](https://github.com/IrisShaders/Iris) (LGPL-3.0): AltShaders' shader pack support (the pack format and options,
  custom uniforms, the shadow camera, `centerDepthSmooth`) and the behaviour AltShaders matches.

Details: `THIRD_PARTY.md` and `docs/provenance.md`.
