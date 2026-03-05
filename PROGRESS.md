# Voxy NeoForge Shader Trace Progress

Last updated: 2026-03-04

## Alignment Batch Applied (2026-03-04)

- Synced `src/main/resources/assets/voxy/shaders/**` directly to `.reference/voxy-upstream` (`rsync --delete` parity pass).
- This removed local `lod/gl46/shadow.frag` and restored upstream `lod/pos_util.glsl`.
- Updated `IrisShaderPatch` parse handling to fail loudly again:
  - parse mismatch now logs full context and throws `ShaderLoadError` (no silent `return null` fallback).
- Updated `IrisVoxyRenderPipelineData` draw target resolution:
  - removed silent out-of-range remap-to-last-target behavior.
  - draw-target failures now log requested index + available count and rethrow.
- Compile verification passed (`./gradlew compileJava -x test`).

## Alignment Batch 2 Applied (2026-03-04)

- Removed Iris render-target expansion hooks from active mixins:
  - deleted `mixin/iris/MixinPackRenderTargetDirectives.java`
  - removed `"iris.MixinPackRenderTargetDirectives"` from `client.voxy.mixins.json`
- Re-aligned `MixinProgramSet` behavior toward upstream patch flow:
  - removed fallback compatibility-mode orchestration and extra render-target expansion mutation path.
  - patch creation now follows direct `IrisShaderPatch.makePatch(...)` flow with upstream-like control behavior.
- Reconciled GLSL rewrite band-aid in `IrisVoxyRenderPipeline`:
  - `shadow2D`/`shadow2DLod` rewrites are now opt-in only via `-Dvoxy.irisGlslCompatFixes=true`.
  - default runtime path now aligns with upstream behavior (no automatic global GLSL rewrite).
  - added explicit debug/log marker when compat rewrite mode is enabled.
- Compile verification passed (`./gradlew compileJava -x test`).

## Alignment Batch 3 Applied (2026-03-04)

- Removed inactive/non-upstream shadow injection source:
  - deleted `mixin/iris/MixinShadowRenderer.java` (it was no longer registered in mixin JSON).
- Re-aligned `MixinIrisRenderingPipeline` default behavior:
  - deferred Voxy renderer recreation after Iris pipeline rebuild is now opt-in via
    `-Dvoxy.deferIrisPipelineRecreate=true` (default off, upstream-leaning runtime behavior).
  - added explicit log when deferred recreate support is disabled by default.
- Cleaned stale shadow-path diagnostics/comments in:
  - `core/VoxyRenderSystem.java`
  - `mixin/embeddium/MixinDefaultChunkRenderer.java`
- Compile verification passed (`./gradlew compileJava -x test`).

## Current Residual Divergence (Post Batch 3)

Shader resources:

- `src/main/resources/assets/voxy/shaders/**` now matches upstream exactly (no remaining deltas).

Primary remaining Java divergence sets:

- `client/iris/`
  - `IrisShaderPatch.java`
  - `IrisVoxyRenderPipelineData.java`
  - `VoxySamplers.java`
  - `VoxyUniforms.java`
- `client/mixin/iris/`
  - `IrisRenderingPipelineAccessor.java`
  - `MixinIrisRenderingPipeline.java`
  - `MixinLevelRenderer.java`
  - `MixinProgramSet.java`
- `client/core/`
  - `IrisVoxyRenderPipeline.java`
  - `VoxyRenderSystem.java`
  - `rendering/util/LightMapHelper.java`

These are now the focused target files for further upstream-reduction while preserving 1.21.1 + Iris/Monocle/Embeddium backport compatibility.

## Alignment Batch 4 Applied (2026-03-04)

- Simplified `mixin/iris/MixinIrisRenderingPipeline` to reduce divergence surface:
  - removed deferred-recreate retry/state machinery.
  - kept only:
    - resilient constructor-tail pipeline build path for backport stability,
    - viewport capture/apply injection path.
  - retained explicit diagnostics around patch/pipeline build outcomes.
- Compile verification passed (`./gradlew compileJava -x test`).

## Scope + Method

Compared current tree against `.reference/voxy-upstream` using:

- `git diff --no-index --name-status` on:
  - `src/main/resources/assets/voxy/shaders`
  - `src/main/java/me/cortex/voxy/client/iris`
  - `src/main/java/me/cortex/voxy/client/core`
  - `src/main/java/me/cortex/voxy/client/mixin/iris`
  - `src/main/java/me/cortex/voxy/client/mixin/minecraft`
- Line-level hunk review on each shader delta + Iris pipeline integration files
- `rg` searches for high-risk keywords: fallback/remap/disable/TAA/sampler/uniform/depth/fog/cloud/shadow

## Shader Delta Inventory

Only file set differences vs upstream in shader assets:

- Added: `lod/gl46/shadow.frag`
- Removed: `lod/pos_util.glsl`
- Modified:
  - `chunkoutline/outline.vsh`
  - `lod/gl46/bindings.glsl`
  - `lod/gl46/quads.frag`
  - `lod/gl46/quads3.vert`
  - `lod/hierarchical/node.glsl`
  - `lod/hierarchical/screenspace.glsl`
  - `lod/hierarchical/traversal_dev.comp`
  - `lod/quad_format.glsl`
  - `lod/quad_util.glsl`
  - `lod/section.glsl`
  - `post/ssao.comp`
  - `util/memcpy.comp`

Net diffstat in shader assets: `14 files, +208/-206`.

## High-Risk Misalignment Candidates

1. Silent patch failure path in `IrisShaderPatch`
- Current branch changed parse errors to `return null` (non-fatal) for `voxy.json`.
- Risk: silently disables Voxy shader patch path for packs that partially parse, creating inconsistent visuals and making failures harder to detect.

2. Render target remap fallback in `IrisVoxyRenderPipelineData#normalizeRenderTargetIndex`
- Out-of-range colortex target gets remapped to `targetCount - 1`.
- Risk: writes into wrong buffer can cause brightness/composite mismatches and pack-specific artifacts.

3. SSAO behavior divergence in `post/ssao.comp`
- Current implementation uses simplified AO angle path with fewer guards/sampling behavior changes.
- Risk: flatter or inconsistent AO appearance vs upstream and pack expectations.

4. HiZ occlusion bias hack in `lod/hierarchical/screenspace.glsl`
- Added `hizBias = 0.0002 * exp2(miplevel)`.
- Risk: can hide flicker but may increase false negatives (over-render), creating different distant compositing characteristics.

5. Shadow path fork (`lod/gl46/shadow.frag` + `MixinShadowRenderer`)
- New non-upstream shadow pass integration guarded by system properties.
- Risk: drift from upstream shading semantics; difficult reproducibility if flags differ between environments.

## Likely Intentional/Useful Backport Alignments

1. Lightmap sampling correction in `lod/gl46/bindings.glsl`
- Changed from `/15` style UV mapping to `/16` center-aligned mapping.
- Expected effect: reduce systematic over-brightness from incorrect light texel sampling.

2. Fog/cloud shaderpack gating
- `MixinFogRenderer` and `MixinLevelRendererClouds` now avoid overriding shaderpack-controlled paths.
- Expected effect: fewer shaderpack cloud/fog conflicts.

3. Pipeline resilience during Iris reload
- Deferred recreation and destroyed-render-target handling in Iris mixins/pipeline.
- Expected effect: fewer hard failures during shader reload cycles.

## Potential Band-Aid Areas To Revisit

- `MixinPackRenderTargetDirectives` + `MixinProgramSet` expanding color target limits to 17/200 by system property.
- Global string replacements for legacy GLSL calls in `IrisVoxyRenderPipeline#applyGlslCompatFixes`.
- Multiple runtime property gates (`voxy.*`) that alter rendering paths without a single validation matrix.

These may be necessary for 1.21.1 backporting, but should be reviewed as controlled compatibility layers instead of permanent default behavior.

## Proposed Next Work (Source-Trace Driven)

1. Lock down silent-failure behavior first
- Make `voxy.json` parse failures explicit in logs with pack name + path + reason, and optionally hard-fail behind a debug flag.

2. Validate render-target remap correctness
- Instrument actual resolved draw targets per pack and compare against pack directives before remapping.
- If remap is unavoidable, map by known semantic fallback (not just last target index).

3. Reconcile SSAO and HiZ forks against upstream
- A/B test upstream vs current for:
  - `post/ssao.comp`
  - `lod/hierarchical/screenspace.glsl`
  - `lod/hierarchical/traversal_dev.comp`
- Keep only deltas that are empirically required.

4. Decide shadow strategy
- Either:
  - fully align to upstream (disable local shadow fork by default), or
  - keep fork but formalize as an experimental feature with documented expected artifacts.

5. Build repeatable diff workflow
- For each iteration, run and append results:
  - `git diff --no-index --name-status .reference/voxy-upstream/src/main/resources/assets/voxy/shaders src/main/resources/assets/voxy/shaders`
  - `git diff --no-index --name-status .reference/voxy-upstream/src/main/java/me/cortex/voxy/client/iris src/main/java/me/cortex/voxy/client/iris`
  - `git diff --no-index --name-status .reference/voxy-upstream/src/main/java/me/cortex/voxy/client/mixin/iris src/main/java/me/cortex/voxy/client/mixin/iris`
  - `rg -n "fallback|remap|disable|TODO|FIXME|shader|sampler|uniform|depth|fog|shadow|cloud" src/main/java src/main/resources/assets/voxy/shaders`

## Immediate Reset Candidates (If We Want Upstream Parity First)

Most likely candidates to reset first and then re-apply minimal required changes:

- `src/main/resources/assets/voxy/shaders/post/ssao.comp`
- `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp`
- `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl`

Rationale: these are quality/perception sensitive and currently contain non-trivial behavior forks.

## Alignment Batch 5 Applied (2026-03-04)

- Investigated camera-move CPU spike hypothesis with direct source-trace in hierarchical traversal + cleaner paths.
- Added motion-aware anti-thrash controls in Java path (backport-safe, runtime configurable):
  - `AbstractRenderPipeline`: `nodeCleaner.tick(...)` now receives active `Viewport`.
  - `NodeCleaner`:
    - new motion guard + cadence gates to avoid aggressive geometry cleanup during high camera velocity.
    - properties:
      - `voxy.nodeCleanerMotionGuard` (default `false`)
      - `voxy.nodeCleanerMotionThreshold` (default `2.0` blocks/frame)
      - `voxy.nodeCleanerMotionGuardFrames` (default `8`)
      - `voxy.nodeCleanerInterval` (default `1` frame)
    - periodic diagnostic log when guard suppresses clean pass.
  - `HierarchicalOcclusionTraverser`:
    - new motion-based request throttle to reduce mesh request burst while moving quickly.
    - properties:
      - `voxy.motionRequestThrottle` (default `false`)
      - `voxy.motionRequestThreshold` (default `2.0` blocks/frame)
      - `voxy.motionRequestScale` (default `0.25`)
      - `voxy.motionRequestLog` (default `false`)
    - optional periodic telemetry when throttle engages.
- Re-aligned two critical traversal divergences discovered by no-index upstream diff:
  - restored render-distance uniform write in `uploadUniform(...)` (using backport accessor `getSectionRenderDistance()`).
  - restored zero-dispatch guard for first compute dispatch to avoid known driver errors on zero workgroup count.
- Verification:
  - `./gradlew compileJava -x test` passed after changes.
  - `git diff --no-index --name-status` confirms shader directory parity remains exact.

## Alignment Batch 6 Applied (2026-03-04)

- Added pipeline-lifecycle stabilization for Iris reload windows:
  - `VoxyRenderSystem.scheduleRendererRecreate(reason)`
    - throttled by `voxy.irisRecreateMinIntervalMs` (default `1500` ms)
    - deduplicates queued recreate attempts
    - logs before/after pipeline types for each recreate
  - `MixinIrisRenderingPipeline` now requests deferred Voxy renderer recreate after successful Iris Voxy pipeline build (opt-out via `-Dvoxy.deferIrisPipelineRecreate=false`).
  - `IrisVoxyRenderPipeline` now requests deferred recreate when Iris reports destroyed RenderTargets mid-bind.
- Added sticky last-known-good Iris pipeline-data reuse in `RenderPipelineFactory`:
  - if current Iris pipeline data is transiently null, attempts to reuse last good data for up to
    `voxy.irisStickyDataWindowMs` (default `5000` ms) before falling back to `NormalRenderPipeline`.
- Verification:
  - `./gradlew compileJava -x test` passed.
