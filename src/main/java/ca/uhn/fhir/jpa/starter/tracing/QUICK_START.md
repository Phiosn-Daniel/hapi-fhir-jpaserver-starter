# 🚀 Enhanced FHIR Tracing - Quick Start Guide

## 快速啟用 FHIR 性能追蹤

### 1. 啟用追蹤功能
在 `application.yaml` 中設置：
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
```

### 2. 選擇追蹤器類型

#### 開發環境 (詳細分析)
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      interceptor:
        type: enhanced  # 詳細的層級分析
      exporter:
        type: console   # 控制台輸出
```

#### 生產環境 (性能優化)
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      interceptor:
        type: improved  # 生產環境優化
      exporter:
        type: console   # 或 jaeger/otlp
```

### 3. 啟動服務器
```bash
mvn spring-boot:run
```

### 4. 測試追蹤功能
發送 FHIR 請求：
```bash
curl http://localhost:8080/fhir/Patient
```

### 5. 查看追蹤日誌
在控制台中查看性能報告：
```
INFO  - 🔧 Configuring OpenTelemetry for service: hapi-fhir-jpa-server with exporter: console
INFO  - ✅ OpenTelemetry configured successfully for service: hapi-fhir-jpa-server
INFO  - 🚀 Creating Improved FHIR Tracing Interceptor for production use
INFO  - Improved FHIR Performance Tracing Interceptor initialized
```

請求處理時的性能報告：
```
INFO  - FHIR Performance Report for: FHIR GET /fhir/Patient
INFO  - Total Time: 45 ms
INFO  - Request Processing: 12 ms (26.67%)
INFO  - Storage Operations: 28 ms (62.22%)
INFO  - Response Generation: 5 ms (11.11%)
```

## 🔧 配置選項

### 追蹤器類型
- `enhanced`: 詳細的 9 層分析，適合開發調試
- `improved`: 生產環境優化，3 個主要階段分析

### 導出器類型
- `console`: 控制台日誌輸出
- `jaeger`: Jaeger 分佈式追蹤 (需要額外配置)
- `otlp`: OpenTelemetry Protocol (需要額外配置)

### 完整配置示例
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      service-name: my-fhir-server
      service-version: 2.0.0
      interceptor:
        type: improved
      exporter:
        type: console
      jaeger:
        endpoint: http://jaeger:14250
      otlp:
        endpoint: http://otel-collector:4317
```

## 🐛 故障排除

### 1. 沒有看到追蹤日誌
- 確認 `hapi.fhir.tracing.enabled=true`
- 檢查日誌級別是否為 INFO 或更低
- 確認 OpenTelemetry 初始化成功

### 2. 性能影響過大
- 切換到 `improved` 追蹤器
- 設置日誌級別為 WARN 以減少輸出
- 考慮採樣配置

### 3. 編譯錯誤
- 確認所有 OpenTelemetry 依賴已添加
- 檢查 Java 版本 (需要 17+)
- 運行 `mvn clean compile`

## 📊 性能監控

### 關鍵指標
- **Total Time**: 總請求時間
- **Request Processing**: 請求處理時間
- **Storage Operations**: 數據庫操作時間
- **Response Generation**: 響應生成時間

### 優化建議
- 如果 Storage Operations 時間過長，檢查數據庫性能
- 如果 Request Processing 時間過長，檢查業務邏輯
- 如果 Response Generation 時間過長，檢查序列化配置

## 🚀 下一步

1. **集成 Jaeger**: 設置分佈式追蹤
2. **添加自定義屬性**: 擴展追蹤信息
3. **配置採樣**: 在高流量環境中優化性能
4. **設置告警**: 基於性能閾值設置監控告警