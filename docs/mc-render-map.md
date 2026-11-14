# Minecraft render map

How each supported version draws a frame, in our own words, so Altrender's adapters know what they replace. Class and
method names are **Feather gen2** names for that version (MCP-era names in parentheses only for orientation). Nothing
here is copied from the game; behaviour is described, not code. If the code disagrees with this file, the code wins.

---

## b1.7.3 (feather-gen2 `b1.7.3+build.2`, read 2026-11-12)

### 1. Window, context and the frame loop: `Minecraft`

**Startup (`Minecraft#init`, our banner mixin sits at its head):**
- Window through LWJGL2 `Display`: parented to the applet canvas, or fullscreen at the current mode, or a windowed
  `DisplayMode(width, height)`. Title "Minecraft Minecraft Beta 1.7.3". `Display.create()` with the default pixel
  format (no explicit depth/stencil request); on failure it waits one second and tries once more.
- Then, in order: options (`options.txt`), texture packs, `TextureManager`, the font, the water/grass/foliage colour
  maps (read from textures into int arrays), `GameRenderer`, the hand renderer, the Mojang loading screen (its own
  ortho setup and a buffer swap), `Keyboard`/`Mouse`/`Controllers` creation (controller failure is caught and
  ignored), base GL state, the capability probe, sound, the nine animated textures (§7), `WorldRenderer` (allocates
  every terrain display list and occlusion query up front, §4), viewport, `ParticleManager`, the resource-download
  thread, the HUD (`GameGui`), and the title screen.
- Base GL state set once: 2D texturing on, smooth shading, clear depth 1, depth test with LEQUAL, alpha test GREATER 0.1,
  back-face culling, identity projection and modelview.
- `glGetError` is checked and logged at three startup points and twice per frame.

**Per frame (`Minecraft#run`):**
1. Reset the `Box`/`Vec3d` pools; close request → stop.
2. Advance the timer, run `ticksThisFrame` game ticks (`Minecraft#tick`: input events, world tick, and the animated
   texture update at 20 Hz, §7).
3. Set the static `BlockRenderer.fancyGraphics` from the options, update sound, enable 2D texturing.
4. **`World#doLightUpdates()` runs here, on the render thread, every frame.**
5. **`Display.update()` (swap and input pump) runs before the frame is drawn**, unless F7 (LWJGL key 65) is held.
6. Interaction manager's render hook, then `GameRenderer#render(partialTick)` (§2).
7. Inactive window: leave fullscreen and sleep 10 ms.
8. F3 on: the frame-time chart (`renderProfilerChart`, §12) over everything.
9. Toasts, `Thread.yield()`, an extra `Display.update()` while F7 is held, the screenshot key (F2, LWJGL key 60:
   `glReadPixels` RGB with pack alignment 1), applet canvas resize, and once a second the F3 string
   "`<n> fps, <m> chunk updates`".
- Fullscreen toggle (F11): desktop display mode, `setFullscreen`, `update`, resize. `resize` resets the viewport size
  used by later frames.

**What this means for 0.4 (the LWJGL2 layer):** b1.7.3 needs, at least, `Display` (parent, fullscreen, display modes,
title, create, close request, update, active, swap, destroy, desktop mode), `DisplayMode`, `Keyboard`, `Mouse`,
`Controllers`, `LWJGLException`, `GLContext.getCapabilities()` with the **fields** `GL_ARB_occlusion_query`,
`GL_NV_fog_distance` and `GL_ARB_vertex_buffer_object`, `ARBOcclusionQuery`, `ARBVertexBufferObject`, and
**`org.lwjgl.util.glu.GLU.gluPerspective`** (LWJGL2's util jar, not core). The full list comes from the constant-pool
scan in 0.4.

### 2. Frame order: `GameRenderer#render` and `GameRenderer#renderWorld`

`render(partialTick)`:
1. Window inactive for over 500 ms → pause menu. While focused: mouse look (cubic sensitivity curve, optional smoothing
   with "smooth camera").
2. With a world: `renderWorld(partialTick, deadline)`. The deadline only limits terrain compiles (§4); it comes from the
   framerate option: Max = no deadline, Balanced = 1/120 s, Power saver = 1/40 s plus a sleep to that pace. Then the HUD
   (`GameGui#render`) unless F1 hid it.
   Without a world: viewport, identity matrices, GUI ortho, the Power-saver sleep.
3. An open screen: clear depth, draw the screen, then its particle layer.

`renderWorld` runs once, or twice with anaglyph 3D (one pass per eye, §10). Each pass:

| # | What | GL state worth knowing |
|---|---|---|
| a | crosshair raycast, interpolated camera position, chunk-cache centre | cull + depth on |
| b | anaglyph colour mask for this eye | |
| c | viewport, clear colour = fog colour (§3), clear colour + depth | |
| d | camera: projection = `gluPerspective(fov, aspect, 0.05, renderDistance × 2)` with anaglyph x-shift and zoom; modelview: anaglyph shift, hurt tilt, view bobbing, portal wobble, then the camera itself (third-person back-off by raycast, bed orientation when sleeping, pitch/yaw, eye height) | matrix stack only |
| e | `Frustum.getInstance()`: **reads both matrices back with `glGetFloat`** and builds the planes | sync point |
| f | sky (§3), **only at Far or Normal render distance** | sky fog mode |
| g | world fog on; smooth shading when smooth lighting is on | |
| h | frustum test of sections (§4) | |
| i | eye 0 only: compile dirty sections until done or the deadline passes (§4) | |
| j | **opaque + cutout terrain** (layer 0) with `/terrain.png` | lighting off, alpha test on, fog on |
| k | entities and block entities (§9), then "lit" particles (item pickup) | fixed-function lighting on (§6) |
| l | the other particles, three batches by texture: `/particles.png`, `/terrain.png`, `/gui/items.png` | lighting off |
| m | camera underwater: mining cracks and block outline here (before the water) | |
| n | **translucent terrain** (layer 1). Fancy: a depth-only pass of layer 1 (colour writes off), then the same lists again with colour, so only the nearest translucent surface shows. Fast: one pass | blend SRC_ALPHA / ONE_MINUS_SRC_ALPHA, cull off |
| o | depth writes, culling back on, blend off; cracks and outline when not underwater | |
| p | rain and snow (§8) | blend, alpha GREATER 0.01 |
| q | clouds: Fast = one flat textured layer; Fancy = 3D boxes, depth-only pass then colour pass | fog per pass |
| r | clear depth, then hand and held item plus screen overlays (fire, in-wall, underwater) | |

After the last pass the colour mask is set to RGB with alpha writes off.

### 3. Fog, clear colour and sky

**Clear/fog colour (`setupClearColor`):** the world's fog colour, pulled towards the sky colour by an amount that grows
with render distance, dimmed by rain and thunder, replaced underwater (dark blue) and in lava (dark red), multiplied by
an interpolated fog-brightness value, converted for anaglyph. Used for `glClearColor` and `GL_FOG_COLOR`.

**Fog (`setupFog(mode)`)**, always followed by colour material on (front, ambient):
- underwater (and the unused "thick fog" case): EXP fog, density 0.1;
- in lava: EXP fog, density 2.0;
- otherwise LINEAR fog from `renderDistance × 0.25` to `renderDistance`; the sky mode (`mode < 0`) uses 0 to
  `renderDistance × 0.8`. With `NV_fog_distance`, **radial fog** (eye-radial distance mode). In the Nether ("unnatural"
  dimension) the fog starts at 0.
- `renderDistance = 256 >> viewDistance`: 256 / 128 / 64 / 32 blocks for Far / Normal / Short / Tiny.

**Sky (`WorldRenderer#renderSky`, overworld only):** three display lists made once (§11): a flat "light sky" plane above
the camera drawn in sky colour with fog; the sunrise/sunset fan (triangle fan, smooth shaded, rotated by time of day);
sun and moon quads (`/terrain/sun.png`, `/terrain/moon.png`) with additive blending, rotated by time of day and faded
by rain; the star list (additive, by star brightness); then a "dark sky" plane below the camera, tinted darker when the
dimension has ground. Depth writes are off for the whole sky.

### 4. Terrain: `WorldRenderer`, `RenderChunk`, `ChunkRenderer`

**Sections and the grid**
- A section (`RenderChunk`, MCP `WorldRenderer`) is 16³ blocks. The grid is *n* × 8 × *n* sections around the camera
  (8 × 16 = the 128-block world height), with *n* = min(400, 64 << (3 − viewDistance)) / 16 + 1: **26, 17, 9, 5** for
  Far, Normal, Short, Tiny.
- It works as a ring buffer: when the camera has moved more than 4 blocks, sections that fall off one side are moved to
  the other (`sortChunks`); a moved section is reset and marked dirty. The draw list is re-sorted by distance at the same
  time.
- **Display lists are allocated once for the largest grid:** 64³ × 3 = 786,432 list names. Each section owns three: its
  opaque/cutout list, its translucent list, and a box list for its occlusion test. With `ARB_occlusion_query`, 64³ query
  objects are generated up front as well.
- Precision: a section's geometry is compiled relative to the 1024-block region it sits in, plus a tiny scale
  (1.000001) about the section centre to hide cracks. At draw time sections are grouped by region (`ChunkRenderer`, up
  to four regions per layer, each a buffer of list ids): one translate from camera to region, then `glCallLists` for
  all of that region's sections.

**What makes a section dirty**
- Block change → the 3 × 3 × 3 box around it → every section touching that box (neighbours included); region changes the
  same way, one block wider.
- Each `render` call also checks ten sections round-robin and re-queues dirty ones.
- **Sky darkness step (`World#tick` → `notifyAmbientDarknessChanged`): every section that saw sky light is rebuilt.**
  This is the expected finding: combined light is baked into the vertex colours, so day/night costs full terrain
  rebuilds (§6).
- Full reload (all sections recreated and dirty): world change, view distance change, Fancy/Fast, smooth lighting,
  Advanced OpenGL. Leaves' face culling follows Fancy/Fast at reload.

**Scheduling (`compileChunks`)**
- The active branch: every dirty section within 16 blocks of the camera compiles immediately (nearest last); of the
  farther ones only the best two (visible first, then nearest) compile per call. `renderWorld` keeps calling until the
  queue is empty or the frame deadline passes, on eye 0 only.
- **Compiles are synchronous on the render thread and write straight into display lists** (`GL_COMPILE`).

**Compiling a section (`RenderChunk#compile`)**
- A `WorldRegion` is made over the section plus one block on each side. It holds **references to the live chunks**, not
  a copy.
- For layer 0, then layer 1: walk y, z, x; on the first non-air block, open the layer's list (push matrix, region
  offset, crack scale) and start the shared `Tesselator` with the section origin as offset. Blocks of the other layer
  only record that a second pass is needed. Block entities that have a renderer are collected during the first pass and
  merged into the global "block entities to render" list.
- Each layer ends with one `Tesselator` draw *inside the list*, so the list captures the vertex data at compile time.
- Records which layers are empty, and whether any sky light was read (a **static** flag on `WorldChunk`, set as a side
  effect of light reads).

**Layers**
- 0 = opaque and cutout (alpha test 0.1): everything else, glass and plants included; leaves are cutout in Fancy and
  culled as opaque in Fast.
- 1 = translucent: water (still and flowing), ice, nether portal (stairs take their base block's layer).
- No translucency sorting inside or between sections; Fancy relies on the depth pre-pass in §2 n.

**Culling**
- Frustum: each section's box grown by 6 blocks. Invisible sections are re-tested every frame, visible ones every 16th
  frame (staggered by index).
- "Advanced OpenGL" (occlusion queries; needs the extension and the option, off in anaglyph, layer 0 only): sections go
  front to back in doubling batches (16, 32, 64, …). For each batch: collect finished query results without blocking
  (check "available" first), draw the visible sections, then issue queries for the batch's boxes with colour and depth
  writes, texturing, lighting, alpha test and fog off. Far sections are queried less often (every 1 + distance/128
  ticks).
- F3 counters: `C: compiled/total. F: frustum-culled, O: occluded, E: empty`.

### 5. Block rendering: `BlockRenderer` and `Tesselator`

- `BlockRenderer#tesselateInWorld` dispatches on the block's render type: full cube (smooth-lit or flat), cross,
  torch, fire, liquid, redstone wire, crops, door, ladder, rail, stairs, fence, lever, cactus, bed, repeater, piston
  parts, and so on. Each one emits quads into the shared **`Tesselator.INSTANCE`**.
- `Tesselator` (MCP `Tessellator`): 2,097,152-int buffer (8 MiB); a vertex is 8 ints (32 bytes): position 3 floats,
  UV 2 floats, colour 4 bytes, normal 3 bytes + pad. **Quads are expanded to triangles as they are written** (6 vertices
  per quad, drawn as `GL_TRIANGLES`). Draws use **client-side vertex arrays**; a VBO path exists but is hard-disabled.
  It flushes itself when nearly full.
- **Live-world reads during meshing**, all of which Phase 2's snapshot must cover:
  - block ids and metadata through `WorldRegion` (live chunk arrays);
  - light through `Block#getBrightness` → region → live chunk sky/block light, the world's current sky darkness and
    the dimension's brightness table (slabs, farmland and stairs take the brightest neighbour; above y 127: full sky
    minus darkness);
  - biome tint for grass and leaves: the world's **shared `BiomeSource`**, filled for one column and then read from its
    shared temperature/downfall arrays (mutable shared state), plus the colour-map arrays;
  - block entities (collection only);
  - **block shape: many render types call `Block#updateShape`/`setShape`, which mutate the block singleton's bounds**;
  - static flags: `BlockRenderer.fancyGraphics`, `GameRenderer.anaglyphEnabled`, the smooth-lighting option, and the
    static sky-light flag above.
  - Conclusion: vanilla meshing is single-threaded by construction. Off-thread meshing needs the snapshot (ids, meta,
    light, biome colours, block entities) **and** per-thread block-shape state (or main-thread meshing for shape-mutating
    types). This is the main risk for Phase 2's "reuse the vanilla block renderer" rule.
- Mining cracks: the block re-tesselated with a forced crack sprite (tile 240 + stage), polygon offset (−3, −3),
  multiplicative blend (DST_COLOR, SRC_COLOR).

### 6. Lighting

- Per block position: `light = max(sky − skyDarkness, block)` (0–15), then `brightnessTable[light]`. Table: a fixed
  curve with an ambient floor of 0.05 in the overworld and 0.1 in the Nether. The result is **baked into the vertex
  colour**, multiplied by the face shade and the biome/block tint.
- Flat (non-smooth) face shades: bottom 0.5, top 1.0, north/south 0.8, west/east 0.6. Grass sides aren't tinted; their
  overlay is.
- Smooth lighting (the `ao` option): per vertex, the average of four already-mapped brightness values (the face
  neighbour, two edge neighbours, and the corner, where the corner is replaced by an edge when both edges are opaque).
  Opaque neighbours supply their own (dark) brightness: that is the whole AO effect; there is no separate AO term.
- **Parity note for Phase 2:** the averaging happens *after* the table, which isn't linear, so "average the raw light,
  then map in the shader" doesn't match vanilla. Moving the day/night combination into the shader (to stop the §4
  rebuilds) while keeping parity means carrying each vertex's four (sky, block) pairs (8 nibbles = 32 bits) and doing
  combine → table → average in the shader, with the darkness as a uniform.
- Entities and items: fixed-function lighting (`Lighting#turnOn`): two directional lights (diffuse 0.6, no specular),
  scene ambient 0.4, colour material for ambient and diffuse, flat shading; their brightness comes from `glColor` set
  from the world brightness at the entity.

### 7. Textures: `TextureManager` and `DynamicTexture`

- Path → GL texture cache. The `%clamp%` and `%blur%` path prefixes switch wrap and filter; default is nearest and
  repeat. A mipmap path exists behind a static flag that is off. Upload: `glTexImage2D` RGBA8 from a direct buffer.
  Anaglyph recolours pixels at upload time (toggling it reloads every texture).
- Atlases: `/terrain.png` and `/gui/items.png`, 16 × 16 tiles of 16 px. Vanilla has no high-resolution support.
- **Animated textures** (`DynamicTexture` subclasses, MCP `TextureFX`): still lava, still water, nether portal,
  compass, clock, flowing water, flowing lava, fire (two). Every game tick (20 Hz, from `Minecraft#tick`) each one
  computes a 16 × 16 RGBA image (1024 bytes) and uploads it with `glTexSubImage2D` into its tile. "Replicate" uploads
  the same image into an N × N block of tiles (the flowing liquids use 2 × 2); "copy to" uploads into a separate
  texture.
- Texture-pack switch: `TextureManager#reload` re-reads every loaded image and re-uploads it into the same GL names, and
  reloads the colour maps.

### 8. Weather, clouds, particles, overlays (all immediate through `Tesselator`)

- Rain and snow: for each column within 5 blocks (10 in Fancy) whose biome has precipitation, a pair of crossed quads
  from the precipitation height to camera ± radius, textured `/environment/rain.png` or `snow.png`, coloured by the
  world brightness there and faded by distance; per column a seeded random offset and a tick-based scroll.
- Clouds: §2 q. Texture `/environment/clouds.png`, scrolled by ticks, at the dimension's cloud height.
- Particles: four lists (particles.png, terrain, items, "lit"); one `Tesselator` batch per texture.
- Hand overlays: on fire, inside a block (that block's side sprite), underwater.
- Block outline: line strips and lines, black at 40 % alpha, width 2, depth writes off.

### 9. Entities and block entities

- `WorldRenderer#renderEntities` (skipped for two frames after a reload): prepare both dispatchers with the camera,
  set the camera offset, draw global entities (lightning), then every entity that passes its distance check and the
  frustum (or ignores it), skipping the camera entity in first person and entities in unloaded chunks, then every
  block entity collected from compiled sections.
- Entity models: one display list per model part, compiled the first time the part is drawn (§11).

### 10. Anaglyph 3D

- Two full world passes with colour masks (eye 0: green + blue, eye 1: red); projection shifted ±0.07 and modelview
  ±0.1 per eye. Terrain compiles only on eye 0; occlusion queries are off.
- Every colour on the way (fog, clear, sky, clouds, sunrise, block tints) goes through a red/cyan conversion, and every
  texture is recoloured at upload (animated ones too). The HUD is drawn after the passes, unaffected.

### 11. Display lists outside terrain

| Owner | Lists | When |
|---|---|---|
| `TextRenderer` | 288 (256 glyphs + 32 colour codes) | at font load; strings are drawn with `glCallLists` |
| `ModelPart` | 1 per entity model part | lazily, first draw |
| `WorldRenderer` sky | 3 (stars: 1500 quads from a fixed seed; light plane; dark plane) | once, at construction |
| `RenderChunk` | occlusion box (third list of each section) | when the section moves |
| `WorldRenderer` | 1 more list, allocated and never used | construction |

Everything else (clouds, weather, particles, outlines, sun, moon, sunrise, the F3 chart, GUI) is immediate. The
compatibility profile is required: vanilla's leftovers (font, models) keep using display lists.

### 12. Options, the video settings screen and F3

**Options (`GameOptions`, saved to `options.txt` as `key:value` after every change):**
- `viewDistance` 0–3 = Far, Normal, Short, Tiny; `fancyGraphics`; `ao` (shown as Smooth Lighting); `fpsLimit` 0–2 =
  Max, Balanced, Power saver (§2); `anaglyph3d`; `bobView`; `guiScale` 0 (auto) to 3; `advancedOpengl` (occlusion
  queries). Not on the video screen: F1 hide GUI, F5 third person, F3 debug (`debugEnabled`, not saved), smooth camera,
  debug camera.
- Side effects: Graphics, Smooth Lighting and Advanced OpenGL reload the world renderer; Anaglyph reloads every texture;
  render distance applies at the next frame (reload when it differs).

**Video settings (`VideoOptionsScreen`, opened from `OptionsScreen` button id 101):** eight cycle buttons in two
columns (Graphics, Render Distance, Smooth Lighting, Performance, 3D Anaglyph, View Bobbing, GUI Scale, Advanced OpenGL)
and Done (id 200, saves and returns). No sliders (sliders exist only for float options).
- Ours (5.2) replaces this screen at that entry point and maps these values into our option model. Advanced OpenGL
  has no meaning with our culling; Performance becomes our frame pacing (3.6).

**F3 (`GameGui#render`, debug block):** left column: version with the fps/chunk-updates string, the section counters
(§4), the entity counters (`E: rendered/total. B: …, I: …`), world and chunk-source strings, then x, y, z and facing
(0–3); right column: used and allocated memory. The frame-time chart (`Minecraft#renderProfilerChart`) is drawn after
the HUD with its own ortho projection in window pixels: two 16.7 ms reference bands, the average, and one line per
recent frame (tick part in a second colour). Ours (5.3) replaces both.

### 13. GL readbacks, extensions and other call sites

- Per frame: `glGetFloat` of projection and modelview (frustum), `glGetError` twice. `GlStateTracker` should serve the
  matrices so the readback isn't a driver round-trip.
- Occasional: `glReadPixels` (screenshot), `glGetString` (crash report).
- Extensions through `GLContext.getCapabilities()`: `ARB_occlusion_query` (§4), `NV_fog_distance` (radial fog, §3),
  `ARB_vertex_buffer_object` (Tesselator, disabled).
- GL11 is called from 73 client classes (GUI screens and widgets, every entity and block-entity renderer, models,
  particles, `GameRenderer`, `WorldRenderer`, `RenderChunk`, `ChunkRenderer`, `Tesselator`, `TextureManager`,
  `TextRenderer`, `Frustum`, `Lighting`, `MemoryTracker`, `Screenshot`, the crash panel, texture packs). That count is
  the size of the interception problem in 0.6.
