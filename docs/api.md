# Altrender API

`altrender:api` is the only part of Altrender other mods compile against: plain Java, no Minecraft or loader types,
the same on every Minecraft version Altrender supports. `AltrenderApi.VERSION` is the version of the Altrender that's
running (semantic; below 1.0.0 nothing is stable yet). Everything is render-thread only unless it says otherwise.

## Settings pages (since 0.2.0)

A mod adds its own pages to Altrender's video settings. They appear in the side bar under the mod's name, after
Altrender's own pages, and get search, tooltips and Apply/Undo/Done like Altrender's.

```java
AltrenderApi.registerOptionPages(new OptionPageProvider() {
    public String modId() { return "mymod"; }
    public String modName() { return "My Mod"; }
    public List<OptionPage> pages() {        // called each time the settings open
        OptionStorage storage = flags -> MyConfig.save();
        Option<Boolean> sparkles = Option.<Boolean>builder("sparkles")
                .name("mymod.options.sparkles")          // a translation key, or plain text
                .tooltip("mymod.options.sparkles.tooltip")
                .impact(OptionImpact.LOW)
                .control(new Control.TickBox())
                .binding(() -> MyConfig.sparkles, v -> MyConfig.sparkles = v)
                .storage(storage)
                .build();
        return List.of(new OptionPage("mymod.main", "My Mod", List.of(OptionGroup.of(sparkles))));
    }
});
```

- **Controls:** `Control.TickBox`, `Control.Slider(min, max, step, format)` (integers; `max - min` a multiple of `step`),
  `Control.Cycling(values, names[, allowed])` (values `allowed` rejects are skipped), `Control.Action(label, run)`
  (since 0.3.0: a button that runs at once; the option's value isn't used).
- **Shader pack screen** (since 0.3.0): a shader pack addon registers a `ShaderPackMenu`
  (`AltrenderApi.registerShaderPackMenu`): its packs, the choice, and the chosen pack's option screens (toggles,
  choices or sliders, links, profiles) with pending values; Altrender shows it as an Iris-style screen ("Shader
  Packs..." in the video settings, O in the world, `AltrenderApi.openShaderPackScreen()`).
- **Desktop** (since 0.3.0): `AltrenderApi.desktop()` opens a folder in the file manager and shows the system's
  open-file dialog (SDL3; the answer comes back on the render thread).
- **Changes wait for Apply:** a control sets a pending value; Apply calls each changed option's setter, then
  `OptionStorage.save(flags)` once per storage that had a change. Undo drops pending values; Done is available once
  nothing is pending.
- **Flags** (`OptionFlag`) ask Altrender to do something once after Apply: rebuild the terrain, reload textures, lay
  screens out again. `REQUIRES_GAME_RESTART` marks the option's tooltip.
- **Unavailable options:** `available(condition, reason)` shows the option struck through, with the reason in its
  tooltip.
- **Impact** (`OptionImpact`: low, medium, high, varies) is shown in the tooltip.

## Building GL programs (since 0.2.0)

`AltrenderApi.shaderPrograms()` (null while Altrender's renderer is off) builds programs without stalling the render
thread: `build(name, vertexSource, fragmentSource, attributes, outputs)` returns a `PendingProgram` at once; poll
`done()`, then `ok()`, `program()` (the GL name) and `log()`; `delete()` frees it. With
`KHR_parallel_shader_compile` (`parallel()`), the driver compiles on its own threads; issue a few builds per frame
and poll the rest to keep a loading screen responsive.

Since 0.3.0 `build(name, stages, attributes, outputs)` takes any set of stages (`ShaderPrograms.Stage`: vertex,
tessellation control and evaluation, geometry, fragment, or compute alone); a failed build's log names each failing
shader's stage.

## Versions (since 0.3.0)

`AltrenderApi.supports("0.3.0")`: whether the running API is that version or a later compatible one (same major;
below 1.0.0, same minor). An addon checks it at start and stays off when it's false.

## Capabilities (since 0.3.0)

`AltrenderApi.capabilities()` (null while the renderer is off): the tier (`gl45`/`gl33`), the GL version, vendor,
renderer and extensions, and the tier's features: compute, storage buffers, image load/store, tessellation and indirect
count are off on `gl33` even where the driver has them; per-buffer blending is on wherever GL 4.0 or
`ARB_draw_buffers_blend` is. A new object after a tier switch.

## Replacing the world render (since 0.3.0)

One `WorldPipeline` at a time (`AltrenderApi.setWorldPipeline`) replaces how the world is drawn: a shader pack. Altrender
calls it during each world render, on the render thread:

| Call | When |
|---|---|
| `beginWorld(width, height, partialTick)` | start of the world render, before the game clears |
| `cameraReady()` | the camera's matrices are set (`AltrenderApi.camera()` has them) |
| `phase(RenderPhase)` | the world render now draws sky, terrain (solid/translucent), entities, cracks, outline, particles, weather, clouds or the hand; `NONE` in between |
| `fixedFunction(Change)` | the game switched texturing, fog or lighting, or set a fog parameter (`AltrenderApi.fixedFunction()` has the values), from inside its GL call |
| `entity(type, blockLight, skyLight)`, `blockEntity(blockKey, …)` | the game draws an entity or block entity next (modern type or block key, light 0–15 at it); returning true takes the light: its draws are white and the pipeline gives the light |
| `entityColor(r, g, b, a)` | that entity flashes (hurt red, a creeper's white) as one colour over its texture; returning true takes it: the game skips its own overlay pass |
| `entityEyes(on)` | its glowing eyes (a spider's) draw next, or are done |
| `terrain()` | its `TerrainPipeline`, or null: programs for the terrain's passes (`SOLID`; `CUTOUT`, the opaque layer's quads with see-through texels, drawn after; `TRANSLUCENT`; `TRANSLUCENT_DEPTH`, the translucent layer drawn for depth only; `translucentDepthDraw()` false skips that draw) and
the block ids; while it has one, Altrender meshes in `TerrainFormat`'s extended format |
| `handFirst()`, `particlesLate()` | asked each frame: whether the solid hand draws before the translucent terrain, and whether the particles draw after it (instead of where the game draws them) |
| `endWorld()` | after the hand, before the HUD |
| `resized(width, height)`, `destroy()` | the framebuffer size changed; free everything (a replaced pipeline, a tier switch: it stays installed and makes its objects again on the next frame, or the renderer stopping) |

Between the calls the game draws its own geometry (fixed-function, so a compatibility-profile program's `gl_*`
inputs work) with the program and into the target set by `Gpu.gameDraws(framebuffer, program)`, with extra textures
from `Gpu.gameTexture(unit, texture)` (unit 0 is the game's own), and Altrender draws its terrain there too. The
pipeline can also take over parts of the game's state for those draws: its alpha test (`gameAlphaTest`), blending for
all buffers or per draw buffer (`gameBlend`, `gameBufferBlends`) and light coordinates (`gameLightCoords`); the
game's own calls are still tracked and come back at the end of the world.
Altrender restores that target, program and those textures after each of its own passes. `gameDraws(null, 0)` hands
the screen back; "the screen" is the game's own framebuffer when the pipeline first took the game's draws.

## GPU objects and drawing (since 0.3.0)

`AltrenderApi.gpu()` makes textures (`TextureSpec`: 1D, 2D, 3D or rectangle, any internal format as a GL enum),
framebuffers and buffers, and draws on Altrender's GL context and state cache, so an addon makes no GL calls of its
own. The pipeline's own work goes between `begin(label)` and `end()` (a debug group; `end()` gives the game its state
back): bind a framebuffer (`null`: the screen), viewport, program and uniforms, textures, images and storage buffers;
blend (all buffers or one), depth and colour mask; clear (a colour, an integer colour, alpha only, depth); `drawFullscreen()` (a 0..1 quad with OptiFine's
composite-pass matrices); `dispatch` and `memoryBarrier`; `copyDepth`/`copyColor` into a texture.
`beginTimer`/`endTimer(TimedPass)` time the pipeline's shadow pass and its full-screen and compute passes on the GPU;
F3's GPU breakdown shows them as "shadow" and "pack".

## Camera, game and fixed-function state (since 0.3.0)

- `AltrenderApi.camera()`: the modelview and projection the game set this frame and last frame (column-major
  `float[16]` copies).
- `AltrenderApi.gameState()`: the dimension as a namespaced id (`minecraft:overworld`, `minecraft:the_nether`), time
  and sky angle, weather, sky colour, camera position, the fluid and light at the eye, biome, held item (id, damage,
  the light it gives), player stats and flags, render distance, the light curve and sky darkness.
- `AltrenderApi.terrainView()`, `entityView()`: the terrain and the game's entities from another camera (a shader
  pack's shadow map); the terrain view takes a blend for every buffer and per buffer and a `ViewCulling` (Iris's
  shadow culling: a distance box, only what can shadow the player's view, a safe box; `backFaces` turns face culling
  on, which the view otherwise leaves off), and draws the opaque layer's
  solid and cut-out quads as `Pass.SOLID` and `Pass.CUTOUT`; the entity view follows `gameBlend`/`gameBufferBlends`. `WorldPipeline.hides(VanillaPart)` switches off clouds, weather, sun, moon, stars or
  the round shadows under entities.
- `AltrenderApi.blockNames()`: the version's blocks (`name`, `properties`, `tags` by block key) and items
  (`itemName`) under their modern names and tags, for shader packs' block.properties and item.properties.
- `AltrenderApi.fixedFunction()`: texturing, fog (on, mode, start, end, density, colour, radial or eye-plane
  distance), lighting and alpha test, as the game's own GL calls set them.
