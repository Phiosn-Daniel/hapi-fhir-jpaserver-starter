# Data Model: 平行 Bundle 驗證器

**Date**: 2026-02-08  
**Feature**: 001-parallel-bundle-validator

## 核心實體

### 1. ParallelValidationConfig

驗證器的配置模型，對應 `application.yaml` 中的設定。

| 欄位 | 型別 | 預設值 | 說明 |
|------|------|--------|------|
| enabled | Boolean | false | 是否啟用平行驗證 |
| threadPoolSize | Integer | CPU 核心數 | 最大並行執行緒數 |
| entryThreshold | Integer | 10 | 觸發平行驗證的最低 entry 數量 |
| cpuUsageLimit | Double | 0.80 | CPU 使用率上限（0.0~1.0） |
| cpuCheckIntervalMs | Long | 1000 | CPU 使用率檢查間隔（毫秒） |

**來源**: `AppProperties.Validation` inner class 擴展

---

### 2. BundleValidationRequest

單次 Bundle 驗證請求的上下文。

| 欄位 | 型別 | 說明 |
|------|------|------|
| bundle | IBaseBundle | 待驗證的 FHIR Bundle |
| entryCount | int | Bundle 中的 entry 數量 |
| validationMode | enum(SEQUENTIAL, PARALLEL) | 選定的驗證模式 |
| startTimeMs | long | 驗證開始時間戳 |

---

### 3. BundleValidationResult

彙整後的驗證結果。

| 欄位 | 型別 | 說明 |
|------|------|------|
| validationMode | enum(SEQUENTIAL, PARALLEL) | 使用的驗證模式 |
| totalEntries | int | 總 entry 數 |
| passedEntries | int | 通過驗證的 entry 數 |
| failedEntries | int | 驗證失敗的 entry 數 |
| entryResults | List\<EntryValidationResult\> | 每筆 entry 的個別結果 |
| totalDurationMs | long | 總驗證耗時 |
| operationOutcome | OperationOutcome | 彙整的 FHIR OperationOutcome |

---

### 4. EntryValidationResult

單筆 entry 的驗證結果。

| 欄位 | 型別 | 說明 |
|------|------|------|
| entryIndex | int | 在 Bundle 中的位置索引 |
| resourceType | String | 資源類型（如 Patient, Observation） |
| passed | boolean | 是否通過驗證 |
| messages | List\<SingleValidationMessage\> | 驗證訊息清單 |
| durationMs | long | 單筆驗證耗時 |

---

### 5. TestDataset（測試用）

測試資料集的描述結構。

| 欄位 | 型別 | 說明 |
|------|------|------|
| name | String | 資料集名稱（如 "large-bundle"） |
| bundleFile | String | Bundle JSON 檔案路徑 |
| baselineFile | String | 基準結果 JSON 檔案路徑 |
| expectedMode | enum(SEQUENTIAL, PARALLEL) | 預期的驗證模式 |
| expectedEntryCount | int | 預期的 entry 數量 |
| description | String | 情境描述 |

---

## 實體關係

```
ParallelValidationConfig
    ↓ (configures)
BundleValidationRequest
    ↓ (produces)
BundleValidationResult
    ├── entryResults[0]: EntryValidationResult
    ├── entryResults[1]: EntryValidationResult
    └── entryResults[N]: EntryValidationResult

TestDataset → BundleValidationRequest → BundleValidationResult (compare with baseline)
```

## 狀態轉換

### 驗證模式選擇

```
收到 Bundle
  ├── entryCount == 0 → 回傳空結果（不驗證）
  ├── entryCount < threshold → SEQUENTIAL 模式
  └── entryCount >= threshold
        ├── CPU < limit → PARALLEL 模式
        └── CPU >= limit → SEQUENTIAL 模式（降級）
```
