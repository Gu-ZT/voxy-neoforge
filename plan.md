# Voxy Performance Handoff Plan: Async Copy Spike Elimination

## Context
Date context: 2026-02-26

This branch has already landed high-ROI performance improvements:
- `WorldSection` array reuse moved to bounded non-allocating queue, with `VOXY_PERF world_section_cache` metrics.
- `UploadStream` coherent path + pressure instrumentation added, with `VOXY_PERF upload_stream` metrics.
- Mesh generation limiter now gates on upload pressure.
- `AsyncNodeManager` now emits `VOXY_PERF async_node` and has copy-count-based sync wait threshold.

Current remaining gap in runtime logs is render-thread copy bursts from `AsyncNodeManager.tick(...)`, e.g.:
- `Large amount of copies, lag will probably happen: 757`

These are warning-level spikes (not hard crashes) and represent the main source of frame-time outliers after previous improvements.

## Problem Statement
`AsyncNodeManager.tick(...)` currently executes geometry copy work (`multiMemcpy`) as a single large batch when results arrive.

Path (current):
1. Worker thread (`run`) publishes a `SyncResults` object with `geometryUpload` data.
2. Render thread (`tick`) consumes all geometry copy entries in one dispatch:
   - uploads headers + scratch data to `UploadStream`
   - dispatches `multiMemcpy` with `copies = upload.dataUploadPoints.size()`
3. On large bursts, a single tick does too much work and frame hitches.

Even with sync-wait/backpressure heuristics, this still allows high per-tick copy cost when a result set is large.

## Goal
Bound render-thread copy work per tick so worst-case frame cost is predictable.

Target behavior:
- No giant one-shot geometry copy dispatches.
- Geometry copy work is drained incrementally across ticks.
- New results can still arrive without violating state integrity.
- Existing correctness guarantees remain intact (no geometry corruption, no stale metadata writes).

## Non-Goals
- No redesign of `multiMemcpy` shader format.
- No broad rewrite of node lifecycle.
- No changes to quad payload format or mesher semantics in this task.

## Success Criteria
### Functional
- No rendering corruption from partial copy application.
- No crash/assertion due to pending result lifecycle.
- No regression in LOD correctness near camera while draining.

### Performance
- `Large amount of copies` warnings become rare or disappear under typical movement.
- New metric shows bounded per-tick copy count (<= configured budget).
- 1% low spikes attributable to copy bursts are reduced.

### Log-based Acceptance
From `latest.log`, after 2-5 minutes of movement/flying:
- `VOXY_PERF async_node` reports:
  - `max_copy_batch` still may be high (incoming), but
  - new `max_copy_dispatched_per_tick` stays near budget.
- `VOXY_PERF upload_stream` remains healthy (`glfinish_stalls=0`).
- No repeated Voxy `/ERROR` lines tied to async sync lifecycle.

## Relevant Files
Primary:
- `src/main/java/me/cortex/voxy/client/core/rendering/hierachical/AsyncNodeManager.java`
- `src/main/resources/assets/voxy/shaders/util/memcpy.comp`

Secondary (read for interaction context):
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/util/UploadStream.java`
- `src/main/java/me/cortex/voxy/common/world/WorldSection.java`

## Existing Runtime Signals (already in code)
- `VOXY_PERF upload_stream ...`
- `VOXY_PERF world_section_cache ...`
- `VOXY_PERF async_node ...`

Leverage these and extend `VOXY_PERF async_node` for the new feature.

## Design Proposal
### High-level approach: Chunked Geometry Copy Draining
Introduce a render-thread pending drain state for geometry copies.

Instead of dispatching all copies in one tick:
- dispatch only up to `N` copies per tick (`N = copiesPerTickBudget`)
- carry remainder into the next tick
- finalize `SyncResults` lifecycle only when geometry copy remainder reaches zero

#### Core idea
Split one `SyncResults.geometryUpload` into two conceptual parts:
1. `copy work` (drained incrementally)
2. `other result payload` (`tlnDelta`, scatter writes, cleaner ops, counters)

Because scatter writes and cleaner ops logically depend on updated geometry state, process ordering must stay safe (see invariants below).

### Critical invariants
1. A `SyncResults` object must not be returned to cache until its geometry copy work is fully drained and all associated sync operations are applied.
2. Partial copy progress must be monotonic and thread-confined to render thread.
3. No stale pointer usage: upload ranges/headers for each partial dispatch must reflect correct subset.
4. Ordering guarantee:
   - if metadata/scatter writes depend on copied geometry, ensure they happen after relevant copy completion for that result set.

## Implementation Plan

### Phase 1: Introduce budget and pending drain state
In `AsyncNodeManager`:
- Add config knobs:
  - `voxy.asyncGeometryCopiesPerTick` (default proposed: `256`)
  - optional `voxy.asyncGeometryMinCopiesPerTick` / `Max...` for future adaptive mode
- Add render-thread fields:
  - `pendingResult` (`SyncResults` or wrapper)
  - `pendingCopyCursor` (index into copy entries)
  - `pendingMaxCopyDispatchedPerTick` counter
  - optional per-window counters for perf log

Data structure update likely needed in `ComputeMemoryCopy`:
- today it uses `Int2IntOpenHashMap dataUploadPoints` + packed headers in scratch buffer.
- for deterministic chunk iteration, create/maintain a dense ordered list of headers for dispatch (e.g., `IntArrayList headerIndices` or contiguous header slots already implied by `size()`).

### Phase 2: Add partial-dispatch path
Refactor geometry copy section in `tick(...)`:
- If no `pendingResult`, acquire from `RESULT_HANDLE` as before.
- If pending exists, process chunk:
  - `toDispatch = min(remainingCopies, copiesPerTickBudget)`
  - Upload only header range `[cursor, cursor+toDispatch)` and corresponding scratch payload buffer binding.
  - Dispatch compute for `toDispatch` workgroups.
  - Advance cursor.

Important:
- shader expects header index to map directly by `gl_WorkGroupID.x`; if dispatching subset, either:
  1. upload subset headers into temporary contiguous upload header block (recommended), or
  2. add base-offset uniform to shader and index `dataCopyHeader[base + gl_WorkGroupID.x]`.

Option (1) keeps shader unchanged and lowers risk.
Option (2) reduces CPU copy of headers but changes shader interface.

Recommended first implementation:
- keep shader unchanged
- build contiguous temporary header segment for each partial dispatch

### Phase 3: Synchronize remaining result operations safely
Decide ordering semantics clearly:

Recommended conservative ordering:
1. Drain all geometry copies for `pendingResult` across ticks.
2. Only after complete copy drain, apply scatter writes / cleaner ops / callbacks for that result.
3. Recycle `pendingResult`.

This maximizes safety and avoids half-updated node/geometry metadata mismatches.

Tradeoff: non-copy operations for that result are delayed by several ticks when result is huge.
Given current objective (frametime stability), acceptable.

### Phase 4: Metrics and logging extensions
Extend `VOXY_PERF async_node` fields with:
- `pending_copy_remaining`
- `max_copy_dispatched_per_tick`
- `avg_copy_dispatched_per_tick`
- `pending_result_age_ticks` (optional)

Keep log cadence at current interval.

### Phase 5: Threshold retuning
After chunking lands, retune:
- `voxy.asyncGeometrySyncWaitCopies` (currently 320)
- `voxy.asyncGeometryWarnCopies` (currently 500)

Potentially raise warn threshold because per-tick dispatch is now bounded.

## Testing Protocol

### Build checks
- `./gradlew compileJava -q`
- `./scripts/deploy.sh`

### Runtime scenario
1. Restart client with new jar.
2. Join server.
3. Move rapidly/fly to force LOD churn for 3-5 minutes.
4. Collect `latest.log`.

### String search checks
- `VOXY_PERF async_node`
- `Large amount of copies`
- `VOXY_PERF upload_stream`
- `/ERROR] [Voxy/` and `RejectedExecutionException`

### Expected outcomes
- `max_copy_dispatched_per_tick` near budget (not unbounded spikes).
- `Large amount of copies` warning frequency reduced substantially.
- No new Voxy errors.

## Risk Assessment

### Risk 1: Result lifecycle bugs
If pending result recycling is mishandled, could produce leaks/corruption.
Mitigation:
- strict state machine with assertions
- explicit transitions: `ACQUIRE -> DRAIN_COPY -> APPLY_OTHER -> RECYCLE`

### Risk 2: Inconsistent metadata visibility
If scatter/cleaner runs before corresponding geometry copy completion.
Mitigation:
- conservative ordering (delay scatter/cleaner until copy drain done)

### Risk 3: Throughput drop too far
Too low copy budget may cause visibly slow LOD catch-up.
Mitigation:
- configurable budget
- optional adaptive budget in follow-up

## Rollback Plan
If instability occurs:
- gate chunked path behind flag (recommended while implementing):
  - `voxy.asyncGeometryChunkedCopy=true` default true
- fallback to prior single-dispatch behavior by toggling flag false.

## Suggested Commit Sequence
1. Refactor-only commit: introduce pending state scaffolding + no behavior change.
2. Behavior commit: chunked copy dispatch path enabled.
3. Metrics commit: expanded `VOXY_PERF async_node` fields and docs.
4. Tune commit: budget defaults adjusted from test feedback.

## Open Questions for Implementer
1. Keep shader unchanged with CPU header slicing, or add base-offset uniform?
2. Apply scatter/cleaner only after full copy drain (recommended) or partially interleave?
3. Should copy budget be static only in this task, or include adaptive mode now?

## Current Observations Snapshot (from latest reboot run)
- Upload stream healthy:
  - `glfinish_stalls=0`
  - `backpressure_observations=0`
- Async node metrics active:
  - `max_copy_batch` observed up to `757`
- Warning still present:
  - `Large amount of copies, lag will probably happen: 757`
- Voxy hard error from earlier run (`RejectedExecutionException` during shutdown race) did not recur in reboot session.

## Definition of Done
- Chunked copy drain implemented with configurable budget.
- No render corruption or Voxy errors in 5-minute stress movement test.
- Log evidence demonstrates bounded per-tick copy dispatch and fewer large-copy warnings.
- `PLAN.md` checklist items updated or replaced by implementation notes for next handoff.
