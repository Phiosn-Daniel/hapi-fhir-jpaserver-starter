# 🔧 FHIR Tracing 故障排除指南

## 🚨 常見錯誤及解決方案

### 1. **啟動錯誤：Bean 循環依賴**
```
The dependencies of some of the beans in the application context form a cycle
```

**解決方案**：
- ✅ 已修復：移除了 `@Component` 註解衝突
- ✅ 已修復：使用 `@Bean` 明確定義 Bean 名稱

### 2. **啟動錯誤：找不到 MeterRegistry**
```
Parameter 1 of constructor in EnhancedFhirTracingInterceptor required a bean of type 'MeterRegistry'
```

**解決方案**：
```yaml
hapi:
  fhir:
    tracing:
      enabled: true  # 確保啟用
      metrics:
        type: simple  # 或 prometheus
```

### 3. **啟動錯誤：找不到 OpenTelemetry**
```
Parameter 0 of constructor required a bean of type 'OpenTelemetry'
```

**解決方案**：檢查 `TracingConfig.java` 是否正確加載

### 4. **沒有看到追蹤日誌**

**檢查步驟**：
1. 確認配置正確：
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
```

2. 檢查日誌級別：
```yaml
logging:
  level:
    ca.uhn.fhir.jpa.starter.tracing: INFO
```

3. 查看健康檢查日誌：
```
🔍 === FHIR Tracing Health Check ===
✅ OpenTelemetry: CONFIGURED
✅ MeterRegistry: CONFIGURED
✅ Enhanced FHIR Tracing Interceptor: ACTIVE
```

## 🔍 診斷步驟

### 步驟 1：檢查配置
```bash
# 檢查 application.yaml
grep -A 10 "tracing:" src/main/resources/application.yaml
```

### 步驟 2：編譯檢查
```bash
mvn compile -q
```

### 步驟 3：啟動並查看日誌
```bash
mvn spring-boot:run | grep -E "(Tracing|ERROR|WARN)"
```

### 步驟 4：測試基本功能
```bash
# 等服務器啟動後
curl http://localhost:8080/fhir/Patient
curl http://localhost:8080/fhir-metrics/summary
```

## 🛠️ 快速修復

### 最小化配置
如果遇到複雜問題，使用最小化配置：

```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      interceptor:
        type: improved  # 使用簡化版本
      exporter:
        type: console
```

### 禁用追蹤
如果需要暫時禁用：
```yaml
hapi:
  fhir:
    tracing:
      enabled: false
```

## 📋 檢查清單

- [ ] `hapi.fhir.tracing.enabled=true`
- [ ] 選擇了正確的 interceptor type
- [ ] OpenTelemetry 依賴已添加
- [ ] Micrometer 依賴已添加
- [ ] 沒有 Bean 名稱衝突
- [ ] 日誌級別設置正確

## 🆘 如果仍有問題

請提供以下信息：
1. 完整的錯誤堆棧
2. `application.yaml` 中的 tracing 配置
3. 啟動日誌中的相關錯誤信息

## 📞 快速聯繫

如果遇到問題，請分享：
- 具體錯誤信息
- 配置文件內容
- 啟動日誌片段