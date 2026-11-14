# Photon: what it needs (development.md 0.8)

The Phase 7 target is Photon on its High profile at ≥ 60 FPS average and ≥ 45 FPS 1% low on the owner's GTX 1650 Ti
(PRIME), 1920×1080, render distance 16. This file records what Photon asks of the renderer, read from its own files.

## Pinned release

- **Photon v1.4** (Modrinth `photon-shader`, 2026-10-19, `photon_v1.4.zip`, sha512 checked; for MC 26.2/26.3), source
  `sixthsurge/photon` `main` @ `2a3d502c17d2e310412c717be47241f014cba7fc`.
- Licence (own): use, personal modification, and examining/learning from the source are allowed; redistribution on
  monetised platforms (Modrinth, CurseForge) needs the author's permission. We **never commit or ship it**: it lives in
  the git-ignored `packs-cache/`.

## Features

- `iris.features.required`: none. `iris.features.optional`: `CUSTOM_IMAGES`, `ENTITY_TRANSLUCENT`.
- Directives: `clouds=off` (its own clouds), `oldLighting=false`, `separateAo=true` (vanilla AO in a separate
  channel), `rain.depth=false`, `vignette=false`, `shadowPlayer=true`, `shadowEntities` per `ENTITY_SHADOWS`,
  `shadowBlockEntities` per `BLOCK_ENTITY_SHADOWS`, `dhShadow.enabled=false`. Distant Horizons, Photonics and
  Colorwheel programs (`dh_*`, `clrwl_*`) only matter with those mods: not for us.

## The High profile

`profile.high = INFO=2 shadowMapResolution=2048 SHADOW_PCF SHADOW_COLOR SHADOW_VPS ENTITY_SHADOWS !BLOCK_ENTITY_SHADOWS
ENVIRONMENT_REFLECTIONS SHADER_AO=SHADER_AO_GTAO VL !AIR_FOG_COLORED_LIGHT_SHAFTS !WATER_CAUSTICS WATER_PARALLAX
!COLORED_LIGHTS SHADOW_SSRT`

| What | High |
|---|---|
| Shadow map | 2048², distance 128 (default `shadowDistance`), PCF, coloured shadows, variable penumbra (VPS), screen-space ray-traced shadow detail (SSRT), entity shadows, no block-entity shadows |
| AO | GTAO (plus vanilla AO kept separately) |
| Lighting | volumetric light (VL), spherical-harmonics skylight (`SH_SKYLIGHT`, default on), no coloured lights (no LPV/voxels on High) |
| Reflections | screen-space (`ENVIRONMENT_REFLECTIONS`) |
| Water | parallax waves, no caustics |
| Sky | volumetric cumulus clouds (default), stars, its own sun/moon |
| Post | TAA, FXAA, bloom (13 bloom passes); DOF, motion blur, TAAU off |

## What the renderer must provide

- **Compute shaders on High: one program**, `world0/deferred4_a.csh` (SH skylight, `d4a_generate_sky_sh.csh`, uses
  image stores / std430). The other compute programs (`setup`, `shadowcomp`, `program/lpv/*`) only run with
  `COLORED_LIGHTS` (off on High, on in Ultra). So Photon High needs GL 4.3-class features: the **gl45** tier.
- **Render targets:** 17 colortex buffers in float/normalised formats (`colortex0` R11F_G11F_B10F, `1`/`2`/`6`/`16`
  RGBA16, `3` RGBA8, `4`/`5`/`9`/`11`/`13` RGBA16F, `7`/`12` RGB16F, `8` RGB8, `10`/`14` RG16F, `15` R32F); scaled sizes
  (`colortex4` 192×108, `6`/`7`/`14` half resolution, `8` 512×512, `9`/`10` by a quality setting).
- **Custom textures:** `image/noise.png`; raw 3D textures from `.dat` files (`RGB16F` 32×64×32 atmosphere scattering,
  two `R8` 64³ Worley noises); `image/galaxy.png`; and `minecraft:textures/environment/clouds.png`, the game's own cloud
  texture (b1.7.3: `/environment/clouds.png`, so AltShaders maps resource locations to old paths).
- **Uniforms** it reads (sample): `cameraPosition`, `gbufferModelView`/`Projection`, `shadowModelView`/`Projection`,
  `worldTime`, `frameTimeCounter`, `frameCounter`, `rainStrength`, `wetness`, `isEyeInWater`, `moonPhase`,
  `eyeAltitude`, `blindness`, `darknessFactor`, `viewWidth`, plus Photon's custom uniforms.
- **`block.properties`:** 95 entries in modern names, states (`tall_grass:half=lower`) and tag groups
  (`tags_leaves`, `tags_small_plants`, …) → the `BlockIdMapper` (owner, 0.6: our numeric-ID table + Angelica's
  `FlatteningMap`) and tag expansion.

## What an old version can't provide, and the planned fallback

| Gap | Fallback (decided in Phase 6) |
|---|---|
| Most modern blocks don't exist (b1.7.3 has ~96 block IDs) | unmatched entries simply never match; the b1.7.3 blocks that exist map through `BlockIdMapper` |
| `MC_VERSION`: Photon gates on `>= 11300`, `> 12111`, `>= 260100`, `>= 260300`, `< 11700` | report a pre-1.13 value so pre-flattening branches are taken; exact value chosen in Phase 6 |
| `darknessFactor` (1.19+), `blindness` effects | 0 |
| Moon phases (b1.7.3 has one moon texture, no phases) | `moonPhase` from the day count, or 0 (Phase 6) |
| Biome uniforms on modern biome categories | mapped from b1.7.3's biomes |
| Entity/translucent ordering features (`ENTITY_TRANSLUCENT`) | optional: off until Phase 6 decides |

## Modern-Iris yardstick (owner's machine): to measure

The Phase 7 target is relative to what Photon High costs on a modern Iris install on the same machine. Measurement
(owner): Minecraft 26.3, Sodium + Iris (current), Photon v1.4, profile **High**, 1920×1080, render distance 16, GTX
1650 Ti via PRIME (`__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia`), a fixed spot (same seed and place
each run), 60 s after chunks settle: average FPS and 1% low; and which passes cost most (Nsight Graphics GPU trace, or
Iris's own profiler if available).

| Setup | Avg FPS | 1% low | Heaviest passes |
|---|---|---|---|
| 26.3 + Sodium + Iris + Photon v1.4 High, RD 16, 1080p, GTX 1650 Ti | _to measure_ | _to measure_ | _to measure_ |
