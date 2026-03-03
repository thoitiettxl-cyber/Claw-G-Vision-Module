# Vision Architecture — Claw-G-Vision-Module

## 1) Objective

Cung cấp **overlay vision + automation control plane** cho Claw-G trên Android theo kiến trúc module LSPosed, ưu tiên deterministic behavior, low-risk rollout, và compatibility với LibXposed API 100.

## 2) High-level Components

```text
ModuleMain
  -> VisionBootstrapHandler
      -> VisionStateCollector (read path)
      -> VisionOverlayController (render path)
      -> VisionActionDispatcher (write path)
      -> VisionTransportAdapter (command I/O)
```

### Component responsibilities

- **VisionStateCollector**
  - Thu thập node tree từ runtime source.
  - Normalize bounds, visibility, focus, action-capability flags.
  - Trả về `VisionSnapshot` immutable.

- **VisionOverlayController**
  - Quản lý lifecycle overlay: `init/show/hide/recover/release`.
  - Render annotations (index, bounds, color).
  - Hỗ trợ `drawingEnabled=false` cho clean capture.

- **VisionActionDispatcher**
  - Nhận command chuẩn hóa (`tap`, `longTap`, `input`, `key`, `scroll`).
  - Validate payload + target bounds.
  - Execute với timeout + structured response.

- **VisionTransportAdapter**
  - Adapter cho local command channel.
  - Mapping request/response envelope thống nhất.
  - Không chứa business logic.

## 3) Package Layout (target)

```text
app/src/main/java/io/github/libxposed/ezxclean/
  vision/
    model/
      VisionNode.kt
      VisionSnapshot.kt
      VisionAction.kt
      VisionResult.kt
    tree/
      VisionStateCollector.kt
      VisionNodeNormalizer.kt
    overlay/
      VisionOverlayController.kt
      VisionOverlayRenderer.kt
      VisionOverlayLifecycle.kt
    action/
      VisionActionDispatcher.kt
      VisionActionGuards.kt
      VisionActionExecutor.kt
    transport/
      VisionTransportAdapter.kt
      VisionEnvelope.kt
  handlers/
    VisionBootstrapHandler.kt
```

## 4) Data Contracts (v1)

### VisionSnapshot
- `timestampMs: Long`
- `packageName: String`
- `windowId: Int?`
- `nodes: List<VisionNode>`
- `focusedNodeId: String?`
- `meta: Map<String, String>`

### VisionNode
- `id: String`
- `index: Int`
- `className: String`
- `text: String?`
- `contentDesc: String?`
- `bounds: RectLike(left, top, right, bottom)`
- `visible: Boolean`
- `enabled: Boolean`
- `clickable: Boolean`
- `editable: Boolean`
- `scrollable: Boolean`
- `depth: Int`

### VisionAction
- `traceId: String`
- `type: tap|longTap|input|key|scroll`
- `target: byIndex|byBounds|global`
- `payload: object`
- `timeoutMs: Int`

### VisionResult
- `status: success|error`
- `traceId: String`
- `result: object?`
- `error: { code, message, details? }?`
- `durationMs: Long`

## 5) Lifecycle

1. `ModuleMain` load package -> scope filter.
2. `VisionBootstrapHandler` init components (lazy).
3. Read path:
   - Collect raw -> normalize -> snapshot cache.
4. Overlay path:
   - show/hide overlay + render from latest snapshot.
5. Write path:
   - transport request -> dispatcher -> executor -> envelope response.
6. Recovery path:
   - overlay attach fail -> retry policy bounded + log.

## 6) Safety/Hardening Rules

- Scope isolation bắt buộc: không hook module self, `android`, packages ngoài allowlist strategy.
- Action guards:
  - reject invalid bounds/index.
  - reject empty payload cho input/key.
  - bounded timeout cho mọi action.
- Overlay guards:
  - max node render count configurable.
  - throttle redraw interval.
- Logging:
  - structured log prefix `Vision:` + traceId.
  - không log sensitive text payload đầy đủ.

## 7) Feature Flags (recommended)

- `vision.enabled`
- `vision.overlay.enabled`
- `vision.actions.enabled`
- `vision.cleanCapture.enabled`
- `vision.debugLogs.enabled`

## 8) Test Matrix (minimum)

- Functional:
  - overlay show/hide.
  - node indexing stable.
  - tap/input/key/scroll success path.
- Stability:
  - repeated toggle overlay 50+ cycles.
  - package switch stress.
- Performance:
  - snapshot build latency budget.
  - overlay redraw budget.
- Security:
  - scope guard pass.
  - action payload validation pass.

## 9) Migration Plan

- Step 1: Introduce new `vision/*` packages (no behavior change).
- Step 2: Add `VisionBootstrapHandler` side-by-side với `CleanHandler` behind flag.
- Step 3: Switch default handler sang vision path.
- Step 4: Remove legacy clean domain code sau khi regression pass.
