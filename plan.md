# Voxy LOD Lighting Parity Refactor Plan

## Scope
This document is a handoff plan for implementing chunk-parity LOD lighting in this branch.
Goal: make Voxy LOD shading match vanilla/Embeddium chunk shading as closely as possible under Iris shader packs (including VanillAA).

Date context: 2026-02-25.
Environment context: Craftoria instance on `tesseract` (Windows Prism Launcher) is current test target.

## Current State Summary
- Active test shaderpack in Craftoria is VanillAA with a Voxy patch (`voxy.json`, `voxy_opaque.glsl`, `voxy_translucent.glsl`) embedded in `VanillAA.zip`.
- A key brightness mismatch was already identified and corrected in shaderpack patching: use `getLighting(interData.y)` (Embeddium-compatible lightmap sampling path) instead of `parameters.lightMap` path.
- Remaining mismatch is structural: Voxy LOD currently has only one light id per quad and lacks per-vertex AO/brightness + per-vertex lightmap semantics used by vanilla/Embeddium.

## Why Perfect Match Is Not Yet Possible
Vanilla/Embeddium lighting path computes per-vertex values:
- Per-vertex brightness (AO/smooth lighting multiplied by directional shade)
- Per-vertex lightmap
- Then raster interpolation across each quad

Voxy LOD currently:
- Stores one packed light byte per quad in quad payload
- Applies simplified face-level lighting in shader path
- Has no per-corner AO/light payload to interpolate

Conclusion: true parity requires extending geometry payload and lighting computation pipeline, not only shader tweaks.

## Reference Sources (Read First)
Use these reference files as the source of truth for behavior to match.

### Vanilla / NeoForge references
- `.reference/minecraft/1.21.1/decompiled/net/minecraft/client/multiplayer/ClientLevel.java`
  - `getShade(Direction, boolean)` constants and `getShade(float,float,float,boolean)` behavior
- `.reference/minecraft/1.21.1/decompiled/net/neoforged/neoforge/client/model/lighting/QuadLighter.java`
  - `calculateShade(...)` formula
  - Per-vertex processing pipeline and normal-based shading

### Embeddium references
- `.reference/embeddium/src/main/java/org/embeddedt/embeddium/impl/model/light/flat/FlatLightPipeline.java`
- `.reference/embeddium/src/main/java/org/embeddedt/embeddium/impl/model/light/smooth/SmoothLightPipeline.java`
- `.reference/embeddium/src/main/java/org/embeddedt/embeddium/impl/model/light/data/LightDataAccess.java`
  - AO/light data packing and emissive/AO semantics

## Local Code Map (Where Changes Must Happen)
### Geometry generation + packing
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
  - Current packed quad format generated in `Mesher.emitQuad(...)`
  - Current 64-bit quad payload assembly in `packPartialQuadData(...)`
- `src/main/java/me/cortex/voxy/client/core/util/ScanMesher2D.java`
  - Merging constraints (quads are merged only when payload key is identical)

### GPU geometry upload assumptions
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryManager.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`
  - Current hardcoded geometry element size assumptions: 8 bytes per quad

### Shader-side quad decode and shading
- `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`

### Model metadata/shading flags
- `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java`

### World data/light encoding constraints
- `src/main/java/me/cortex/voxy/common/world/other/Mapper.java`
- `src/main/java/me/cortex/voxy/common/world/other/Mipper.java`
- `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java`

## Proposed Target Architecture
### Quad payload v2
Move from 64-bit quad payload to 128-bit payload (2x64), keeping draw command logic the same.
- Word A: existing geometry + ids (compatible decode for existing logic)
- Word B: lighting payload
  - `light4`: 4 corner packed light bytes (BL/SL nibble each)
  - `ao4`: 4 corner AO/brightness bytes (quantized)

Reason: this enables per-fragment interpolation of corner AO/light, matching vanilla pipeline behavior class.

### Lighting bake strategy
Implement CPU-side corner light bake per emitted quad:
- Use neighborhood occupancy/state to derive corner AO/brightness and corner light ids
- Match Embeddium smooth/flat semantics as closely as feasible
- Include model shading flags (`isShade`) and directional shade formula

### Shader strategy
- Decode `light4`/`ao4` in shader
- Interpolate by quad-local coordinates
- Sample light texture using existing `getLighting(...)`-compatible mapping
- Multiply atlas color by interpolated AO/brightness and light sample

## Implementation Phases
## Phase 0: Baseline and instrumentation
- Add debug toggles to visualize:
  - AO factor only
  - Light factor only
  - Chunk-vs-LOD delta approximation
- Capture baseline screenshots and logs in Craftoria with VanillAA before major refactor.

Deliverables:
- Repro notes and baseline captures committed in docs or notes.

## Phase 1: Data format migration (8-byte -> 16-byte quad)
- Introduce quad format version constants in Java and GLSL.
- Update geometry buffer size accounting and upload arena assumptions from 8-byte element size to configurable element size.
- Update decode helpers in `quad_format.glsl` to read new structure.
- Keep old fields readable to avoid touching unrelated culling/draw logic initially.

Deliverables:
- Build succeeds.
- LOD renders identically to pre-migration when using compatibility path.

## Phase 2: Per-corner light payload generation
- Add a corner-light bake module in `RenderDataFactory` (or helper class).
- For each emitted quad, compute and pack:
  - 4x corner light ids
  - 4x corner AO/brightness terms
- Ensure mesher merge key includes lighting payload equivalence so incorrect cross-merge does not occur.

Deliverables:
- Payload populated and decoded in shader (can be no-op on color initially).
- Validation shader can display per-corner payload.

## Phase 3: Shader parity path
- In patched and non-patched paths, use interpolated corner AO/light for final color modulation.
- Replace simplified single-face shading path for parity mode.
- Maintain fallback mode via compile define/config for regression isolation.

Deliverables:
- LOD/chunk boundary brightness mismatch significantly reduced.

## Phase 4: Semantics alignment and edge cases
- Align constant ambient light behavior (Nether-like dimensions).
- Validate emissive and translucent interactions.
- Tune quantization and interpolation precision.

Deliverables:
- Stable visuals across Overworld/Nether-like contexts.

## Phase 5: Mip-level realism follow-up (optional but recommended)
- Investigate `Mipper` light aggregation policy which currently uses coarse heuristics.
- Improve distant mip light/material selection to reduce far-distance lighting drift.

Deliverables:
- Better far-LOD light plausibility beyond boundary region.

## Acceptance Criteria
- Seam tests at chunk/LOD boundary under VanillAA show no obvious brightness jump in daytime and nighttime.
- Indoor and shadowed scenes no longer show LOD consistently brighter than chunks.
- No shader compile errors across opaque/translucent paths.
- No geometry upload corruption or offset/count regressions.
- Performance remains acceptable (document delta for meshing time and VRAM usage).

## Test Protocol
### Local build and validation
- `./gradlew build`
- If available, run existing validation scripts in `scripts/` used by this repo.

### Remote Craftoria test cycle
- Deploy mod build using existing script:
  - `./scripts/deploy.sh Craftoria`
- Check remote logs for shader mode and patch mode:
  - `./scripts/logs.sh Craftoria latest`
- Manually reload shaders in-game and compare boundary scenes.

## Known Risks
- Quad payload size increase impacts memory bandwidth and geometry arena capacity.
- Mesher merge behavior may reduce quad fusion if payload contains high-variance per-corner data.
- True parity depends on how closely CPU AO bake can mirror Embeddium neighborhood semantics.
- Some far-distance mismatch can persist due to world mip policy (`Mipper`) even with perfect shader-side interpolation.

## Open Questions for Implementer
- Whether to preserve strict 64-bit path behind a config for low-memory mode.
- Whether AO should be stored as 8-bit linear or custom curve to better match perceived contrast.
- Whether to include additional per-corner metadata (emissive/material flags) in v2 payload now or defer.

## Suggested Execution Order for Next Agent
1. Implement Phase 1 only, commit when rendering parity is unchanged.
2. Implement Phase 2 + payload debug views, commit.
3. Implement Phase 3 parity shading, commit.
4. Validate on Craftoria VanillAA scenes and iterate constants.
5. Open follow-up task for `Mipper` semantics if far-LOD mismatch remains.
