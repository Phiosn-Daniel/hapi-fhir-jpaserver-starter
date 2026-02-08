# Test Dataset Contract

## 目錄結構

```
src/test/resources/
├── bundles/                          # 測試 Bundle 資料集
│   ├── empty-bundle.json             # 空 Bundle (0 entries)
│   ├── small-bundle.json             # 小型 Bundle (3 entries, < threshold)
│   ├── threshold-bundle.json         # 門檻 Bundle (10 entries, = threshold)
│   ├── large-bundle.json             # 大型 Bundle (50 entries)
│   ├── error-bundle.json             # 含錯誤 Bundle (20 entries, 部分無效)
│   └── mixed-bundle.json             # 混合類型 Bundle (30 entries)
│
└── baselines/                        # 預期驗證結果基準
    ├── empty-bundle-baseline.json
    ├── small-bundle-baseline.json
    ├── threshold-bundle-baseline.json
    ├── large-bundle-baseline.json
    ├── error-bundle-baseline.json
    └── mixed-bundle-baseline.json
```

## Bundle 檔案格式

每個測試 Bundle 為標準 FHIR R4 Bundle JSON，範例：

```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "id": "test-patient-001",
        "name": [{"family": "Test", "given": ["Patient"]}]
      }
    }
  ]
}
```

## Baseline 檔案格式

每個基準結果為 JSON，記錄預期的驗證結果摘要：

```json
{
  "datasetName": "large-bundle",
  "expectedMode": "PARALLEL",
  "expectedEntryCount": 50,
  "expectedResults": {
    "totalPassed": 50,
    "totalFailed": 0,
    "entries": [
      {
        "index": 0,
        "resourceType": "Patient",
        "expectedPassed": true
      }
    ]
  }
}
```

## 新增測試案例步驟

1. 建立 Bundle JSON 檔案，放入 `src/test/resources/bundles/`
2. 執行「首次建立基準」模式：`mvn test -Dtest=ParallelBundleValidatorTest -Dbaseline.mode=create`
3. 檢查產生的基準檔案（`src/test/resources/baselines/`）
4. 將基準檔案納入版本控制
