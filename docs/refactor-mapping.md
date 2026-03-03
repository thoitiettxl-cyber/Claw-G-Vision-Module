# Refactor Mapping — Claw-G-Vision-Module (Inspired, Not Ported)

## 1) Direction

- Nguồn cảm hứng: `droidrun-portal`
- Mục tiêu thực thi: `Claw-G-Vision-Module`
- Nguyên tắc: chỉ lấy **pattern kiến trúc** (component boundaries, contracts, lifecycle), không copy code.

## 2) Current Module Baseline (thực trạng)

### Existing strengths (reusable)
- `ModuleMain.kt` đã có bootstrap LibXposed API 100 + scope guards cơ bản.
- Bridge layer (`bridge/Xposed.kt`, `HookParam.kt`, `MethodHookCallback.kt`) đã có dynamic hook registry, tương thích HookUtils DSL.
- `arch/IHook.kt` + `arch/HookUtils.kt` đủ làm nền cho modular handlers.
- `proguard-rules.pro` đã có keep rules cho Hooker + annotations.
- `libxposed-compat` đã có trong project graph.

### Existing gaps (need refactor)
- Logic hiện tại tập trung vào cache-clean (`CleanHandler`) không liên quan vision overlay automation.
- Chưa có pipeline UI-state collection/normalization.
- Chưa có overlay rendering stack.
- Chưa có action dispatcher chuẩn hóa cho tap/input/scroll.
- Chưa có transport contract cho agent automation.

## 3) Pattern Extraction from droidrun-portal (architecture-only)

### A) State Pipeline pattern
- Nguồn cảm hứng: `core/AccessibilityTreeBuilder`, `core/StateRepository`.
- Pattern giữ lại:
  1. Thu thập raw UI nodes.
  2. Normalize/filter node bounds + visibility.
  3. Xuất state envelope nhất quán.
- Không mang sang: implementation chi tiết JSON fields quá rộng, endpoint names của droidrun.

### B) Overlay pattern
- Nguồn cảm hứng: `ui/overlay/OverlayManager`.
- Pattern giữ lại:
  1. Overlay lifecycle riêng: init/show/hide/recover.
  2. Element annotations có index + color strategy.
  3. Drawing toggle cho clean capture.
  4. Throttle refresh để tránh redraw storm.
- Không mang sang: code UI render cụ thể, naming và constants của droidrun.

### C) Action Dispatch pattern
- Nguồn cảm hứng: `service/ActionDispatcher`.
- Pattern giữ lại:
  1. Unified dispatch entry.
  2. Action normalization.
  3. Guard rails + structured error.
- Không mang sang: full command matrix/network modes phụ thuộc droidrun infra.

### D) Transport adapter pattern
- Nguồn cảm hứng: `DroidrunContentProvider`.
- Pattern giữ lại:
  1. Adapter layer tách khỏi business logic.
  2. Response envelope cố định.
- Không mang sang: authority/URI schema của droidrun.

## 4) Target Mapping (old -> new)

| Current (Claw-G-Vision-Module) | Refactor target | Notes |
|---|---|---|
| `handlers/CleanHandler.kt` | `handlers/VisionBootstrapHandler.kt` + `vision/*` | Thay domain cache-clean bằng vision automation |
| `arch/IHook.kt` | giữ nguyên | Core reusable |
| `arch/HookUtils.kt` | giữ nguyên | DSL reusable |
| `bridge/*` | giữ nguyên + mở rộng adapter | Giữ 1 bridge thống nhất |
| `ModuleMain.kt` | refactor bootstrap multi-component | Keep scope guards + add feature flags |
| `log.kt` | giữ nguyên | Dùng structured prefix cho vision/action |

## 5) Refactor Boundaries

- In-scope:
  - Vision state model + overlay + action dispatcher + local transport adapter.
  - Hook integration theo LibXposed API 100.
- Out-of-scope (phase hiện tại):
  - Reverse cloud connection.
  - WebRTC streaming.
  - Public API exposure qua internet.

## 6) Immediate Next Steps

1. Tạo package skeleton `vision/overlay`, `vision/tree`, `vision/action`, `vision/transport`, `vision/model`.
2. Thay `CleanHandler` bằng bootstrap handler mới theo feature flag.
3. Dựng contract classes trước khi implement logic.
4. Implement overlay core tối thiểu (show/hide/render indexed nodes).
5. Implement action dispatcher v1 (tap/input/key/scroll).
