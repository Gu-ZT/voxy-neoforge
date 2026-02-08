# Shader Packs + “Indistinguishable” LOD Shading (Voxy NeoForge)

This document summarizes the current state of Voxy’s shader-pack integration and what needs to change to make LODs blend in as closely as possible with shader-pack-rendered vanilla terrain.

This file intentionally includes **cross-repo source references**. If a mechanic is mentioned here, its upstream/reference code should be available under `./.reference/` or `~/Reference/`.

## START HERE (Fresh Agent Bootstrap)

This section is required context for a fresh agent to start making changes.

### 0) One-command sanity check (must pass)

Run this from repo root:

```bash
cd /Users/shelfwood/Projects/voxy-neoforge

tree -L 2 .reference --gitignore
tree -L 2 "$HOME/Reference" --gitignore

test -f SHADERPACKS_AND_LOD_SHADING.md
```

If any pinned repo below is missing, (re)clone it using the **Clone Commands** section.

### 1) Source-of-truth rule

- **Voxy behavior is defined by this repo** (`src/main/java`, `src/main/resources`).
- `.reference/*` exists to answer “what does Iris/DH expect?” and to provide upstream semantics.
- If the repo code and a reference repo disagree, treat the reference repo as guidance and the repo code as truth.

### 2) Critical version mismatch warning (Iris)

The installed Iris NeoForge jar in this workspace reports:

- `./.reference/iris/iris-neoforge.jar` → `version = "1.8.12-snapshot+mc1.21.1-local"` (from `META-INF/neoforge.mods.toml`).

That string does **not** correspond to a known upstream Git tag in `IrisShaders/Iris` (at time of writing), so:

- Use `./.reference/iris-1.7.3+1.21/` **for documented DH semantics and example implementation**.
- When you need to match Iris internals precisely, you must additionally locate the *exact* Iris source commit that produced the local jar (or select a nearby upstream branch by MC version and validate differences manually).

Do not assume `./.reference/iris-repo/` matches the jar; it is currently on a newer branch and is not pinned to the jar’s build provenance.

### 3) “Indistinguishable” definition (what you’re optimizing for)

Treat this as the acceptance criteria for “perfect” LOD shading under shader packs:

- Same gbuffer outputs as shaderpack terrain (color/normal/aux buffers) for both opaque and translucent LODs.
- Correct depth semantics (including DH depth textures if using DH-mode), so SSAO/SSR/fog/TAA don’t diverge at the seam.
- Correct temporal behavior (jitter offsets + history stability) with no shimmer seam.
- Shadow-pass participation when the pack expects it (DH `dh_shadow` behavior).

### 4) Primary entrypoints in Voxy (start reading here)

- Patch ABI / shaderpack hook point: `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`
- Shaderpack patch parsing + fallback: `src/main/java/me/cortex/voxy/client/iris/IrisShaderPatch.java`
- Iris framebuffer/render-target wiring: `src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java`
- DH impersonation knobs (defines/uniforms/samplers):
  - `src/main/java/me/cortex/voxy/client/mixin/iris/MixinStandardMacros.java`
  - `src/main/java/me/cortex/voxy/client/iris/VoxyUniforms.java`
  - `src/main/java/me/cortex/voxy/client/iris/VoxySamplers.java`

## Reference Sources (Pinned)

The following reference sources are expected to exist locally for code navigation.

### Iris (shader pack pipeline + DH integration reference)

- **Repo**: `./.reference/iris-1.7.3+1.21/`
- **Remote**: `https://github.com/IrisShaders/Iris.git`
- **Ref**: tag `1.7.3+1.21` (detached)
- **Commit**: `ee97d207bbbc2b0649f48cd0e61b57a8b56f48bd`

Key mechanics in Iris:

- DH program naming/selection: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/shaderpack/loading/ProgramId.java`
- DH sampler names (`dhDepthTex0/1`): `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/samplers/IrisSamplers.java`
- DH uniforms (`dhNearPlane`, `dhFarPlane`, `dhRenderDistance`): `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/uniforms/CommonUniforms.java`
- DH attribute injection (`dhMaterialId`): `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHTransformer.java`
- Iris-side DH bridging glue: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/compat/dh/DHCompatInternal.java`

### ShaderDoc (format + semantics documentation)

- **Repo**: `./.reference/shaderdoc/`
- **Remote**: `https://github.com/IrisShaders/ShaderDoc.git`
- **Branch**: `master`
- **Commit**: `ddf473726710c6504422d192784c1652f8aece98`

Key docs:

- DH interface overview (programs, uniforms, samplers, `dhMaterialId`): `./.reference/shaderdoc/dh-support.md`
- Entity ID uniforms: `./.reference/shaderdoc/uniforms.md`

### Distant Horizons 2.x (save format + data structures reference)

The official DH repo was not discoverable via GitHub owner/name at the time this doc was generated, so this document pins a public fork that contains DH 2.0.1a-era sources.

- **Repo**: `./.reference/distant-horizons-2.0.1a/`
- **Remote**: `https://github.com/PlxelBuilder/Distant-Horizons-2.0.1a-Oculus-shadow-fix.git`
- **Branch**: `master`
- **Commit**: `39725c004da7c93e9f6a2e2181c3c113b5dc6842`

Key mechanics:

- Full-data mapping string separators (`_DH-BSW_`, `_STATE_`): `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/FullDataPointIdMap.java`
- BlockState string format: `./.reference/distant-horizons-2.0.1a/common/src/main/java/com/seibel/distanthorizons/common/wrappers/block/BlockStateWrapper.java`
- Render datapoint bit layout + material id bits: `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/util/RenderDataPointUtil.java`
- DB table naming (DH “full data” storage): `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/sql/FullDataRepo.java`

### Clone Commands (for reproducibility)

```bash
cd /Users/shelfwood/Projects/voxy-neoforge

# Iris source (pinned tag)
git clone --depth 1 --branch "1.7.3+1.21" https://github.com/IrisShaders/Iris.git ".reference/iris-1.7.3+1.21"

# ShaderDoc (pinned by commit at time of writing)
git clone --depth 1 https://github.com/IrisShaders/ShaderDoc.git ".reference/shaderdoc"

# DH 2.x fork (pinned by commit at time of writing)
git clone --depth 1 https://github.com/PlxelBuilder/Distant-Horizons-2.0.1a-Oculus-shadow-fix.git ".reference/distant-horizons-2.0.1a"
```

## Reference Directory Inventory (tree -L 2)

Snapshot generated on **2026-02-04**.

### `./.reference/`

```text
.reference
├── README.md
├── distant-horizons
│   ├── CODEOWNERS
│   ├── LICENSE
│   ├── README.md
│   ├── build.gradle.kts
│   ├── dependencies.gradle
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gtnhShared
│   ├── jitpack.yml
│   ├── repositories.gradle
│   ├── settings.gradle.kts
│   └── src
├── distant-horizons-2.0.1a
│   ├── Dockerfile
│   ├── LICENSE.LESSER.txt
│   ├── LICENSE.txt
│   ├── Readme.md
│   ├── build.gradle
│   ├── buildAll
│   ├── buildAll.bat
│   ├── code_of_conduct.md
│   ├── common
│   ├── compile.sh
│   ├── contributing.md
│   ├── coreSubProjects
│   ├── fabric
│   ├── forge
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── license_header.txt
│   ├── settings.gradle
│   └── versionProperties
├── embeddium
│   ├── CONTRIBUTING.md
│   ├── COPYING
│   ├── COPYING.LESSER
│   ├── Jenkinsfile
│   ├── README.md
│   ├── build.gradle.kts
│   ├── buildSrc
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── idea
│   ├── scripts
│   ├── settings.gradle.kts
│   └── src
├── fabric-api
├── forgified-fabric-api
│   ├── HEADER
│   ├── LICENSE
│   ├── README.md
│   ├── build.gradle.kts
│   ├── buildSrc
│   ├── deprecated
│   ├── fabric-api-base
│   ├── fabric-api-bom
│   ├── fabric-api-catalog
│   ├── fabric-api-lookup-api-v1
│   ├── fabric-biome-api-v1
│   ├── fabric-block-api-v1
│   ├── fabric-block-view-api-v2
│   ├── fabric-blockrenderlayer-v1
│   ├── fabric-client-tags-api-v1
│   ├── fabric-command-api-v2
│   ├── fabric-content-registries-v0
│   ├── fabric-convention-tags-v2
│   ├── fabric-data-attachment-api-v1
│   ├── fabric-data-generation-api-v1
│   ├── fabric-entity-events-v1
│   ├── fabric-events-interaction-v0
│   ├── fabric-game-rule-api-v1
│   ├── fabric-gametest-api-v1
│   ├── fabric-item-api-v1
│   ├── fabric-item-group-api-v1
│   ├── fabric-key-binding-api-v1
│   ├── fabric-lifecycle-events-v1
│   ├── fabric-loot-api-v3
│   ├── fabric-message-api-v1
│   ├── fabric-model-loading-api-v1
│   ├── fabric-networking-api-v1
│   ├── fabric-object-builder-api-v1
│   ├── fabric-particles-v1
│   ├── fabric-recipe-api-v1
│   ├── fabric-registry-sync-v0
│   ├── fabric-renderer-api-v1
│   ├── fabric-renderer-indigo
│   ├── fabric-rendering-fluids-v1
│   ├── fabric-rendering-v1
│   ├── fabric-resource-conditions-api-v1
│   ├── fabric-resource-loader-v0
│   ├── fabric-screen-api-v1
│   ├── fabric-screen-handler-api-v1
│   ├── fabric-sound-api-v1
│   ├── fabric-transfer-api-v1
│   ├── fabric-transitive-access-wideners-v1
│   ├── ffapi.gradle.properties
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── settings.gradle.kts
│   └── src
├── iris
│   └── iris-neoforge.jar
├── iris-1.7.3+1.21
│   ├── DHApi.jar
│   ├── LICENSE
│   ├── LICENSE-DEPENDENCIES
│   ├── README.md
│   ├── build.gradle.kts
│   ├── docs
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── jitpack.yml
│   ├── settings.gradle.kts
│   └── src
├── iris-repo
│   ├── DHApi.jar
│   ├── LICENSE
│   ├── LICENSE-DEPENDENCIES
│   ├── README.md
│   ├── build.gradle.kts
│   ├── common
│   ├── docs
│   ├── fabric
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── jitpack.yml
│   ├── neoforge
│   └── settings.gradle.kts
├── lambdynamiclights
│   ├── CHANGELOG.md
│   ├── HOW_DOES_IT_WORK.md
│   ├── LICENSE
│   ├── LICENSE.OLD
│   ├── README.md
│   ├── api
│   ├── assets
│   ├── build.gradle.kts
│   ├── build_logic
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── metadata
│   ├── settings.gradle.kts
│   └── src
├── minecraft
│   └── 1.21.1
├── neoforge
│   └── 21.1.217
├── nvidium
├── shaderdoc
│   ├── LICENSE
│   ├── README.md
│   ├── const-directives.md
│   ├── dh-support.md
│   ├── directives
│   ├── getting-started
│   ├── iris-features.md
│   ├── passes
│   ├── uniforms.md
│   ├── unsupported-features.md
│   └── vertex-format-extensions.md
├── sodium
│   ├── 0.6.13
│   ├── 0.6.9
│   ├── CONTRIBUTING.md
│   ├── LICENSE.md
│   ├── README.md
│   ├── build.gradle.kts
│   ├── buildSrc
│   ├── common
│   ├── fabric
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── idea
│   ├── neoforge
│   ├── settings.gradle.kts
│   └── thirdparty
├── sodium-options-api
│   ├── CHANGELOG.md
│   ├── CNAME
│   ├── LICENSE
│   ├── README.md
│   ├── banner.png
│   ├── build.gradle.kts
│   ├── gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── logo.png
│   ├── settings.gradle.kts
│   ├── src
│   ├── stonecutter.gradle.kts
│   └── versions
└── voxy-upstream
    ├── LICENSE.md
    ├── README.md
    ├── build.gradle
    ├── gradle
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── init.gradle
    ├── settings.gradle
    └── src
```

### `~/Reference/`

```text
/Users/shelfwood/Reference
├── copilot-cli
│   ├── LICENSE.md
│   ├── README.md
│   └── changelog.md
├── infrastructure
│   ├── coolify
│   ├── laravel-forge-api-v1-reference.md
│   ├── mattermost
│   ├── n8n
│   └── outline
├── tools
│   ├── panel
│   └── zed
├── unity
│   ├── ECS-Network-Racing-Sample
│   ├── EntityComponentSystemSamples
│   ├── Graphics
│   ├── InputSystem
│   ├── NetcodeForGameObjects
│   ├── Unity.Mathematics
│   ├── UnityCsReference
│   └── ready-player-me
└── web
    ├── astro
    ├── flowbite-illustrations
    ├── laravel-docs
    ├── laravel-docs-core
    ├── laravel-docs-essential
    ├── laravel-docs-minimal
    ├── mermaid
    └── mews-api
```

## Scope / Terms

- **LOD rendering**: Voxy’s far terrain rendering (not vanilla chunks).
- **Shader pack**: Iris/OptiFine-format packs loaded through Iris.
- **“Indistinguishable” goal**: Same buffers, same lighting model inputs, same temporal behavior (TAA), same fog, and (if applicable) consistent shadow pass participation.

## Current Implementation (What Exists Today)

### 1) Voxy renders LODs with patchable shaders

- LOD fragment shader: `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`
  - Non-shaderpack path writes `outColour` directly.
  - Shaderpack path compiles with `PATCHED_SHADER` and calls `voxy_emitFragment(VoxyFragmentParameters ...)`.
  - Available inputs to patch code (current ABI):
    - `sampledColour` (already texture-filtered)
    - `tile`, `uv` (atlas UV)
    - `face` (packed face id)
    - `modelId`
    - `lightMap` (encoded 0..1 sample coords for lightmap)
    - `tinting` (vertex tint multiplier)
    - `customId` (model.customId; intended to align with Iris/pack IDs)

### 2) Iris-specific pipeline to render into shaderpack targets

- `src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java`
  - Attaches Iris render-target textures to Voxy framebuffers.
  - Injects extra uniform/SSBO/sampler declarations into Voxy shaders (header concat).
  - Can optionally blit depth back to vanilla (`renderToVanillaDepth` path).

### 3) Shader pack patch data loaded from the pack (voxy.json)

- `src/main/java/me/cortex/voxy/client/iris/IrisShaderPatch.java`
  - Loads `voxy.json` from the shader pack directory.
  - Optional external patch files:
    - `voxy_opaque.glsl`
    - `voxy_translucent.glsl`
    - `voxy_taa.glsl`
  - If missing, Voxy can use a **fallback patch** to avoid “flat/unlit” LODs.

### 4) Shader-pack fog override to keep LODs visible

- `src/main/java/me/cortex/voxy/client/VoxyClientEvents.java`
  - Pushes terrain fog far away (unless disabled via config) to avoid shaderpack fog walls hiding LODs.

### 5) Optional “pretend to be Distant Horizons” mode

- `voxy.impersonateDHShader` system property:
  - Defines `DISTANT_HORIZONS` for shaderpacks: `src/main/java/me/cortex/voxy/client/mixin/iris/MixinStandardMacros.java`
  - Exposes DH-named samplers mapped to Voxy depth textures: `src/main/java/me/cortex/voxy/client/iris/VoxySamplers.java`
  - Exposes DH-named uniforms (currently hardcoded near/far): `src/main/java/me/cortex/voxy/client/iris/VoxyUniforms.java`

## Why “Perfect Match” Still Fails Under Shader Packs

Shader packs often expect a specific distant-terrain interface and semantics that go beyond “write color to gbuffer”:

1) **Shadow-pass participation**
   - Many packs achieve “correctness” by rendering distant terrain during the Iris shadow pass as well (or sampling shadow maps consistently).
   - If Voxy LODs don’t take part in the pack’s shadow path, the result diverges immediately (shadowing, SSAO, GI approximations, etc.).
   - DH shadow program semantics: `./.reference/shaderdoc/dh-support.md`
   - Iris DH shadow integration: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/compat/dh/DHCompatInternal.java`

2) **Pack-specific material systems**
   - For DH programs specifically, shaderpacks use `dhMaterialId` (a “mini-id” set) rather than normal block IDs.
     - Spec: `./.reference/shaderdoc/dh-support.md`
     - Iris implementation/injection: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHTransformer.java`
   - Voxy’s current patch ABI exposes `customId`, but it is not wired to the DH mini-id interface unless you fully implement the DH path or replicate its semantics.

3) **DH shader interface mismatch**
   - Packs that “support Distant Horizons” typically ship `dh_*` programs (e.g. `dh_terrain`, `dh_water`, `dh_shadow`) with their own input expectations.
     - Spec: `./.reference/shaderdoc/dh-support.md`
     - Iris program IDs: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/shaderpack/loading/ProgramId.java`
   - Voxy’s current approach is “patch our GLSL into the pack’s pipeline”, not “render via the pack’s dh programs”.

4) **Temporal correctness**
   - TAA jitter, motion vectors, history buffers, and depth semantics must line up.
   - Voxy already supports a pack-provided TAA offset function (`voxy_taa.glsl`) and can inject it into the vertex shader, but any mismatch in matrices/timing will show up as shimmer/ghosting.
   - DH projection matrices and near/far semantics (non-shadow): `./.reference/shaderdoc/dh-support.md`

## Highest-Impact Strategy Options

### Option A (Best Fidelity): Implement a DH-program backend for Voxy

Goal: If a shader pack provides DH programs, render Voxy LOD geometry through those programs so the pack’s intended distant-terrain shading applies.

Key requirements:

- Detect presence of DH programs in the pack:
  - `dh_terrain.vsh/.fsh`, `dh_water.*`, `dh_shadow.*` (naming depends on Iris’ program ID mapping).
  - Iris naming reference: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/shaderpack/loading/ProgramId.java`
  - Spec reference: `./.reference/shaderdoc/dh-support.md`
- Render integration:
  - Render opaque and translucent LOD passes into the same gbuffer targets Iris expects for DH.
  - Participate in shadow pass when Iris is rendering shadows.
- Data compatibility:
  - Supply the DH-required uniforms/samplers (projection variants, DH depth textures, near clip distance, etc.).
  - Provide block/material IDs in the format DH shaders use for branching.
    - `dhDepthTex0/1` exposure: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/samplers/IrisSamplers.java`
    - `dhNearPlane`/`dhFarPlane`/`dhRenderDistance`: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/uniforms/CommonUniforms.java`
    - `dhMaterialId` attribute: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHTransformer.java`

Expected result:

- Best chance of “indistinguishable” output on packs that already tune their DH path.

Primary risk:

- Iris’ DH pipeline is not a small/clean public API surface; this may require careful integration with Iris internals or compatibility layers.

### Option B (Most Maintainable): Expand the `voxy.json` patch ABI and ship pack templates

Goal: Keep the existing `voxy_emitFragment(...)` hook, but make it powerful enough that pack authors can implement a high-quality LOD path without duplicating huge shaderpack code.

Concrete improvements:

- Expand `VoxyFragmentParameters` to include the common data shaderpacks use:
  - world-space normal (or enough info to reconstruct it)
  - view-space normal
  - linearized depth / world position (or enough to reconstruct)
  - packed material / id values aligned to Iris conventions (not just `customId`)
  - optional motion vector inputs (for TAA)
- Provide a documented “voxy pack support kit”:
  - `voxy.json` examples for common packs
  - reference `voxy_opaque.glsl` / `voxy_translucent.glsl` templates
  - recommended draw buffer layouts for typical gbuffer pipelines

Expected result:

- Very good results for cooperative packs.
- Still limited for packs that rely on full DH shadow behavior unless you also implement a shadow path.

## Fixes Needed Even If You Don’t Implement DH Programs

### 1) Correct DH uniform semantics when impersonating DH

If `voxy.impersonateDHShader=true` is used:

- `dhNearPlane`, `dhFarPlane`, `dhRenderDistance` must be computed like the DH/Iris expectations (not hardcoded).
- Incorrect values break fog, depth linearization, and many pack effects that assume DH’s geometry near/far behavior.

File: `src/main/java/me/cortex/voxy/client/iris/VoxyUniforms.java`

Reference semantics:

- Spec: `./.reference/shaderdoc/dh-support.md`
- Iris impl (where values come from under real DH): `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/uniforms/CommonUniforms.java`
- Iris DH compat computes near/far/render distance: `./.reference/iris-1.7.3+1.21/src/main/java/net/irisshaders/iris/compat/dh/DHCompat.java`

### 2) Shadow awareness

Even without full DH programs, you should make Voxy’s pipeline explicitly aware of the Iris shadow pass and decide:

- Render LODs into shadow pass (if pack expects it), or
- Ensure the LOD shading path does not assume shadow maps that won’t match.

Signals available today:

- Iris shadow activity: `src/main/java/me/cortex/voxy/client/core/util/IrisUtil.java`

## Shader Pack Authoring: `voxy.json` (Current Fields)

`voxy.json` is parsed into `PatchGson` in:

- `src/main/java/me/cortex/voxy/client/iris/IrisShaderPatch.java`

Current notable fields:

- `version` (must match Voxy’s `IrisShaderPatch.VERSION`)
- `opaqueDrawBuffers`, `translucentDrawBuffers` (indices into Iris render targets)
- `uniforms` (ordered list of Iris uniform names to pack into a UBO)
- `samplers` (ordered set/map of sampler names to GLSL sampler types, e.g. `sampler2D`, `sampler2DShadow`)
- `opaquePatchData`, `translucentPatchData` (GLSL text appended by Voxy before compilation)
- `ssbos` (optional SSBO declarations mapped to Iris SSBO indices)
- `blending` (optional per-drawbuffer blend override)
- `taaOffset` (optional GLSL function body for TAA jitter)
- `excludeLodsFromVanillaDepth` (controls whether Voxy writes depth back to vanilla)
- `renderScale` (optional rendering scale factor)
- `useViewportDims` (controls source framebuffer sizing assumptions)

## DH Save/Import Format References (Voxy DHImporter)

Voxy’s DH import pipeline reads DH “full data” SQLite exports. The serialization conventions it depends on are defined in DH 2.x:

- Voxy importer implementation: `src/main/java/me/cortex/voxy/commonImpl/importers/DHImporter.java`
- DH mapping key separators (`_DH-BSW_`, `_STATE_`): `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/FullDataPointIdMap.java`
- DH block-state string serialization (`minecraft:water_STATE_{level:0}`): `./.reference/distant-horizons-2.0.1a/common/src/main/java/com/seibel/distanthorizons/common/wrappers/block/BlockStateWrapper.java`
- DH render datapoint encoding (color/light/material id bits): `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/util/RenderDataPointUtil.java`
- DH DB naming (table name and schema entry points): `./.reference/distant-horizons-2.0.1a/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/sql/FullDataRepo.java`

## Practical Next Steps (Implementation Roadmap)

1) Decide target compatibility mode:
   - “Support DH shaderpacks perfectly” (Option A), or
   - “Support voxy.json well and document it” (Option B), or both.

2) If Option A:
   - Add detection for DH programs inside the active shaderpack.
   - Implement a DH-render backend path that can compile/bind the DH programs and feed them Voxy geometry.
   - Add shadow-pass LOD rendering support.

3) If Option B:
   - Version the `voxy_emitFragment` ABI and extend parameters.
   - Provide a root-level documentation + example `voxy.json` files (and optional patch GLSL) for pack authors.
   - Improve fallback patch outputs (normals/aux buffers) to reduce artifacts on packs without `voxy.json`.

4) Validation checklist:
   - With shaders off: LOD/vanilla transition seamless (color + depth).
   - With shaders on: LODs match vanilla in:
     - fog behavior
     - lighting intensity and face shading
     - normals/gbuffer auxiliary outputs (no “garbage” data)
     - TAA stability (no shimmer seams at the transition boundary)
     - shadow consistency (if enabled)
