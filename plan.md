# Voxy LOD Boundary Fix Plan

## Problem Statement

6-chunk gaps appear at the LOD/vanilla boundary when moving or flying.
The depth mask (ChunkBoundRenderer) does not cover the full area that MC
considers "loaded", so LOD geometry bleeds through.

---

## Root Cause Analysis

### The lifecycle chain

```
Server packet → ClientChunkCache.replaceWithPacketData()
  → ClientLevel.onChunkLoaded()
      → Embeddium ChunkTracker: FLAG_HAS_BLOCK_DATA set
  → (async) applyLightData()
      → Embeddium ChunkTracker: FLAG_HAS_LIGHT_DATA set
          → 3×3 neighbour check: if centre + all 8 neighbours have FLAG_ALL
              → next frame: RenderSectionManager.onChunkAdded() fires
                  → our mixin hook fires → ChunkBoundRenderer.addSection()
```

### Finding 1 — renderDistance +3 mismatch (systematic, always present)

`ClientChunkCache.inRange()` accepts chunks up to radius `max(2, renderDistance) + 3`
from the player centre. MC loads **3 extra rings** of chunks beyond the render
distance setting for lighting and neighbour purposes.

Our `shouldRender()` in `outline.vsh` tests against `negInnerSec.w`, which is set
to `getEffectiveRenderDistance() * 16` (bare render distance, no +3).

Result: the 3 extra rings of chunks ARE added to ChunkBoundRenderer on load,
but `shouldRender` clips their AABBs out — they produce no depth mask geometry.
The depth mask ends 3 chunks (48 blocks) short of where MC actually has chunks.

**This alone accounts for a 3-chunk systematic gap, regardless of movement.**

### Finding 2 — 3×3 neighbour requirement (movement-dependent gap)

Embeddium's `ChunkTracker.updateMerged()` requires all 8 neighbours to also have
`FLAG_ALL` before `onChunkAdded` fires. When moving, the leading edge always has
chunks loaded but without all neighbours yet → those chunks are not in
ChunkBoundRenderer → gap at the leading edge.

This is partially unavoidable, but is **amplified** by Finding 1.

### Finding 3 — 1-block AABB expansion missing

Upstream `outline.vsh` uses `icorner-1` / `icorner+17` (1-block outward expansion)
when computing the closest AABB corner for the distance test. This gives a 1-block
numerical tolerance for floating-point rounding in the camera position.

Our version uses `icorner` / `icorner+16` (exact boundaries). Missing this
contributes sub-chunk flickering at the exact boundary edge.

### Finding 4 — circular vs square (minor, already partially fixed)

Upstream uses circular distance (`x² + z² < r²`). MC chunk loading is square
(Chebyshev: `max(|x|,|z|) <= r`). We already switched to Chebyshev in
`shouldRender`, which is correct for matching MC's square pattern. No change
needed here.

---

## Planned Fixes

### Fix 1 — Correct `renderDistance` in `ChunkBoundRenderer.java`

**File**: `src/main/java/me/cortex/voxy/client/core/rendering/ChunkBoundRenderer.java`

Change:
```java
final float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
```
To:
```java
// MC loads chunks at radius (renderDistance + 3) — see ClientChunkCache.calculateStorageRange().
// The depth mask must cover this full radius, not just bare renderDistance, or a systematic
// 3-chunk gap appears between the mask edge and where LODs start.
final float renderDistance = (Minecraft.getInstance().options.getEffectiveRenderDistance() + 3) * 16.0f;
```

This is the single most impactful fix. It closes the 3-chunk systematic gap.

### Fix 2 — Restore 1-block AABB expansion in `outline.vsh`

**File**: `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh`

Match upstream's corner computation exactly:

```glsl
// Expand AABB by 1 block outward (matches upstream) for numerical robustness.
// Prevents sub-chunk flicker from FP rounding of the camera position.
vec3 corner = vec3(
    mix(
        mix(ivec3(0), icorner - 1, greaterThan(icorner - 1, ivec3(0))),
        icorner + 17,
        lessThan(icorner + 17, ivec3(0))
    )
) - negInnerSec.xyz;
```

### Fix 3 — Remove `boundaryBuffer` (now unnecessary)

With Fix 1 applied, the systematic gap is closed. `boundaryBuffer` was a workaround
for the gap; it is now harmful (causes water flickering) and should be removed.

- **`ChunkBoundRenderer.java`**: remove `boundaryBuffer` from uniform upload
- **`outline.vsh`**: remove `boundaryBuffer` from UBO and `shouldRender` logic
- **`VoxyNeoForgeConfig.java`**: remove `LOD_BOUNDARY_BUFFER` config entry
- **`VoxyConfig.java`**: remove `getLodBoundaryBuffer()` delegation

### Fix 4 — Consider reverting to mesh-build tracking (optional / investigative)

Our change to load-level tracking (Fix from previous session) ensures no holes for
chunks loaded but not yet meshed. However, the 3×3 neighbour requirement means the
mask boundary will always lag by ~1 chunk at the leading edge while moving.

Whether to revert or keep load-level tracking should be evaluated after Fix 1 is
applied and tested. If movement gaps persist, reverting to mesh-build tracking
(which naturally respects the 3×3 neighbour boundary) may give a tighter
visual match at the cost of some "see-through" on direction changes.

---

## Implementation Order

1. Apply Fix 1 (`renderDistance + 3`) — highest impact, no side effects
2. Apply Fix 2 (1-block AABB expansion) — correctness, matches upstream
3. Apply Fix 3 (remove `boundaryBuffer`) — cleanup after Fix 1 makes it moot
4. Build, deploy, test in Craftoria
5. Evaluate Fix 4 based on test results

---

## Files to Change

| File | Change |
|------|--------|
| `src/main/java/me/cortex/voxy/client/core/rendering/ChunkBoundRenderer.java` | `renderDistance` → `(rd + 3) * 16` |
| `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` | 1-block expansion, remove boundaryBuffer |
| `src/main/java/me/cortex/voxy/client/config/VoxyNeoForgeConfig.java` | Remove LOD_BOUNDARY_BUFFER |
| `src/main/java/me/cortex/voxy/client/config/VoxyConfig.java` | Remove getLodBoundaryBuffer() |

---

## Key References

- `ClientChunkCache.calculateStorageRange()`: `max(2, renderDistance) + 3`
- `ClientChunkCache.Storage.inRange()`: `Math.abs(x - centerX) <= chunkRadius`
- `ChunkTracker.updateMerged()`: 3×3 neighbourhood `FLAG_ALL` requirement
- Upstream `outline.vsh`: `icorner-1` / `icorner+17` expansion, circular distance
- `EmbeddiumWorldRenderer.processChunkEvents()`: drives `onChunkAdded`/`onChunkRemoved`
