# Research: 平行 Bundle 驗證器

**Date**: 2026-02-08  
**Feature**: 001-parallel-bundle-validator

## 1. 現有平行驗證架構

### Decision: 擴展現有 FhirValidator 機制

**Rationale**: HAPI FHIR 8.4.0 的 `FhirValidator` 已內建 `setConcurrentBundleValidation()` 和 `setExecutorService()` API。專案中 `StarterJpaConfig.createConfiguredValidator()` 已實作基礎版本，使用固定大小的執行緒池。應在此基礎上擴展，而非另起爐灶。

**Alternatives considered**:
- 自行實作 Bundle 解析 + 手動分發到執行緒：過度複雜，且與框架重複
- 使用 Spring `@Async`：與 HAPI FHIR 的驗證流程不匹配，無法控制 Bundle 內部的平行粒度

### 現有實作分析

| 項目 | 現有狀態 | 缺失 |
|------|----------|------|
| `setConcurrentBundleValidation(true)` | ✅ 已啟用 | - |
| `setExecutorService(fixedThreadPool)` | ✅ 固定 20 執行緒 | 無動態調節 |
| `concurrent_bundle_validation_enabled` 配置 | ✅ 已存在 | - |
| `concurrent_bundle_validation_thread_pool_size` 配置 | ✅ 已存在 | - |
| Bundle 大小門檻偵測 | ❌ | 需新增 |
| CPU 使用率監控 | ❌ | 需新增 |
| 動態執行緒池 | ❌ | 需新增 |
| 驗證結果等價性保證 | ✅ 框架內建 | - |

### 關鍵程式碼位置

- **`StarterJpaConfig.createConfiguredValidator()`** (line 616-641): 建立 FhirValidator 並配置平行驗證
- **`StarterJpaConfig.restfulServer()`** (line 463-483): 註冊驗證攔截器
- **`AppProperties.Validation`** (line 952-993): 配置屬性定義
- **`application.yaml`** (line 328-333): 驗證相關配置
- **`ValidationTimerInterceptor`**: 已有的驗證效能監控攔截器

## 2. CPU 監控方案

### Decision: 使用 `java.lang.management.OperatingSystemMXBean`

**Rationale**: JDK 內建，無需額外依賴。`com.sun.management.OperatingSystemMXBean.getProcessCpuLoad()` 提供程序級 CPU 使用率，精度足夠且開銷極小。

**Alternatives considered**:
- OSHI 庫：功能強大但引入額外依賴，違反 Constitution 的簡約原則
- Spring Actuator Metrics：可間接取得但不夠即時，不適合執行緒池動態調節
- Micrometer Gauge：適合暴露指標但不適合作為內部決策依據

### CPU 監控設計

```
OperatingSystemMXBean.getProcessCpuLoad()
  ├── > 80% (上限) → 暫停新增執行緒、縮小池大小
  ├── 50-80% → 維持現狀
  └── < 50% → 可擴大池大小（不超過最大值）
```

- 採樣間隔：每秒一次（避免過度監控）
- 採用移動平均避免瞬時波動觸發誤判

## 3. 動態執行緒池方案

### Decision: 使用 `ThreadPoolExecutor` 搭配 CPU 感知調節

**Rationale**: `Executors.newFixedThreadPool()` 不支援動態調節。改用 `ThreadPoolExecutor` 可設定 core/max pool size，搭配自訂 `RejectedExecutionHandler` 處理過載情境。

**設計重點**:
- Core pool size: `min(bundleEntryCount, cpuCores)`
- Max pool size: 可配置上限（預設 = CPU 核心數 × 2）
- Keep alive: 60 秒（閒置執行緒自動回收）
- 當 CPU > 上限時，動態縮小 max pool size

## 4. 測試資料集方案

### Decision: 使用 JUnit 5 參數化測試 + JSON Bundle 檔案

**Rationale**: 專案已使用 JUnit 5，且 `src/test/resources/r4/` 下已有 Bundle 範例。採用參數化測試搭配預建 Bundle 檔案，可快速擴充測試案例。

**測試資料集規劃**:

| 檔案名稱 | 情境 | Entry 數量 | 預期模式 |
|----------|------|-----------|---------|
| `empty-bundle.json` | 空 Bundle | 0 | 循序（不啟動驗證） |
| `small-bundle.json` | 低於門檻 | 3 | 循序 |
| `threshold-bundle.json` | 剛好觸發 | 10 | 平行 |
| `large-bundle.json` | 大型 Bundle | 50 | 平行 |
| `error-bundle.json` | 含驗證錯誤 | 20 | 平行（部分失敗） |
| `mixed-bundle.json` | 混合資源類型 | 30 | 平行 |

**基準結果**: 以 JSON 格式儲存在 `src/test/resources/baselines/` 目錄，記錄每個 Bundle 的預期驗證結果。

## 5. 自動修復方案

### Decision: 結合 Maven Test + AI Agent 迴圈

**Rationale**: 自動修復依賴 AI 工具（Cursor Agent）的程式碼理解能力。系統負責產生結構化的失敗報告（含失敗測試、錯誤訊息、相關程式碼位置），AI Agent 負責解讀並修復。

**流程設計**:
1. `mvn test -pl . -Dtest=ParallelBundleValidatorTest` 執行測試
2. 解析 Surefire 報告 XML → 產生結構化失敗摘要
3. AI Agent 讀取摘要 → 修改程式碼
4. 重新執行測試 → 迴圈直到通過（最多 3 次）

## 6. 與 Constitution 的合規性

| Constitution 原則 | 合規狀態 | 說明 |
|-------------------|---------|------|
| I. FHIR 標準合規 | ✅ | 使用 HAPI FHIR 內建驗證機制，結果符合 FHIR 規範 |
| II. 安全優先 | ✅ | 無涉及敏感資料處理，執行緒池有上限保護 |
| III. 組態驅動 | ✅ | 所有參數透過 application.yaml 配置 |
| IV. 測試紀律 | ✅ | 含完整的 JUnit 5 測試套件 |
| V. 可觀測性 | ✅ | 整合 ValidationTimerInterceptor + 日誌 |
| VI. 簡約原則 | ✅ | 擴展現有機制，不引入新依賴 |
