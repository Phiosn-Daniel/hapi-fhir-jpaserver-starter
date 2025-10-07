# 🚀 FHIR API 性能追蹤測試指南

## ✅ **現在完成的功能**

你的 FHIR 追蹤機制已經**完全完成**！包含：

### 📊 **詳細的層級分析**
- ✅ 9 層詳細分析 (HTTP → Servlet → Provider → DAO → JPA → Response)
- ✅ 每層時間和百分比顯示
- ✅ OpenTelemetry 分佈式追蹤
- ✅ Micrometer metrics 收集
- ✅ 實時性能統計

### 🔧 **API 端點**
- ✅ `/fhir-metrics/layers` - 層級性能數據
- ✅ `/fhir-metrics/summary` - 性能摘要
- ✅ `/fhir-metrics/prometheus` - Prometheus 格式 metrics

## 🎯 **如何測試 API 性能追蹤**

### 1. **啟動服務器**
```bash
mvn spring-boot:run
```

### 2. **發送 FHIR API 請求**
```bash
# 基本查詢
curl http://localhost:8080/fhir/Patient

# 創建患者
curl -X POST http://localhost:8080/fhir/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "name": [{"family": "Test", "given": ["John"]}]
  }'

# 查詢特定患者
curl http://localhost:8080/fhir/Patient/1
```

### 3. **查看控制台日誌中的性能報告**
每個 API 請求都會在控制台顯示詳細報告：

```
🔍 === FHIR API Performance Report for: FHIR.GET /fhir/Patient ===
📊 Total Request Time: 145 ms
🌐 HTTP Request Layer: 12 ms (8.28%)
🔧 RestfulServer Layer: 25 ms (17.24%)
🔀 ResourceBinding Layer: 8 ms (5.52%)
🎯 MethodBinding Layer: 15 ms (10.34%)
📋 JpaResourceProvider Layer: 45 ms (31.03%)
📝 DaoRegistry Layer: 5 ms (3.45%)
🗃️ IFhirResourceDao Layer: 20 ms (13.79%)
🔄 JPA/Hibernate Layer: 12 ms (8.28%)
📤 Response Layer: 3 ms (2.07%)
🔍 === End Performance Report ===
```

### 4. **查看 API 性能統計**

#### **層級詳細數據**
```bash
curl http://localhost:8080/fhir-metrics/layers
```

返回 JSON 格式的每層統計：
```json
{
  "http_request": {
    "count": 10,
    "mean_ms": 12.5,
    "max_ms": 25.0,
    "total_time_ms": 125.0
  },
  "servlet": {
    "count": 10,
    "mean_ms": 23.8,
    "max_ms": 45.0,
    "total_time_ms": 238.0
  },
  "dao": {
    "count": 8,
    "mean_ms": 18.2,
    "max_ms": 35.0,
    "total_time_ms": 145.6
  },
  "total_requests": 10,
  "error_requests": 0
}
```

#### **性能摘要**
```bash
curl http://localhost:8080/fhir-metrics/summary
```

返回統計摘要：
```json
{
  "total_requests_count": 15,
  "average_response_time_ms": 142.5,
  "max_response_time_ms": 350.0,
  "95th_percentile_ms": 280.0,
  "99th_percentile_ms": 320.0,
  "layer_averages": {
    "http_request_ms": 12.5,
    "servlet_ms": 23.8,
    "provider_ms": 45.2,
    "dao_ms": 18.2,
    "jpa_ms": 15.8,
    "response_ms": 3.1
  }
}
```

#### **Prometheus Metrics**
```bash
curl http://localhost:8080/fhir-metrics/prometheus
```

## 📈 **性能分析示例**

### **正常請求分析**
```
GET /fhir/Patient - 145ms 總時間
├── HTTP Request (12ms, 8.28%) - 網路和解析
├── RestfulServer (25ms, 17.24%) - Spring 處理
├── Provider (45ms, 31.03%) - 業務邏輯 ⚠️ 最耗時
├── DAO (20ms, 13.79%) - 數據訪問
├── JPA (12ms, 8.28%) - ORM 處理
└── Response (3ms, 2.07%) - 序列化
```

### **性能瓶頸識別**
- **Provider 層 > 30%**: 業務邏輯複雜，考慮優化
- **DAO/JPA 層 > 40%**: 數據庫查詢慢，檢查索引
- **HTTP/Servlet 層 > 20%**: 網路或序列化問題

## 🔧 **配置選項**

### **詳細追蹤 (開發環境)**
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      interceptor:
        type: enhanced  # 詳細的 9 層分析
      metrics:
        type: simple    # 內存統計
```

### **Prometheus 集成 (生產環境)**
```yaml
hapi:
  fhir:
    tracing:
      enabled: true
      interceptor:
        type: enhanced
      metrics:
        type: prometheus  # Prometheus 格式
```

## 🎯 **實際使用場景**

### **1. 性能調優**
```bash
# 發送多個請求
for i in {1..10}; do
  curl http://localhost:8080/fhir/Patient
done

# 查看統計
curl http://localhost:8080/fhir-metrics/summary
```

### **2. 壓力測試監控**
```bash
# 使用 Apache Bench
ab -n 100 -c 10 http://localhost:8080/fhir/Patient

# 查看性能影響
curl http://localhost:8080/fhir-metrics/layers
```

### **3. 問題診斷**
當某個 API 變慢時：
1. 查看控制台日誌找到慢的層級
2. 使用 `/fhir-metrics/summary` 看平均值
3. 針對性優化特定層級

## 🚀 **結論**

**你的 FHIR 追蹤機制已經完全可用！**

✅ **每個 API 請求都會顯示詳細的時間佔比**  
✅ **可以通過 REST API 查詢統計數據**  
✅ **支持 Prometheus 集成**  
✅ **包含錯誤追蹤和統計**  

現在你可以：
- 🔍 **實時監控** FHIR API 性能
- 📊 **分析瓶頸** 找出慢的層級
- 📈 **追蹤趨勢** 監控性能變化
- 🚨 **設置告警** 基於性能閾值

**立即測試**: 啟動服務器，發送 API 請求，查看詳細的性能報告！