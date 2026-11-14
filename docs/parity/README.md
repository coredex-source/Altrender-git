# Parity testing (b1.7.3)

Every rendering milestone is judged by screenshots: the fixed scenes rendered with vanilla (today: Altrender doesn't
draw yet; later `-Daltrender.renderer=off`) and with Altrender, on the same GPU, diffed with `tools/imgdiff`.

```sh
tools/testscene/capture.sh <scene> [--tag T] [--gpu nvidia|llvmpipe] [--renderer off] [--out DIR] [--pack default]
java tools/imgdiff/ImgDiff.java a.png b.png [diff.png] [--min-psnr DB]
tools/testscene/capture-set.sh [--jobs 3] [--tag T] [scene ...] [-- -P...]   # several scenes in parallel, one table
```

- Scenes: `tools/testscene/scenes/*.json` (format in `Scene.java`): `overworld_day`, `overworld_night` (torches,
  glowstone), `overworld_fast` (Fast graphics), `overworld_flat_light` (smooth lighting off), `rain`, `snow` (nearest
  snowy biome), `cave` (a lit room underground: ores, lava), `nether`, `entities`. Seed 20261112, anchored on column
  (35, 34). The day showcase has glass + water, ice, leaves next to a log, flowers, tall grass, a cactus on sand, a
  torch, glowstone, a jack-o-lantern, a jukebox, a sign (block entity) and wool colours.
- Each capture: a fresh world from the seed, the scene built in the same frame (no world tick in between), ticks and
  frame interpolation frozen, wait until the compile queue has been empty for 60 frames, shot **a**, 60 frames later
  shot **b**, quit. 1280×720 into the harness's own framebuffer (the window may be any size).
- **Textures:** the original `ar_debug` pack (`tools/packs/ar_debug/generate.py`), so the committed references
  contain no Mojang texture (development.md 0.3). Each atlas tile shows its index as 8 bits; cutout tiles have a
  transparent checkerboard, water/ice are half transparent. The font is still the game's (b1.7.3 loads it from its own
  jar, not the pack); scene shots hide the GUI. `--pack default` captures with the game's textures, for local use only.

## Determinism (measured)

| Source of variation | Fix |
|---|---|
| World ticks (time, weather, mobs, fluids, fire, leaf decay, random ticks) | harness freezes the tick timer after building the scene: no tick ever runs on the scene's world |
| Frame interpolation (camera, entities, clouds use the partial tick, which follows wall time) | partial tick pinned to 1.0 |
| Animated textures (water, lava, fire, portal start from `Math.random`) | `TextureManager.tick` cancelled under the harness: static atlas tiles |
| Fog brightness eases per tick toward the camera's brightness | pinned to its converged value when the scene settles |
| Cloud scroll and rain animation counters | both tick counters set to 0 |
| Spawn point: `World.random` is unseeded and picks it, and the area around it is generated first, which decides the order neighbouring chunks are decorated in (trees on chunk borders) | world random seeded from the world seed before the spawn search; scenes anchored on a fixed column, not on the spawn |
| Nether portal orientation (`PortalForcer` has its own unseeded random) | seeded |
| Mobs: body rotation and animation offsets from `Math.random` in the constructor | set explicitly per placed mob |
| Mob spawner's display mob (random rotation) | not used in scenes |
| Item drops (a placed block can break a neighbour: their spin and bob are random) | scene blocks placed legally (cactus with free sides); any item drop removed after building |
| Title screen (splash text pulses) still open after starting the world | closed by the harness |
| Window manager resizing the window (niri tiles it) | capture framebuffer at a fixed size |
| Mouse grab / pause on focus loss | cancelled under the harness |
| Particles spawned during load | cleared at the freeze |

Results (2026-11-12, Intel UHD, Mesa 26.1.8): all nine scenes **bit-identical** between shot a and b within a run,
and between two separate runs (psnr = inf everywhere). No known nondeterminism remains.

## Reference set

`reference/b1.7.3/<scene>-vanilla-intel.png` plus `.meta` (scene, camera, options, GL driver, settle frames): vanilla
b1.7.3 on Altrender's LWJGL 3 / SDL3 layer (Altrender doesn't render anything itself yet), Mesa Intel UHD (CML GT2),
Mesa 26.1.8, GL 4.6 compatibility, 1280×720, `ar_debug` textures. llvmpipe and NVIDIA sets are added when Phase 2's
parity runs need them (`capture.sh <scene> --gpu llvmpipe|nvidia`).

## Benchmark mode

A scene with `"benchSeconds": N` orbits its camera (16 blocks, one lap per 15 s, looking along the path): a warm-up
lap, then until the compile queue settles, then N seconds measured. Output: `<scene>-<tag>-bench.csv` (every frame
time) and `bench_frames`, `bench_avg_fps`, `bench_low1_fps` (FPS over the slowest 1% of frames), `bench_p50_ms`,
`bench_p99_ms` in `.meta`. First check (vanilla, Intel UHD, Normal distance, 10 s): 2606 frames, 260.6 FPS average,
144.4 FPS 1% low.
