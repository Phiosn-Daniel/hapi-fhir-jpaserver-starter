# Implementation Plan: 平行 Bundle 驗證器

**Branch**: `daniel_fhirStarter` | **Date**: 2026-02-08 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/001-parallel-bundle-validator/spec.md`

## Summary

擴展現有 HAPI FHIR JPA Server 的平行 Bundle 驗證功能，新增動態門檻偵測、CPU 使用率感知的執行緒池調節、完整的配置選項，以及自動化回歸測試套件。在 `StarterJpaConfig.createConfiguredValidator()` 的基礎上增強，不引入新的外部依賴。

## Technical Context

**Language/Version**: Java 17 (四空格縮排、無萬用字元 import)  
**Primary Dependencies**: Spring Boot 3.x + HAPI FHIR 8.4.0  
**Storage**: PostgreSQL 15 (生產) / H2 (測試)  
**Testing**: JUnit 5 + Surefire/Failsafe + Spring Boot Test  
**Target Platform**: Linux server / Docker container  
**Project Type**: Single project (Maven)  
**Performance Goals**: 50+ entry Bundle 驗證時間縮短 40%，CPU ≤ 80%  
**Constraints**: 不引入新外部依賴、所有配置透過 YAML、不修改 HAPI FHIR 核心版本  
**Scale/Scope**: 支援單一 Bundle 最多 500+ entries、同時 10+ 併發請求

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 檢查項目 | 結果 |
|------|---------|------|
| I. FHIR 標準合規 | 使用 HAPI FHIR 內建 FhirValidator 機制 | ✅ PASS |
| II. 安全優先 | 無敏感資料硬編碼、執行緒池有上限保護 | ✅ PASS |
| III. 組態驅動 | 所有參數透過 application.yaml 配置 | ✅ PASS |
| IV. 測試紀律 | JUnit 5 參數化測試覆蓋所有情境 | ✅ PASS |
| V. 可觀測性 | 整合 ValidationTimerInterceptor + 結構化日誌 | ✅ PASS |
| VI. 簡約原則 | 擴展現有機制、零新依賴、使用 JDK 內建 API | ✅ PASS |

**Gate Result**: ✅ ALL PASSED — 可進入 Phase 0

## Project Structure

### Documentation (this feature)

```text
specs/001-parallel-bundle-validator/
├── spec.md              # 功能規格書
├── plan.md              # 本檔（實作計畫）
├── research.md          # Phase 0 技術調研
├── data-model.md        # Phase 1 資料模型
├── quickstart.md        # 快速啟動指南
├── contracts/           # API 與配置契約
│   ├── validation-config.yaml
│   └── test-dataset-format.md
└── checklists/
    └── requirements.md  # 規格品質清單
```

### Source Code (repository root)

```text
src/main/java/ca/uhn/fhir/jpa/starter/
├── common/
│   ├── StarterJpaConfig.java              # 🔧 修改: createConfiguredValidator() 增強
│   └── validation/                         # 現有驗證攔截器工廠
│       └── (existing files unchanged)
├── interceptors/
│   └── ValidationTimerInterceptor.java     # 現有: 驗證計時攔截器
├── validation/                             # 🆕 新增套件
│   ├── CpuAwareThreadPoolExecutor.java     # 🆕 CPU 感知的執行緒池
│   ├── BundleValidationStrategy.java       # 🆕 驗證模式選擇策略
│   └── ParallelValidationConfig.java       # 🆕 配置模型 (或擴展 AppProperties)
├── AppProperties.java                      # 🔧 修改: 擴展 Validation 內部類別

src/main/resources/
└── application.yaml                        # 🔧 修改: 新增配置項

src/test/java/ca/uhn/fhir/jpa/starter/
├── ParallelBundleValidatorTest.java        # 🆕 參數化驗證測試
├── CpuAwareThreadPoolExecutorTest.java     # 🆕 執行緒池單元測試
└── BundleValidationStrategyTest.java       # 🆕 策略選擇單元測試

src/test/resources/
├── bundles/                                # 🆕 測試 Bundle 資料集
│   ├── empty-bundle.json
│   ├── small-bundle.json
│   ├── threshold-bundle.json
│   ├── large-bundle.json
│   ├── error-bundle.json
│   └── mixed-bundle.json
└── baselines/                              # 🆕 預期驗證結果基準
    ├── empty-bundle-baseline.json
    ├── small-bundle-baseline.json
    ├── threshold-bundle-baseline.json
    ├── large-bundle-baseline.json
    ├── error-bundle-baseline.json
    └── mixed-bundle-baseline.json
```

**Structure Decision**: 採用 Single project 結構。新增 `validation` 套件放置核心邏輯，遵循 `ca.uhn.fhir.jpa.starter` 套件命名慣例。測試資料放在 `src/test/resources/bundles/` 和 `src/test/resources/baselines/`。

## Implementation Phases

### Phase 1: 基礎設施 (Setup)

**修改的檔案**:
- `AppProperties.java`: 擴展 `Validation` 內部類別，新增 `entry_threshold`、`cpu_limit`、`cpu_check_interval_ms` 屬性
- `application.yaml`: 新增對應的配置項

### Phase 2: 核心功能 (P1-P3)

**新增的檔案**:
- `CpuAwareThreadPoolExecutor.java`: 包裝 `ThreadPoolExecutor`，定期檢查 CPU 使用率並動態調整 max pool size
- `BundleValidationStrategy.java`: 根據 entry 數量 + CPU 狀態決定使用 SEQUENTIAL 或 PARALLEL 模式
- `ParallelValidationConfig.java`: 配置模型 Bean

**修改的檔案**:
- `StarterJpaConfig.createConfiguredValidator()`: 整合新的策略與 CPU 感知執行緒池

### Phase 3: 自動化測試 (P4)

**新增的檔案**:
- 6 個測試 Bundle JSON 檔案（`src/test/resources/bundles/`）
- 6 個基準結果 JSON 檔案（`src/test/resources/baselines/`）
- `ParallelBundleValidatorTest.java`: JUnit 5 參數化測試，載入所有測試資料集並比對基準

### Phase 4: 單元測試 (P4 延伸)

**新增的檔案**:
- `CpuAwareThreadPoolExecutorTest.java`: CPU 監控邏輯測試
- `BundleValidationStrategyTest.java`: 模式選擇邏輯測試

### Phase 5: 自動修復與報告 (P5-P6)

**實作方式**: 
- 自動修復透過 Maven Surefire 報告 + AI Agent 迴圈實現（見 [research.md](./research.md)）
- 測試報告由 Surefire 自動產生，搭配自訂的結果摘要腳本

## Dual-Repository Architecture

本功能涉及**兩個專案**的修改：

| 專案 | 路徑 | 修改內容 |
|------|------|---------|
| **hapi-fhir (core)** | `C:\Users\User\hapi-fhir` | 修改 `FhirValidator.doValidate()` 加入門檻判斷、CPU 感知邏輯 |
| **hapi-fhir-jpaserver-starter** | `C:\Users\User\hapi-fhir-jpaserver-starter` | 配置傳遞、ProfileAutoApplier、測試資料集 |

**Core 修改範圍**（`hapi-fhir-base` 模組）：
- `FhirValidator.java`: 新增 `concurrentBundleEntryThreshold` 欄位 + setter，在 `doValidate()` 中根據 entry 數量決定模式
- `FhirValidator.java`: 新增 CPU 感知的 ExecutorService 支援（已可透過 `setExecutorService()` 傳入）

**Starter 修改範圍**：
- `StarterJpaConfig.createConfiguredValidator()`: 傳入門檻值到 core 的 `FhirValidator`
- `AppProperties.Validation`: 配置屬性（已完成）
- `ProfileAutoApplier`: TW Core Profile 自動套用（已完成）
- 測試資料集與回歸測試

## Key Technical Decisions

### 1. 為什麼在 Core 修改 FhirValidator 而非在 Starter 繞過？

`FhirValidator.doValidate()` 中的平行/循序決策是 class-level 的靜態旗標（`myConcurrentBundleValidation`），無法在共用實例上 per-request 切換（非 thread-safe）。在 Core 中加入門檻判斷是唯一乾淨、安全的做法。

### 2. 為什麼擴展現有 `createConfiguredValidator()` 而非新建攔截器？

HAPI FHIR 的 `FhirValidator` 已內建完整的平行驗證機制（`validateBundleEntriesConcurrently()`）。在此基礎上增強執行緒池和策略，比在攔截器層面自行解析 Bundle 更簡單、更可靠。

### 2. 為什麼使用 `OperatingSystemMXBean` 而非 Micrometer？

需要即時的 CPU 使用率作為執行緒池決策依據，而非延遲的指標數據。JDK 內建的 MXBean 無需額外依賴，符合 Constitution 的簡約原則。

### 3. 為什麼測試資料集使用靜態 JSON 而非動態產生？

靜態 JSON 可被版本控制追蹤、可人工審閱、可重複使用。動態產生的 Bundle 每次可能不同，不利於建立穩定的基準比對。

## Complexity Tracking

> 無 Constitution 違規需要記錄。

| 項目 | 複雜度評估 | 說明 |
|------|-----------|------|
| CPU 感知執行緒池 | 中等 | 使用 JDK 內建 API，邏輯清晰但需要仔細的並行測試 |
| 測試資料集產生 | 低 | 手動建立符合 FHIR R4 規範的 Bundle JSON |
| 自動修復機制 | 低 | 依賴外部 AI 工具，系統僅負責結構化報告 |
