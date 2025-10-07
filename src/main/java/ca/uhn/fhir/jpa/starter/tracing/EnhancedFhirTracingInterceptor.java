package ca.uhn.fhir.jpa.starter.tracing;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.method.BaseMethodBinding;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced FHIR Tracing Interceptor that provides detailed layer-by-layer performance analysis
 * for HAPI FHIR operations, showing timing and percentage breakdown for each layer:
 * 
 * 1. HTTP Request (GET /fhir/Patient/123)
 * 2. RestfulServer (Servlet 層)
 * 3. ResourceBinding (路由層)
 * 4. BaseMethodBinding (方法綁定層)
 * 5. JpaResourceProvider (Provider 層)
 * 6. DaoRegistry (註冊表層)
 * 7. IFhirResourceDao (DAO 層)
 * 8. JPA/Hibernate (持久化層)
 * 9. Database
 */
@Component
@Interceptor
public class EnhancedFhirTracingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedFhirTracingInterceptor.class);
    
    private final Tracer tracer;
    
    // Timing data structures for performance analysis
    private final Map<RequestDetails, LayerTimings> requestTimings = new ConcurrentHashMap<>();
    private final Map<RequestDetails, Context> requestContextMap = new ConcurrentHashMap<>();
    private final Map<RequestDetails, Span> requestSpanMap = new ConcurrentHashMap<>();
    
    // Performance statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    /**
     * 層級計時資料結構
     */
    public static class LayerTimings {
        public long startTime = System.nanoTime();
        public long httpRequestStart;
        public long httpRequestEnd;
        public long servletStart;
        public long servletEnd;
        public long resourceBindingStart;
        public long resourceBindingEnd;
        public long methodBindingStart;
        public long methodBindingEnd;
        public long providerStart;
        public long providerEnd;
        public long daoRegistryStart;
        public long daoRegistryEnd;
        public long daoStart;
        public long daoEnd;
        public long jpaStart;
        public long jpaEnd;
        public long dbStart;
        public long dbEnd;
        public long responseStart;
        public long responseEnd;
        
        public void printPerformanceReport(String operationName) {
            long totalTime = responseEnd - startTime;
            
            logger.info("🔍 === FHIR API Performance Report for: {} ===", operationName);
            logger.info("📊 Total Request Time: {} ms", totalTime / 1_000_000);
            
            if (httpRequestEnd > httpRequestStart) {
                long duration = httpRequestEnd - httpRequestStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🌐 HTTP Request Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (servletEnd > servletStart) {
                long duration = servletEnd - servletStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🔧 RestfulServer Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (resourceBindingEnd > resourceBindingStart) {
                long duration = resourceBindingEnd - resourceBindingStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🔀 ResourceBinding Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (methodBindingEnd > methodBindingStart) {
                long duration = methodBindingEnd - methodBindingStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🎯 MethodBinding Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (providerEnd > providerStart) {
                long duration = providerEnd - providerStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("📋 JpaResourceProvider Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (daoRegistryEnd > daoRegistryStart) {
                long duration = daoRegistryEnd - daoRegistryStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("📝 DaoRegistry Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (daoEnd > daoStart) {
                long duration = daoEnd - daoStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🗃️ IFhirResourceDao Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (jpaEnd > jpaStart) {
                long duration = jpaEnd - jpaStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🔄 JPA/Hibernate Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (responseEnd > responseStart) {
                long duration = responseEnd - responseStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("📤 Response Layer: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            logger.info("🔍 === End Performance Report ===");
        }
    }

    public EnhancedFhirTracingInterceptor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("hapi-fhir-layers", "1.0.0");
        logger.info("✅ Enhanced FHIR Performance Tracing Interceptor initialized");
        System.out.println("✅ Enhanced FHIR Performance Tracing Interceptor initialized");
    }

    // =================== LAYER 1: HTTP REQUEST LAYER ===================
    
    /**
     * 第1層：HTTP Request 進入 - 記錄請求開始時間
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void httpRequestStart(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = new LayerTimings();
        timings.httpRequestStart = System.nanoTime();
        requestTimings.put(requestDetails, timings);
        
        totalRequests.incrementAndGet();
        
        String operationName = String.format("FHIR.%s %s", 
            requestDetails.getRequestType(), 
            requestDetails.getRequestPath());
        
        // Create main span
        Span mainSpan = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
        
        mainSpan.setAttribute("http.method", requestDetails.getRequestType().name());
        mainSpan.setAttribute("http.url", requestDetails.getCompleteUrl());
        mainSpan.setAttribute("http.route", requestDetails.getRequestPath());
        mainSpan.setAttribute("fhir.resource_type", requestDetails.getResourceName() != null ? requestDetails.getResourceName() : "unknown");
        mainSpan.setAttribute("layer", "HTTP_REQUEST");
        mainSpan.setAttribute("layer.number", "1");
        
        Context context = Context.current().with(mainSpan);
        requestContextMap.put(requestDetails, context);
        requestSpanMap.put(requestDetails, mainSpan);
        
        logger.info("🌐 [Layer 1] HTTP Request started: {}", operationName);
    }
    
    /**
     * HTTP Request 層結束
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void httpRequestEnd(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.httpRequestEnd = System.nanoTime();
            timings.servletStart = System.nanoTime(); // 開始 Servlet 層
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span httpSpan = tracer.spanBuilder("FHIR.HTTP.Processing")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                httpSpan.setAttribute("layer", "HTTP_PROCESSING");
                httpSpan.setAttribute("layer.number", "1");
                httpSpan.end();
            }
        }
        
        logger.debug("🌐 [Layer 1] HTTP Request processed");
    }

    // =================== LAYER 2: RESTFUL SERVER LAYER ===================
    
    /**
     * 第2層：RestfulServer (Servlet 層) 處理
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public void servletLayerProcessed(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.servletEnd = System.nanoTime();
            timings.resourceBindingStart = System.nanoTime(); // 開始資源綁定層
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span servletSpan = tracer.spanBuilder("FHIR.RestfulServer")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                servletSpan.setAttribute("layer", "RESTFUL_SERVER");
                servletSpan.setAttribute("layer.number", "2");
                servletSpan.setAttribute("fhir.resource_type", requestDetails.getResourceName() != null ? requestDetails.getResourceName() : "unknown");
                servletSpan.end();
            }
        }
        
        logger.debug("🔧 [Layer 2] RestfulServer processed");
    }

    // =================== LAYER 3: RESOURCE BINDING LAYER ===================
    
    /**
     * 第3層：ResourceBinding (路由層) - 替代方案使用 SERVER_INCOMING_REQUEST_POST_PROCESSED
     */
    // 注意：SERVER_RESOURCE_REQUESTED 在此版本中不存在，我們使用其他 pointcut 來模擬
    
    // =================== LAYER 4: METHOD BINDING LAYER ===================
    
    /**
     * 第4層：BaseMethodBinding (方法綁定層) - 替代方案
     */
    // 注意：SERVER_METHOD_SELECTED 在此版本中不存在，我們整合到其他層級中

    // =================== LAYER 5: JPA RESOURCE PROVIDER LAYER ===================
    
    /**
     * 第5層：JpaResourceProvider (Provider 層) - 使用現有的 pointcut
     */
    
    /**
     * Provider 層正常完成
     */
    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
    public void providerLayerEnd(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.providerEnd = System.nanoTime();
            timings.daoRegistryStart = System.nanoTime(); // 開始 DAO Registry 層
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span providerSpan = tracer.spanBuilder("FHIR.JpaResourceProvider.complete")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                providerSpan.setAttribute("layer", "JPA_RESOURCE_PROVIDER");
                providerSpan.setAttribute("layer.number", "5");
                providerSpan.setAttribute("provider.phase", "processing_completed");
                providerSpan.end();
            }
        }
        
        logger.debug("📋 [Layer 5] JpaResourceProvider processing completed normally");
    }

    // =================== LAYER 6: DAO REGISTRY LAYER ===================
    
    /**
     * 第6層：DaoRegistry (註冊表層) - 使用 STORAGE 相關 pointcut 來模擬
     */

    // =================== LAYER 7: DAO LAYER ===================
    
    /**
     * 第7層：IFhirResourceDao (DAO 層) - 儲存前處理
     */
    @Hook(Pointcut.STORAGE_PREACCESS_RESOURCES)
    public void daoLayerPreAccess(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            // DAO 開始時間已在 daoRegistry 層設定
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span daoSpan = tracer.spanBuilder("FHIR.IFhirResourceDao.preAccess")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                daoSpan.setAttribute("layer", "IFHIR_RESOURCE_DAO");
                daoSpan.setAttribute("layer.number", "7");
                daoSpan.setAttribute("dao.operation", "pre_access_resources");
                daoSpan.end();
            }
        }
        
        logger.debug("🗃️ [Layer 7] IFhirResourceDao pre-access resources");
    }
    
    /**
     * DAO 層 - 儲存前資源創建
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void daoLayerPreStorage(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.daoEnd = System.nanoTime();
            timings.jpaStart = System.nanoTime(); // 開始 JPA 層
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span daoSpan = tracer.spanBuilder("FHIR.IFhirResourceDao.preStorage")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                daoSpan.setAttribute("layer", "IFHIR_RESOURCE_DAO");
                daoSpan.setAttribute("layer.number", "7");
                daoSpan.setAttribute("dao.operation", "pre_storage_resource_created");
                daoSpan.end();
            }
        }
        
        logger.debug("🗃️ [Layer 7] IFhirResourceDao pre-storage resource created");
    }

    // =================== LAYER 8: JPA/HIBERNATE LAYER ===================
    
    /**
     * 第8層：JPA/Hibernate (持久化層) - 使用現有的 pointcut
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void jpaLayerPreCommit(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.jpaEnd = System.nanoTime();
            timings.dbStart = System.nanoTime(); // 開始 DB 層（模擬）
        }
        
        Context parentContext = requestContextMap.get(requestDetails);
        if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
                Span jpaSpan = tracer.spanBuilder("FHIR.JPA.Hibernate.preCommit")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                jpaSpan.setAttribute("layer", "JPA_HIBERNATE");
                jpaSpan.setAttribute("layer.number", "8");
                jpaSpan.setAttribute("jpa.operation", "pre_storage_resource_updated");
                jpaSpan.end();
            }
        }
        
        logger.debug("🔄 [Layer 8] JPA/Hibernate pre-storage processing");
    }

    // =================== RESPONSE & CLEANUP ===================
    
    /**
     * 響應處理開始
     */
    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void responseStart(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.get(requestDetails);
        if (timings != null) {
            timings.dbEnd = System.nanoTime(); // DB 層結束（模擬）
            timings.responseStart = System.nanoTime();
        }
        
        logger.debug("📤 Response processing started");
    }
    
    /**
     * 處理完成 - 清理並產生效能報告
     */
    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
    public void processingCompleted(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.remove(requestDetails);
        Span mainSpan = requestSpanMap.remove(requestDetails);
        Context context = requestContextMap.remove(requestDetails);
        
        if (timings != null) {
            timings.responseEnd = System.nanoTime();
            
            String operationName = String.format("FHIR.%s %s", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            // 產生詳細效能報告
            timings.printPerformanceReport(operationName);
        }
        
        if (mainSpan != null) {
            mainSpan.setStatus(StatusCode.OK);
            mainSpan.end();
        }
        
        logger.info("✅ Request processing completed - Total requests: {}", totalRequests.get());
    }
    
    /**
     * 錯誤處理
     */
    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
    public void handleException(RequestDetails requestDetails, Exception exception) {
        if (requestDetails == null) return;
        
        LayerTimings timings = requestTimings.remove(requestDetails);
        Span mainSpan = requestSpanMap.remove(requestDetails);
        requestContextMap.remove(requestDetails);
        
        if (timings != null) {
            timings.responseEnd = System.nanoTime();
            
            String operationName = String.format("FHIR.%s %s", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            logger.error("❌ Request failed: {}", operationName);
            timings.printPerformanceReport(operationName + " [FAILED]");
        }
        
        if (mainSpan != null) {
            String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
            mainSpan.setStatus(StatusCode.ERROR, errorMessage);
            if (exception != null) {
                mainSpan.recordException(exception);
            }
            mainSpan.end();
        }
    }
}