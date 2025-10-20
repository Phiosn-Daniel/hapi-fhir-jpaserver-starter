package ca.uhn.fhir.jpa.starter.tracing;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple FHIR Tracing Interceptor that provides basic performance analysis
 * without external dependencies like OpenTelemetry or Micrometer.
 * 
 * Shows timing breakdown for key phases:
 * 1. Request Processing
 * 2. Storage Operations  
 * 3. Response Generation
 */
@Component
@Interceptor
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class SimpleFhirTracingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleFhirTracingInterceptor.class);
    
    // Request tracking
    private final Map<RequestDetails, RequestTiming> activeRequests = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    /**
     * Simple timing data structure
     */
    private static class RequestTiming {
        final long startTime = System.nanoTime();
        long requestProcessingStart;
        long requestProcessingEnd;
        long storageStart;
        long storageEnd;
        long responseStart;
        long responseEnd;
        
        void printReport(String operationName, Logger logger) {
            long totalTime = responseEnd - startTime;
            
            logger.info("🔍 === FHIR API Performance Report for: {} ===", operationName);
            logger.info("📊 Total Request Time: {} ms", totalTime / 1_000_000);
            
            if (requestProcessingEnd > requestProcessingStart) {
                long duration = requestProcessingEnd - requestProcessingStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🔧 Request Processing: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (storageEnd > storageStart) {
                long duration = storageEnd - storageStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("🗃️ Storage Operations: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            if (responseEnd > responseStart) {
                long duration = responseEnd - responseStart;
                double percentage = (duration * 100.0) / totalTime;
                logger.info("📤 Response Generation: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
            }
            
            logger.info("🔍 === End Performance Report ===");
        }
    }

    public SimpleFhirTracingInterceptor() {
        logger.info("✅ Simple FHIR Performance Tracing Interceptor initialized");
    }

    /**
     * Request starts
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void requestStart(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        totalRequests.incrementAndGet();
        
        RequestTiming timing = new RequestTiming();
        timing.requestProcessingStart = System.nanoTime();
        activeRequests.put(requestDetails, timing);
        
        String operationName = String.format("FHIR %s %s", 
            requestDetails.getRequestType(), 
            requestDetails.getRequestPath());
        
        logger.debug("🌐 Started request: {}", operationName);
    }
    
    /**
     * Request processing phase
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public void requestProcessingEnd(RequestDetails requestDetails) {
        RequestTiming timing = activeRequests.get(requestDetails);
        if (timing != null) {
            timing.requestProcessingEnd = System.nanoTime();
        }
    }

    /**
     * Storage operations
     */
    @Hook(Pointcut.STORAGE_PREACCESS_RESOURCES)
    public void storageStart(RequestDetails requestDetails) {
        RequestTiming timing = activeRequests.get(requestDetails);
        if (timing != null) {
            timing.storageStart = System.nanoTime();
        }
    }
    
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void storageOperation(RequestDetails requestDetails) {
        RequestTiming timing = activeRequests.get(requestDetails);
        if (timing != null && timing.storageEnd == 0) {
            timing.storageEnd = System.nanoTime();
        }
    }

    /**
     * Response generation
     */
    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void responseStart(RequestDetails requestDetails) {
        RequestTiming timing = activeRequests.get(requestDetails);
        if (timing != null) {
            timing.responseStart = System.nanoTime();
        }
    }
    
    /**
     * Request completion
     */
    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
    public void requestCompleted(RequestDetails requestDetails) {
        RequestTiming timing = activeRequests.remove(requestDetails);
        if (timing != null) {
            timing.responseEnd = System.nanoTime();
            completedRequests.incrementAndGet();
            
            String operationName = String.format("FHIR %s %s", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            timing.printReport(operationName, logger);
            
            if (completedRequests.get() % 10 == 0) {
                logger.info("📊 Stats - Total: {}, Completed: {}, Failed: {}", 
                    totalRequests.get(), completedRequests.get(), failedRequests.get());
            }
        }
    }
    
    /**
     * Error handling
     */
    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
    public void requestFailed(RequestDetails requestDetails, Exception exception) {
        RequestTiming timing = activeRequests.remove(requestDetails);
        if (timing != null) {
            timing.responseEnd = System.nanoTime();
            failedRequests.incrementAndGet();
            
            String operationName = String.format("FHIR %s %s [FAILED]", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            timing.printReport(operationName, logger);
            
            logger.warn("❌ Request failed: {} - Error: {}", 
                operationName, exception != null ? exception.getMessage() : "Unknown error");
        }
    }
}