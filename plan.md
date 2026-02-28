# Voxy Performance + Frustum Correctness Handoff Plan

## Mission
Stabilize camera-movement performance and eliminate FOV-edge culling artefacts in this `voxy-neoforge` fork with a discovery-first, evidence-driven workflow.

## Ground Rules
1. Do not start coding immediately.
2. First complete Discovery Phase and publish findings.
3. Any optimization must include measurement before/after.
4. Prefer architectural reductions in CPU orchestration over threshold-only tuning.

## Current Context Snapshot
1. Known symptoms:
   - Stutter when moving/turning camera.
   - Edge pop-in/culling issues across FOV ranges.
2. Recent work already in branch:
   - Adaptive geometry copy budget in `AsyncNodeManager`.
   - Projection aspect extraction fix in `VoxyRenderSystem`.
3. Logs indicate:
   - Frequent large copy bursts.
   - Rising `sync_wait_events`.
   - Upload stream not memory-starved.

## Phase 1: Discovery (Required Before Any Code Changes)

### 1.1 Local Codebase Discovery
Run systematic searches and map hot-path ownership.

Commands:
```bash
rg -n "AsyncNodeManager|drainGeometryCopies|DownloadStream|UploadStream|GlFence|HierarchicalOcclusionTraverser|computeProjectionMat|extractFovFromProjection|setFrustum|outsideFrustum" src/main/java src/main/resources -S
```

Deliverables:
1. List of hottest methods and their call chains.
2. Explicit map of CPU↔GPU synchronization points.
3. List of per-frame GPU->CPU readbacks and why they exist.

### 1.2 Reference Repository Discovery (`./.reference`)
Read relevant upstream/reference implementations first.

Priority repos/files:
1. `.reference/voxy-upstream`
2. `.reference/distant-horizons`
3. `.reference/embeddium`
4. `.reference/iris-repo` / `.reference/iris-1.7.3+1.21`

Target searches:
```bash
rg -n "frustum|projection|aspect|cull|occlusion|request queue|download|readback|fence|dispatch indirect" .reference/voxy-upstream .reference/distant-horizons .reference/embeddium .reference/iris-repo .reference/iris-1.7.3+1.21 -S
```

Deliverables:
1. What upstream does differently for traversal/culling/control flow.
2. Any existing design that avoids per-frame CPU readback.
3. Any formula-based frustum/aspect handling applicable here.

### 1.3 Runtime Evidence Discovery (`latest.log` over SSH)
Use remote logs as primary evidence.

Reference scripts:
1. `scripts/deploy.sh`
2. `scripts/logs.sh`

Useful log extraction:
```bash
ssh -o ConnectTimeout=8 tesseract "powershell -Command \"Get-Content 'C:\\Users\\shelfwood\\AppData\\Roaming\\PrismLauncher\\instances\\Craftoria\\minecraft\\logs\\latest.log'\"" \
| rg -n "VOXY_PERF async_node|VOXY_PERF upload_stream|Large amount of copies|sync_wait_events|copy_budget|\\[DIAG\\]" -S
```

Deliverables:
1. Time-correlated view of copy bursts vs sync waits.
2. Evidence whether adaptive budget changes helped.
3. Any new regressions introduced by recent patches.

### 1.4 Discovery Report Gate
Before coding, write a short report containing:
1. Root-cause hypothesis ranked by confidence.
2. Why each candidate is CPU-bound, GPU-bound, or sync-bound.
3. Proposed implementation plan with expected impact and risk.

No implementation starts until this gate is complete.

## Phase 2: Implementation Plan (Post-Discovery)

### 2.1 Performance Priority Order
1. Reduce CPU orchestration in movement-hot path.
2. Reduce GPU->CPU readback cadence/volume.
3. Reduce synchronization pressure (`sync_wait_events`) under copy bursts.
4. Keep frustum correctness robust across FOV/aspect/roll conditions.

### 2.2 Candidate Work Items
1. Request queue handling:
   - Replace unconditional per-cycle readback with thresholded/paced polling.
   - Explore persistent GPU queue/ring design.
2. Geometry copy scheduling:
   - Move toward GPU-driven compaction/indirect dispatch where feasible.
3. Fence strategy:
   - Avoid tight polling patterns where completion latency can be amortized.
4. Frustum correctness:
   - Keep projection-derived FOV/aspect path.
   - If edge issue persists, prefer formula/clip-space guard-band, not static world-space padding.

## Phase 3: Validation Protocol

### 3.1 Build Validation
```bash
./gradlew compileJava -x test
```

### 3.2 Runtime Validation
1. Deploy with `scripts/deploy.sh Craftoria`.
2. Run same camera sweep scenarios.
3. Collect:
   - spark profile URL
   - `latest.log` slices around test window

### 3.3 Success Metrics
1. Lower growth rate of `sync_wait_events` during movement.
2. Fewer and smaller `Large amount of copies` bursts.
3. Reduced share/time of:
   - `AsyncNodeManager.tick`
   - `drainGeometryCopies`
   - `DownloadStream.tick`
4. No FOV-edge pop-in regression.

## Reporting Requirements for the Next Agent
1. Provide findings first, changes second.
2. Include file references and concrete log lines.
3. For each patch:
   - Why it helps
   - Measured before/after evidence
   - Any tradeoffs or risk

## Immediate Next Action for Fresh Agent
Execute Phase 1 in full and post the Discovery Report Gate output before any further code edits.
