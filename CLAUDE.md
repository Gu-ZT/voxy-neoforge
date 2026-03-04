# Voxy NeoForge 1.21.1 Port - Project Context

## Project Overview

NeoForge 1.21.1 port of the Voxy LOD mod (originally Fabric/MCRcortex).

**Target Platform**: NeoForge 21.1.x for Minecraft 1.21.1
**Active branch**: `embeddium-compat` (off `neoforge-1.21.1`)
**Primary test target**: Craftoria modpack on Windows gaming laptop via SSH

---

## Critical Development Rules

### 1. Reference-First Research

**ALWAYS** research cloned repositories in `.reference/` before making changes.

```
.reference/
├── minecraft/1.21.1/decompiled/     # MC 1.21.1 decompiled sources
├── embeddium/                        # Embeddium 1.0.x sources (mc1.21.1)
├── iris-repo/                        # Iris source (common/src/main/java/...)
├── iris-1.7.3+1.21/                  # Iris 1.7.3 tagged source
├── photon-voxy-pack/shaders/         # Photon main voxy shaders (voxy.json, voxy_opaque.glsl, voxy_translucent.glsl)
├── photon-upstream/                  # Photon upstream main branch
├── craftoria-pack-snapshot/          # Complementary r5.6.1 + EuphoriaPatcher 1.7.8 (deployed in Craftoria)
├── euphoria-patcher/                 # EuphoriaPatcher JAR + properties
└── distant-horizons-2.0.1a/          # DH 2.0.1a source (for DH API reference)
```

**Process**:
1. Identify the issue (mixin signature, API change, shader uniform, etc.)
2. Search `.reference/` for actual source code
3. Verify method signatures, class structures, field types
4. Apply fix based on **verified evidence** — no guessing
5. Document evidence in commit/comments

### 2. Validation Before Changes

Before modifying any file:
1. Read the current implementation
2. Find corresponding reference in `.reference/`
3. Verify the proposed change matches the reference
4. `./gradlew build` after changes — build validates all mixin targets
5. Deploy + test

### 3. Deployed Dependency Versions (Craftoria)

| Mod | Version |
|-----|---------|
| Iris | `iris-neoforge-1.8.12+mc1.21.1` (snapshot) |
| Embeddium | `embeddium-1.0.15+mc1.21.1` |
| Monocle | `monocle-mod-file.jar` (0.2.2.ms — wraps Iris for NeoForge) |
| EuphoriaPatcher | `EuphoriaPatcher-1.7.8-r5.6.1-neoforge` |
| Active shader pack | Complementary Reimagined r5.6.1 + EuphoriaPatcher 1.7.8 |
| NeoForge | 21.1.115 |

**Note on Monocle**: Monocle is NOT a separate render mod — it is the NeoForge bootstrap layer that wraps Iris. `iris-neoforge-1.8.12+mc1.21.1.jar` is loaded via Monocle. Monocle's `ShaderTransformer` rewrites Iris shader sources and requires shader packs to use `mc_midTexCoord` (not `iris_MidTex` directly) in vertex shaders.

---

## Key Architecture

### Render Pipeline Stack

```
Minecraft frame
  └── MixinLevelRenderer (minecraft/MixinLevelRenderer)
        ├── createRenderer() → new VoxyRenderSystem(...)
        │     └── IrisCompat.createPipeline()
        │           └── Iris.getPipelineManager().getPipelineNullable()
        │                 ├── IrisRenderingPipeline  → IrisVoxyRenderPipeline
        │                 └── VanillaRenderingPipeline → NormalRenderPipeline
        └── renderLevel hook → VoxyRenderSystem.renderOpaque()
```

### Iris Integration Layer

| Class | Role |
|-------|------|
| `IrisShaderPatch` | Parses `voxy.json` from shader pack; determines compatibility mode |
| `IrisVoxyRenderPipelineData` | Builds actual GL programs from voxy.json directives |
| `VoxySamplers` | Registers Iris samplers (dhDepthTex, etc.) |
| `VoxyUniforms` | Registers Iris uniforms (dhProjection, dhNearPlane, etc.) |
| `IrisUtil` | Reflection-safe static accessors; `CAPTURED_VIEWPORT_PARAMETERS` |
| `MixinIrisRenderingPipeline` | TAIL inject into `IrisRenderingPipeline.<init>`; defers VoxyRenderSystem rebuild via `mc.execute()` |
| `MixinProgramSet` | Reads `voxy.json`, enables DH impersonation when `dhImpersonation=true` |
| `MixinStandardMacros` | Injects `#define DISTANT_HORIZONS` when DH impersonation active |

### Compatibility Modes (IrisShaderPatch)

| Mode | Trigger | Behaviour |
|------|---------|-----------|
| `VOXY_PATCH` | Pack has `shaders/program/voxy.json` | Voxy compiles its own programs from voxy.json directives |
| `DH_NATIVE_CANDIDATE` | Pack has DH programs, no voxy.json | Impersonates DH; uses pack's own DH shaders |
| `DISABLED` / fallback | None of the above | Falls back to `NormalRenderPipeline` |

### voxy.json Fields (IrisShaderPatch.PatchGson)

```json
{
  "dhImpersonation": true,           // Inject #define DISTANT_HORIZONS; register DH uniforms+samplers
  "excludeLodsFromVanillaDepth": true, // Keep Voxy depth out of depthtex0
  "opaqueDrawBuffers": [1],
  "translucentDrawBuffers": [13, 16],
  "blending": { "-1": "..." },       // -1 = all buffers
  "uniforms": ["light_dir", "sun_dir", "moon_dir", ...],
  "samplers": ["noisetex", "colortex4", "depthtex0", ...]
}
```

---

## Shader Pack Compatibility

### Complementary Reimagined r5.6.1 + EuphoriaPatcher 1.7.8 (PRIMARY — Craftoria)

**Status**: Working.

- Has `voxy.json` → `VOXY_PATCH` mode
- `dhImpersonation: true` → `#define DISTANT_HORIZONS` injected
- `excludeLodsFromVanillaDepth: true` → LOD depth kept out of `depthtex0`
- DH fog + cloud occlusion path activates correctly via `DISTANT_HORIZONS` define
- Pack ships as `ComplementaryReimagined_r5.6.1 + EuphoriaPatches_1.7.8` (not a zip, unpacked folder in Craftoria)
- Sources: `.reference/craftoria-pack-snapshot/`
- EuphoriaPatcher injects additional defines into Iris via `IrisModernStandardMacrosMixin` on `net.irisshaders.iris.gl.shader.StandardMacros`

### Photon (main branch, post-voxy-merge)

**Status**: `voxy.json` support present; Iris compilation with Monocle 0.2.2.ms unresolved (throws internal error).

- `voxy-support` branch was merged into `main` (commit ~Feb 2026) — no longer exists separately
- Modrinth v1.2a release (June 2025) predates voxy support — do NOT use
- Packed as `.reference/Photon-main-voxy.zip` / unpacked in `.reference/photon-voxy-pack/`
- Uses `attribute vec2 mc_midTexCoord` (Monocle-compatible) in `gbuffers_all_solid.vsh:43`
- `voxy_opaque.glsl`: writes to `gbuffer_data_0` — albedo(RG), blue+material_mask(B), normal(Z), lightmap(W)
- `voxy_translucent.glsl`: full PBR (diffuse, specular, fog, cloud shadows)
- When Photon fails to compile, Iris shows in-game "internal error" screen; exception is NOT written to `SHADER_DUMP.txt` — it's a Java-level throw inside `IrisRenderingPipeline.<init>`

**Diagnosing Photon compile failures**: Iris errors are NOT in `SHADER_DUMP.txt` unless GLSL compilation succeeds. To capture Java-level exceptions add logging in `MixinIrisRenderingPipeline.voxy$injectPipeline`'s catch block, or wrap `IrisVoxyRenderPipelineData.buildPipeline()` more broadly.

### BSL v10.1.1

**Status**: Tested; needs shadow pass fix.
- Has `voxy.json` with draw buffer config
- Shadow pass (`ShadowRenderer.ACTIVE`) causes Voxy to skip rendering — see Known Bug below
- Sources: `.reference/bsl-from-instance/`, `.reference/bsl-study/`

### Other tested packs

- `.reference/bliss-shader/` — Bliss shader (no voxy.json, DH_NATIVE_CANDIDATE path)
- `.reference/rethinking-voxels/` — Rethinking Voxels
- `.reference/eclipse-shader/` — Eclipse shader
- `.reference/ComplementaryReimagined/` — Standalone Complementary (no EuphoriaPatcher)

---

## Known Bugs

### Shadow Pass Gate (b023c9aa regression)

`VoxyRenderSystem.renderOpaque()` guards on `IrisCompatManager.isShadowActive() && !RENDER_LODS_IN_IRIS_SHADOW_PASS`. With `NormalRenderPipeline` active (Iris not instrumented), `ShadowRenderer.ACTIVE=true` during shadow pass causes the Embeddium CUTOUT hook to bail → no LODs rendered.

**Fix**: Guard should only apply when an `IrisVoxyRenderPipeline` is active. `NormalRenderPipeline` has no alternative render path and should skip the gate.

### LOD Race Condition on World Join (FIXED — mc.execute() deferral)

`MixinIrisRenderingPipeline.voxy$injectPipeline` (TAIL inject, fires inside `IrisRenderingPipeline.<init>`) previously called `shutdownRenderer() + createRenderer()` immediately. At that point `Iris.getPipelineManager().getPipelineNullable()` returned null because the constructor hadn't returned yet. Result: Voxy used `NormalRenderPipeline` until `/voxy reload`.

Fixed by deferring via `mc.execute(() -> {...})` so the rebuild runs on the next Minecraft tick after Iris fully registers the pipeline.

---

## Key Files

### Build & Config
- `build.gradle` — dependencies, build settings
- `gradle.properties` — version numbers
- `src/main/resources/META-INF/neoforge.mods.toml` — mod metadata
- `src/main/resources/META-INF/accesstransformer.cfg` — access transformers
- `src/main/resources/client.voxy.mixins.json` — client mixins
- `src/main/resources/common.voxy.mixins.json` — common mixins

### Core Iris Compat Sources
- `src/main/java/me/cortex/voxy/client/iris/IrisShaderPatch.java` — voxy.json parser; compat mode logic
- `src/main/java/me/cortex/voxy/client/iris/IrisVoxyRenderPipelineData.java` — GL program builder
- `src/main/java/me/cortex/voxy/client/iris/VoxySamplers.java` — sampler registration
- `src/main/java/me/cortex/voxy/client/iris/VoxyUniforms.java` — uniform registration
- `src/main/java/me/cortex/voxy/client/core/IrisUtil.java` — reflection accessors
- `src/main/java/me/cortex/voxy/client/mixin/iris/MixinIrisRenderingPipeline.java` — pipeline lifecycle hooks
- `src/main/java/me/cortex/voxy/client/mixin/iris/MixinProgramSet.java` — voxy.json injection point
- `src/main/java/me/cortex/voxy/client/mixin/iris/MixinStandardMacros.java` — DH define injection

---

## Remote Testing Infrastructure

**SSH host**: `shelfwood@192.168.178.206`

Scripts in `./scripts/`:
- `./scripts/logs.sh [instance] [latest|crash|debug|list]` — fetch logs
- `./scripts/deploy.sh [instance]` — build + deploy JAR
- `./scripts/list_instances.sh` — list Prism Launcher instances

**Available instances**: `1.21.1`, `Craftoria`, `Homestead - A Cozy Survival Experience`, `Packje 1.0.1`, `voxy`

**Default deploy target**: `Craftoria`

**Shader pack location on Windows**:
`C:\Users\shelfwood\AppData\Roaming\PrismLauncher\instances\Craftoria\minecraft\shaderpacks\`

**Log paths on Windows**:
`C:\Users\shelfwood\AppData\Roaming\PrismLauncher\instances\<instance>\minecraft\logs\latest.log`

### Deployment Workflow

```bash
./gradlew build && ./scripts/deploy.sh Craftoria
# Then restart Minecraft on the remote machine and check logs:
./scripts/logs.sh Craftoria latest
./scripts/logs.sh Craftoria debug
```

---

## Iris Mixin Caveats

- All Iris mixins use `remap = false` (Iris uses Fabric mappings internally, not SRG)
- `require = 0` on all Iris injects — Iris changes internal structure frequently; fail-soft
- Constructor injects (`<init>`) can only use `@At("TAIL")`, not HEAD (super() not returned yet)
- `IrisRenderingPipeline` constructor TAIL fires **before** the pipeline manager registers the new pipeline — always defer side-effects via `mc.execute()`
- `SHADER_DUMP.txt` is only written when GLSL compilation itself fails — Java-level exceptions inside `<init>` are NOT captured there

---

## Fabric → NeoForge Porting Notes

1. **Mod metadata**: `fabric.mod.json` → `neoforge.mods.toml`
2. **Access wideners**: `.accesswidener` → `accesstransformer.cfg`
3. **Dependencies**: Must be explicitly declared in `neoforge.mods.toml`
4. **Entrypoints**: Fabric entrypoints don't work on NeoForge
5. **Mixin remapping**: NeoForge uses SRG; Iris/Embeddium mixins need `remap = false`
6. **Forgified Fabric API**: mod ID is `fabric_api`; bundles `forgified-fabric-loader` via JarJar
