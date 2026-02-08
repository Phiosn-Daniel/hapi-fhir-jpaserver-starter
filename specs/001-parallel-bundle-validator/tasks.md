# Tasks: 平行 Bundle 驗證器

**Input**: Design documents from `specs/001-parallel-bundle-validator/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 本功能規格書明確要求測試覆蓋（US4），測試任務包含在內。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 擴展現有配置結構，為平行驗證器的所有 User Story 建立基礎

- [x] T001 擴展 `AppProperties.Validation` 內部類別，新增 `entry_threshold`(int, 預設 10)、`cpu_limit`(double, 預設 0.80)、`cpu_check_interval_ms`(long, 預設 1000) 屬性及 getter/setter in `src/main/java/ca/uhn/fhir/jpa/starter/AppProperties.java`
- [x] T002 在 `src/main/resources/application.yaml` 的 `hapi.fhir.validation` 區段新增 `concurrent_bundle_validation_entry_threshold: 10`、`concurrent_bundle_validation_cpu_limit: 0.80`、`concurrent_bundle_validation_cpu_check_interval_ms: 1000` 配置項（含註解說明）
- [ ] T003 在 `application.yaml` 的 `hapi.fhir.implementationguides` 區段新增 TW Core IG 配置（`tw-core` 套件名稱、版本、installMode: STORE_AND_INSTALL），確保 TW Core StructureDefinition 預設載入

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心元件，所有 User Story 都依賴此階段完成

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 [P] 建立 `BundleValidationStrategy.java` in `src/main/java/ca/uhn/fhir/jpa/starter/validation/BundleValidationStrategy.java`，實作 `determineMode(int entryCount, double currentCpuLoad, ParallelValidationConfig config)` 方法，回傳 SEQUENTIAL 或 PARALLEL enum 值
- [x] T005 [P] 建立 `CpuAwareThreadPoolExecutor.java` in `src/main/java/ca/uhn/fhir/jpa/starter/validation/CpuAwareThreadPoolExecutor.java`，使用 `ThreadPoolExecutor` + `OperatingSystemMXBean.getProcessCpuLoad()` 定期檢查 CPU 使用率，動態調整 maximumPoolSize
- [x] T006 [P] 建立 `ParallelValidationConfig.java` in `src/main/java/ca/uhn/fhir/jpa/starter/validation/ParallelValidationConfig.java`，作為 Spring `@ConfigurationProperties` Bean，映射 `AppProperties.Validation` 中的新增屬性
- [x] T007 [P] 建立 `ProfileAutoApplier.java` in `src/main/java/ca/uhn/fhir/jpa/starter/validation/ProfileAutoApplier.java`，實作根據資源類型自動套用對應 TW Core Profile URL 的邏輯（FR-027），提供 `getProfileForResourceType(String resourceType)` 方法
- [x] T008 修改 `StarterJpaConfig.createConfiguredValidator()` in `src/main/java/ca/uhn/fhir/jpa/starter/common/StarterJpaConfig.java`，將固定 `Executors.newFixedThreadPool()` 替換為 `CpuAwareThreadPoolExecutor`，並整合 `BundleValidationStrategy` 的門檻判斷邏輯
- [ ] T009 修改 `StarterJpaConfig.restfulServer()` 驗證區段，整合 `ProfileAutoApplier`，對未宣告 `meta.profile` 的資源自動套用 TW Core Profile 進行驗證
- [x] T010 在 `StarterJpaConfig` 中新增結構化日誌，記錄每次驗證的模式選擇（SEQUENTIAL/PARALLEL）、entry 數量、CPU 使用率、執行緒池大小

**Checkpoint**: Foundation ready — 基礎元件完成，可開始各 User Story 的實作

---

## Phase 3: User Story 1 — 自動平行驗證 (Priority: P1) 🎯 MVP

**Goal**: 系統根據 Bundle entry 數量自動選擇循序或平行驗證模式，並回傳完整的驗證結果

**Independent Test**: 提交不同大小的 Bundle（3 筆、20 筆、100 筆），確認模式選擇正確且驗證結果完整

### Implementation for User Story 1

- [ ] T011 [US1] 在 `BundleValidationStrategy.java` 中實作完整的門檻邏輯：entryCount == 0 回傳 SKIP、entryCount < threshold 回傳 SEQUENTIAL、entryCount >= threshold 回傳 PARALLEL in `src/main/java/ca/uhn/fhir/jpa/starter/validation/BundleValidationStrategy.java`
- [ ] T012 [US1] 驗證 `createConfiguredValidator()` 整合後，提交 50 entry Bundle 確認平行模式啟用，提交 3 entry Bundle 確認循序模式啟用（手動 curl 測試 + 檢查日誌）
- [ ] T013 [US1] 確認 HAPI FHIR `FhirValidator.validateBundleEntriesConcurrently()` 的結果彙整邏輯（OperationOutcome 格式），驗證單筆失敗不影響其他筆的驗證（FR-006）

**Checkpoint**: User Story 1 完成 — 自動平行驗證 MVP 可獨立運作

---

## Phase 4: User Story 2 — CPU 使用率自動管控 (Priority: P2)

**Goal**: 平行驗證執行時自動監控 CPU，超過上限時降級為循序或縮小執行緒池

**Independent Test**: 提交 500+ entry Bundle 並監控 CPU，確認不超過設定上限

### Implementation for User Story 2

- [ ] T014 [US2] 在 `CpuAwareThreadPoolExecutor.java` 中實作 CPU 監控迴圈（ScheduledExecutorService，每秒檢查一次），使用移動平均避免瞬時波動 in `src/main/java/ca/uhn/fhir/jpa/starter/validation/CpuAwareThreadPoolExecutor.java`
- [ ] T015 [US2] 實作動態調節邏輯：CPU > limit 時縮小 maximumPoolSize、CPU < 50% 時恢復到配置上限，記錄每次調節的結構化日誌 in `src/main/java/ca/uhn/fhir/jpa/starter/validation/CpuAwareThreadPoolExecutor.java`
- [ ] T016 [US2] 在 `BundleValidationStrategy.determineMode()` 中加入 CPU 檢查：即使 entryCount >= threshold，若 CPU > limit 則降級為 SEQUENTIAL in `src/main/java/ca/uhn/fhir/jpa/starter/validation/BundleValidationStrategy.java`
- [ ] T017 [US2] 實作 graceful shutdown 邏輯：在 `CpuAwareThreadPoolExecutor` 中實作 `@PreDestroy` 方法，等待正在執行的驗證完成或在 30 秒後強制關閉

**Checkpoint**: User Story 2 完成 — CPU 管控功能可獨立運作

---

## Phase 5: User Story 3 — 平行驗證行為可配置 (Priority: P3)

**Goal**: 管理員可透過 application.yaml 調整所有平行驗證參數

**Independent Test**: 修改 entry_threshold 為 5 後重啟，提交 6 entry Bundle 確認啟用平行模式

### Implementation for User Story 3

- [ ] T018 [US3] 確認 `ParallelValidationConfig` Bean 正確映射 application.yaml 中所有新增配置項（entry_threshold、cpu_limit、cpu_check_interval_ms），並在啟動時以 INFO 日誌輸出當前配置值 in `src/main/java/ca/uhn/fhir/jpa/starter/validation/ParallelValidationConfig.java`
- [ ] T019 [US3] 在 application.yaml 中為每個新增配置項加上完整的中英文註解說明（含預設值、有效範圍、使用範例）in `src/main/resources/application.yaml`
- [ ] T020 [US3] 驗證所有配置的邊界條件：thread_pool_size = 0 時自動偵測 CPU 核心數、entry_threshold = 1 時幾乎永遠平行、cpu_limit = 1.0 時永不降級

**Checkpoint**: User Story 3 完成 — 所有參數可配置並生效

---

## Phase 6: User Story 4 — 預建測試資料集自動驗證 (Priority: P4)

**Goal**: 提供 6+ 種情境的測試 Bundle 資料集，單一指令執行回歸測試

**Independent Test**: 執行 `mvn test -Dtest=ParallelBundleValidatorTest`，所有測試案例通過

### Test Datasets

- [ ] T021 [P] [US4] 建立 `src/test/resources/bundles/empty-bundle.json` — 空 Bundle（0 entries, type=collection）
- [ ] T022 [P] [US4] 建立 `src/test/resources/bundles/small-bundle.json` — 小型 Bundle（3 entries: Patient + Observation + Condition）
- [ ] T023 [P] [US4] 建立 `src/test/resources/bundles/threshold-bundle.json` — 門檻 Bundle（10 entries: 混合資源類型，含 TW Core 合規資源）
- [ ] T024 [P] [US4] 建立 `src/test/resources/bundles/large-bundle.json` — 大型 Bundle（50 entries: 使用 TW Core IG 官方範例資源格式）
- [ ] T025 [P] [US4] 建立 `src/test/resources/bundles/error-bundle.json` — 含驗證錯誤 Bundle（20 entries: 部分資源刻意違反 TW Core Profile 約束）
- [ ] T026 [P] [US4] 建立 `src/test/resources/bundles/mixed-bundle.json` — 混合類型 Bundle（30 entries: Patient/Observation/MedicationRequest/Encounter/Condition 等）
- [ ] T027 [P] [US4] 建立 `src/test/resources/bundles/twcore-positive-bundle.json` — TW Core 正向案例 Bundle（使用 IG 官方範例資源，MUST 100% 通過驗證）
- [ ] T028 [P] [US4] 建立 `src/test/resources/bundles/twcore-negative-bundle.json` — TW Core 負向案例 Bundle（刻意違規資源，MUST 100% 被拒絕）

### Test Implementation

- [ ] T029 [US4] 建立 `ParallelBundleValidatorTest.java` in `src/test/java/ca/uhn/fhir/jpa/starter/ParallelBundleValidatorTest.java`，使用 `@ParameterizedTest` + `@MethodSource` 載入所有 bundles/ 目錄下的 JSON 檔案，對每個執行驗證
- [ ] T030 [US4] 在 `ParallelBundleValidatorTest` 中實作基準比對邏輯：載入 `baselines/` 目錄下的對應基準 JSON，比較 entryCount、validationMode、每筆 entry 的通過/失敗狀態
- [ ] T031 [US4] 在 `ParallelBundleValidatorTest` 中實作「首次建立基準」模式：當系統屬性 `baseline.mode=create` 時，將驗證結果寫入 `baselines/` 目錄而非比對
- [ ] T032 [US4] 執行所有測試並建立初始基準結果（`mvn test -Dtest=ParallelBundleValidatorTest -Dbaseline.mode=create`），將基準 JSON 檔案納入版本控制

**Checkpoint**: User Story 4 完成 — 回歸測試套件可獨立執行

---

## Phase 7: User Story 5 — 自動偵測失敗並啟動修復流程 (Priority: P5)

**Goal**: 測試失敗時自動分析原因並透過 AI Agent 修復，迴圈直到通過或達上限

**Independent Test**: 刻意引入錯誤後執行自動修復腳本，確認能自動修復並重新通過測試

### Implementation for User Story 5

- [ ] T033 [US5] 建立 `scripts/auto-fix.sh`（或 `scripts/auto-fix.ps1` for Windows），實作迴圈邏輯：執行 `mvn test` → 解析 Surefire XML 報告 → 產生結構化失敗摘要 JSON → 呼叫 AI Agent → 重新測試（最多 3 輪）
- [ ] T034 [US5] 建立 `scripts/parse-surefire-report.sh`，解析 `target/surefire-reports/TEST-*.xml`，提取失敗測試名稱、錯誤訊息、堆疊追蹤，輸出為結構化 JSON 摘要
- [ ] T035 [US5] 在 auto-fix 腳本中實作安全機制：修復導致編譯失敗時自動 `git checkout` 回滾、達到最大嘗試次數時輸出完整報告並停止

**Checkpoint**: User Story 5 完成 — 自動修復迴圈可運作

---

## Phase 8: User Story 6 — 測試結果報告與歷史追蹤 (Priority: P6)

**Goal**: 記錄每次測試執行的結果並支援歷史追蹤

**Independent Test**: 連續執行 3 次測試，確認每次結果都被記錄在報告目錄中

### Implementation for User Story 6

- [ ] T036 [US6] 建立 `scripts/generate-report.sh`，從 Surefire 報告和自動修復日誌產生 Markdown 格式的測試執行報告，輸出到 `performance-reports/` 目錄
- [ ] T037 [US6] 報告內容包含：執行時間戳、測試總數/通過/失敗、每個失敗案例的詳情、自動修復嘗試紀錄（若有）、驗證模式統計（SEQUENTIAL vs PARALLEL 比例）
- [ ] T038 [US6] 在 auto-fix 腳本（T033）結尾整合報告產生，確保每次執行（無論成功或失敗）都自動產生報告

**Checkpoint**: User Story 6 完成 — 測試報告自動產生且可追溯

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 跨越多個 User Story 的改善

- [ ] T039 [P] 建立 `BundleValidationStrategyTest.java` in `src/test/java/ca/uhn/fhir/jpa/starter/BundleValidationStrategyTest.java`，測試所有模式選擇邊界條件（0 entries、threshold-1、threshold、CPU 過載降級）
- [ ] T040 [P] 建立 `CpuAwareThreadPoolExecutorTest.java` in `src/test/java/ca/uhn/fhir/jpa/starter/CpuAwareThreadPoolExecutorTest.java`，測試動態調節邏輯、graceful shutdown、移動平均計算
- [ ] T041 [P] 建立 `ProfileAutoApplierTest.java` in `src/test/java/ca/uhn/fhir/jpa/starter/ProfileAutoApplierTest.java`，測試各資源類型的 Profile 自動套用（Patient→TW Core Patient、Observation→TW Core Observation 等）
- [ ] T042 程式碼清理：確保所有新增類別遵循專案命名慣例（`*Config`、`*Strategy`、`*Service`）、四空格縮排、無萬用字元 import
- [ ] T043 執行 `mvn verify` 確認所有測試通過（含新增與既有測試），修復任何回歸問題
- [ ] T044 執行 quickstart.md 驗證流程，確認文件中的所有指令可正常執行

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — MVP milestone
- **US2 (Phase 4)**: Depends on Phase 2 — can run in parallel with US1
- **US3 (Phase 5)**: Depends on Phase 2 — can run in parallel with US1/US2
- **US4 (Phase 6)**: Depends on US1+US2+US3 completion — needs working validation to generate baselines
- **US5 (Phase 7)**: Depends on US4 — needs test suite to drive auto-fix loop
- **US6 (Phase 8)**: Depends on US4 — needs test results to generate reports
- **Polish (Phase 9)**: Depends on all user stories

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — No dependencies on other stories
- **US2 (P2)**: Can start after Phase 2 — Independent of US1
- **US3 (P3)**: Can start after Phase 2 — Independent of US1/US2
- **US4 (P4)**: Depends on US1+US2+US3 — Needs working validator for baseline generation
- **US5 (P5)**: Depends on US4 — Needs test suite
- **US6 (P6)**: Depends on US4 — Needs test execution data

### Parallel Opportunities

- T004, T005, T006, T007 (Phase 2 foundational components) can run in parallel
- US1, US2, US3 can run in parallel after Phase 2
- T021–T028 (test Bundle datasets) can ALL run in parallel
- T039, T040, T041 (unit tests in Polish phase) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 2: Foundational (T004–T010)
3. Complete Phase 3: User Story 1 (T011–T013)
4. **STOP and VALIDATE**: 提交不同大小的 Bundle，確認模式選擇正確
5. This is the minimum deployable increment

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → 自動平行驗證 MVP → Validate
3. US2 → CPU 管控 → Validate
4. US3 → 可配置 → Validate
5. US4 → 回歸測試套件 → Validate（建立基準）
6. US5 → 自動修復 → Validate
7. US6 → 測試報告 → Validate
8. Polish → 單元測試 + 清理 → Final Validate

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (平行驗證核心)
   - Developer B: US2 (CPU 管控)
   - Developer C: US3 (配置) + US4 (測試資料集準備，同時進行)
3. US4 integration after US1+US2+US3 merge
4. US5 + US6 sequentially after US4

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- TW Core IG 官方範例資源用於 SC-012 正向/負向驗證完整度測試
