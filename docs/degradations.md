# Degradations: where Altrender's picture differs from the game's on purpose

Development.md 0.4: optimizations that change pixels are listed here (and opt-in, unless the owner decides otherwise).

| Since | What | Difference | Switch |
|---|---|---|---|
| 2026-11-12 (2.5) | **Cave culling** (sections reachable from the camera only through connected faces) | A section hidden behind solid terrain except through a sub-pixel crack between two faces isn't drawn: the crack shows the background instead of the far terrain. Orbit test (8 views of overworld_day, culling on vs off): 1–3 pixels per 921,600 at 5 views, none at 3; the cave scene: identical at all 8. The game's own "Advanced OpenGL" occlusion queries hide sections the same way. | **(Review)** on by default; `-Daltrender.debug.caveCulling.disable` turns it off (an in-game option comes with the video settings, Phase 5) |
