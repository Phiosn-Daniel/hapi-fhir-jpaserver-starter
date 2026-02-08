# Quickstart: 平行 Bundle 驗證器

## 前置條件

- Java 17+
- Maven 3.8+
- Docker Desktop（用於 PostgreSQL）

## 1. 啟動資料庫

```bash
docker-compose up -d db
```

## 2. 配置平行驗證

在 `src/main/resources/application.yaml` 中確認以下設定：

```yaml
hapi:
  fhir:
    validation:
      requests_enabled: true
      concurrent_bundle_validation_enabled: true
      concurrent_bundle_validation_thread_pool_size: 0    # 0 = 自動偵測 CPU 核心數
      concurrent_bundle_validation_entry_threshold: 10    # entry >= 10 啟用平行
      concurrent_bundle_validation_cpu_limit: 0.80        # CPU 上限 80%
```

## 3. 啟動伺服器

```bash
mvn spring-boot:run
```

## 4. 測試平行驗證

提交一個包含多筆 entry 的 Bundle 到 `$validate` 端點：

```bash
curl -X POST http://localhost:8080/fhir/Bundle/$validate \
  -H "Content-Type: application/fhir+json" \
  -d @src/test/resources/bundles/large-bundle.json
```

## 5. 執行自動化測試

```bash
mvn test -Dtest=ParallelBundleValidatorTest
```

## 6. 建立測試基準

首次執行或重建基準結果：

```bash
mvn test -Dtest=ParallelBundleValidatorTest -Dbaseline.mode=create
```

## 7. 驗證結果

- 檢查控制台日誌中的 `Concurrent bundle validation enabled with thread pool size:` 訊息
- 觀察 `ValidationTimerInterceptor` 輸出的驗證耗時
- 確認測試報告在 `target/surefire-reports/` 中
