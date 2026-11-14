# Shader pack compatibility (development.md 6.10)

AltShaders loads Iris-format shader packs on b1.7.3 through Altrender's API. This page records how the test packs
run, per tier, and what b1.7.3 can't give them. The packs are downloaded to the git-ignored `packs-cache/` (Modrinth,
sha512 checked); they are never committed or shipped.

**Statuses:** *works*: the pack's look in every test scene, no errors; *partial*: it runs, with the gaps named;
*refused*: AltShaders won't load it (the missing features are named).

**How it was checked:** the scene harness (`tools/testscene/capture-set.sh`, scenes in `tools/testscene/scenes`:
cave, cave with entities, entities by day and at night, entity flashes, nether, day, fast graphics, flat light,
night, rain, snow, underwater, in lava, sleeping, the hand by day and night, the player's shadow, cut-outs, particles),
each pack at its default profile, Intel UHD (Mesa 26.1), gl45 tier, 1280×720; two shots 60 frames apart per scene;
0 GL driver errors required. The gl33 tier (Intel) and NVIDIA (GTX 1650 Ti, driver 595, gl45) were checked on the day,
cave and night-entities scenes, also with 0 driver errors.

## Matrix

| Pack (version, profile) | gl45, Intel | gl33, Intel | gl45, NVIDIA | Notes |
|---|---|---|---|---|
| Photon v1.4 (High) | works | works | works | Terrain, water, clouds, shadows, caves, nether, entities. Its sky light (`colorimg4`, a deferred compute) reaches shaded areas since 2026-11-14 (they were near black before). Under water the surface seen from below shows broken reflections (not yet compared with Iris). |
| BSL 10.1.8 (High) | works | works | works | Every scene as expected: water reflections, clouds, shadows, caves, rain. Its `alphaTest.*` overrides apply. |
| Complementary Reimagined r5.9.3 | works | works | works | Every scene as expected. |
| MakeUp Ultra Fast 9.6 (medium) | works | works | works | Every scene as expected. `fixedTaaCorrection` (a `vec2 * float` custom uniform) doesn't resolve, as in Iris. |
| Sildur's Vibrant 2.02 Extreme | works | works | works | Terrain through `gbuffers_textured` (Iris's fallback). In lava the sky stays visible (its land fog only). |
| Refrax v1.3.1 (defaults) | works | | | Terrain, shadows incl. the player's, coloured torch light (its shadow pass voxelizes through images, bound since 2026-11-14), caves, nether. Inside a block (the `nether` scene's camera) the frame is black where other packs draw a dim overlay. Its `vxProjInv*` custom uniforms (Voxy's) don't resolve: unused without Voxy. |
| ar_minimal (original) | works | works | works | Matches no pack: 7 of 14 scenes identical, the rest 56–93 dB (fixed-function lighting precision, rain/snow drop edges). |
| ar_debug_ids (original) | works | | | 158 of 181 b1.7.3 block states show their id; the other 23 are states the game changes when placed alone. |

## What b1.7.3 can't give a pack

These standard uniforms keep a neutral value (0, false, -1 or none); a pack that reads one is told once in the log
(`StandardUniforms.UNAVAILABLE`):

- effects: `nightVision`, `blindness`, `darknessFactor`, `darknessLightFactor`, `playerMood`;
- player states that don't exist yet: `is_sprinting`, `is_invisible`, `inSwimmingAnimation`, `isElytraFlying`,
  `isSpectator`, hunger (`currentPlayerHunger`, `maxPlayerHunger`: no hunger before b1.8);
- the off hand: `heldItemId2`, `heldBlockLightValue2`, `heldBlockLightColor2`;
- the End's flashes: `endFlashIntensity`, `previousEndFlashIntensity`, `endFlashPosition`;
- modern display settings: `currentColorSpace` (sRGB), `textureFilteringMode`.

Other differences packs may notice:

- **Block tags** (`IRIS_TAG_SUPPORT` 2): b1.7.3's blocks are in the modern tags they'd be in, in 1.21.11 or 26.1–26.3
  (Altrender's own list, checked against those versions' tags): `grass_block` is in `#dirt` (to 1.21.11, which packs
  like Photon 1.4 rely on) and `#grass_blocks` (26.1 on). Tags without b1.7.3 members (`%terracotta`, `%candles`, …)
  match nothing. As in Iris, a block's name entries come first and its tags only fill in.
- **Biomes:** b1.7.3's biomes map to their nearest modern ones (`Biomes`, Review); newer biomes' `BIOME_*` names are
  -1.
- **Block ids:** b1.7.3's blocks, items and entities go by their modern names (block states where b1.7.3 has them:
  crop age, slab type, lit furnaces, …). The id files are read in their modern branch (`MC_VERSION` 12111 there,
  10710 in the shaders; docs/decisions.md 2026-11-13).
- **One terrain layer pair:** b1.7.3 has solid (with cut-outs) and translucent: the first takes the pack's cut-out
  program (`gbuffers_terrain_cutout` → `gbuffers_terrain` → …, as Iris's fallbacks), the second its water program;
  `gbuffers_terrain_solid` and `_cutout_mip` aren't used on their own.
- **Particles:** b1.7.3's are all opaque ones, so `particles.ordering` `before` and `mixed` draw them where the game
  does (before the translucent terrain) and `after` (Iris's default for packs with deferred passes) after it. They
  carry no normals: under a pack they get (0, 0, 1) (Review), which packs that light from `gl_Normal` need.
- **Flashes and eyes:** the hurt red (b1.7.3's 0.4; twice on spiders, as the game draws it) and a creeper's white
  flash reach `entityColor` and the game's own overlay pass is skipped; packs then light the flash, where b1.7.3
  didn't light the creeper's. Spider eyes use `gbuffers_spidereyes` with Iris's default additive blend (b1.7.3
  alpha-blends them, fading with light); packs without it draw them with its fallback and the game's blend.
- **No Distant Horizons or Voxy:** `dh_*` and `voxy_*` programs are never used.
- **Fixed-function draws:** the sky, entities, particles, clouds and the hand are the game's own draws under the
  pack's programs (compatibility profile), as on OptiFine-era Minecraft.

- **Thin blocks:** faces take their normal from the game's winding (snow layers, rails, plates, ladders and crossed
  sprites face the right way for packs' lighting).
- **Blob shadows:** the game's round shadows under entities are hidden when the pack has a shadow map (as OptiFine
  and Iris); otherwise they draw at full light (Photon lit them as emissive).

- **Not provided:** `blendFunc`, `gtextureSize`, `gtextureId` and `textureReloadCount` (none of the tested packs
  reads them); the shadow depth's RRR1 swizzle Iris keeps for old packs.
- **Shadow back-face culling** (AltShaders' settings, off by default): the backs of solid blocks left out of the
  shadow map; shadows stay the same, a little less shadow-pass work.
- **Shadow samplers:** a `sampler2DShadow` compares and a `sampler2D` reads raw depth, whatever
  `shadowHardwareFiltering` (Iris sets the compare mode on the texture, so a plain sampler there reads undefined
  values; Review).

Blend overrides (`blend.<program>` and `blend.<program>.<buffer>`) apply to the game's draws, the terrain and the
shadow pass (Iris's default for shadow programs: off), and to the begin, prepare, deferred, composite and shadowcomp
passes (`blend.compositeN`; the final pass never blends, as Iris).
