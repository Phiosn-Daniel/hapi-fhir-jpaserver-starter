package ca.uhn.fhir.jpa.starter.tracing;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Improved FHIR Tracing Interceptor with better performance, accuracy, and memory management.
 * 
 * Features:
 * - Accurate timing measurements using available pointcuts
 * - Memory leak prevention with cleanup mechanisms
 * - Configurable performance impact
 * - Better error handling and span management
 * - Focused on actual measurable layers rather than simulated ones
 */
@Component
@Interceptor
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class ImprovedFhirTracingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ImprovedFhirTracingInterceptor.class);
    
    private final Tracer tracer;
    private final boolean detailedLogging;
    
    // Request tracking with cleanup mechanism
    private final Map<RequestDetails, RequestTrace> activeRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "fhir-tracing-cleanup"));
    
    // Performance statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    /**
     * Request trace data structure with automatic cleanup
     */
    private static class RequestTrace {
        final long startTime = System.nanoTime();
        final Span mainSpan;
        final Context context;
        
        // Measured phases
        long requestProcessingStart;
        long requestProcessingEnd;
        long storageStart;
        long storageEnd;
        long responseStart;
        long responseEnd;
        
        // Cleanup tracking
        final long createdAt = System.currentTimeMillis();
        
        RequestTrace(Span mainSpan, Context context) {
            this.mainSpan = mainSpan;
            this.context = context;
        }
        
        boolean isExpired(long maxAgeMs) {
            return (System.currentTimeMillis() - createdAt) > maxAgeMs;
        }
        
        void generateReport(String operationName, Logger logger) {
            long totalTime = responseEnd - startTime;
            
            if (logger.isInfoEnabled()) {
                logger.info("FHIR Performance Report for: {}", operationName);
                logger.info("Total Time: {} ms", totalTime / 1_000_000);
                
                if (requestProcessingEnd > requestProcessingStart) {
                    long duration = requestProcessingEnd - requestProcessingStart;
                    double percentage = (duration * 100.0) / totalTime;
                    logger.info("Request Processing: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
                }
                
                if (storageEnd > storageStart) {
                    long duration = storageEnd - storageStart;
                    double percentage = (duration * 100.0) / totalTime;
                    logger.info("Storage Operations: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
                }
                
                if (responseEnd > responseStart) {
                    long duration = responseEnd - responseStart;
                    double percentage = (duration * 100.0) / totalTime;
                    logger.info("Response Generation: {} ms ({:.2f}%)", duration / 1_000_000, percentage);
                }
            }
        }
    }

    public ImprovedFhirTracingInterceptor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("hapi-fhir-improved", "1.0.0");
        this.detailedLogging = logger.isDebugEnabled();
        
        // Start cleanup task to prevent memory leaks
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRequests, 1, 1, TimeUnit.MINUTES);
        
        logger.info("Improved FHIR Performance Tracing Interceptor initialized");
    }

    /**
     * Request starts - create main span and tracking
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void requestStart(RequestDetails requestDetails) {
        if (requestDetails == null) return;
        
        totalRequests.incrementAndGet();
        
        String operationName = String.format("FHIR %s %s", 
            requestDetails.getRequestType(), 
            requestDetails.getRequestPath());
        
        // Create main span
        Span mainSpan = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
        
        // Add standard attributes
        mainSpan.setAttribute("http.method", requestDetails.getRequestType().name());
        mainSpan.setAttribute("http.url", requestDetails.getCompleteUrl());
        mainSpan.setAttribute("http.route", requestDetails.getRequestPath());
        
        if (requestDetails.getResourceName() != null) {
            mainSpan.setAttribute("fhir.resource_type", requestDetails.getResourceName());
        }
        
        Context context = Context.current().with(mainSpan);
        RequestTrace trace = new RequestTrace(mainSpan, context);
        activeRequests.put(requestDetails, trace);
        
        if (detailedLogging) {
            logger.debug("Started tracing request: {}", operationName);
        }
    }
    
    /**
     * Request processing phase
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void requestProcessingStart(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            trace.requestProcessingStart = System.nanoTime();
            
            try (Scope scope = trace.context.makeCurrent()) {
                Span processingSpan = tracer.spanBuilder("FHIR Request Processing")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                processingSpan.setAttribute("phase", "request_processing");
                processingSpan.end();
            }
        }
    }
    
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public void requestProcessingEnd(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            trace.requestProcessingEnd = System.nanoTime();
        }
    }

    /**
     * Storage operations
     */
    @Hook(Pointcut.STORAGE_PREACCESS_RESOURCES)
    public void storageStart(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            trace.storageStart = System.nanoTime();
            
            try (Scope scope = trace.context.makeCurrent()) {
                Span storageSpan = tracer.spanBuilder("FHIR Storage Access")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                storageSpan.setAttribute("phase", "storage_access");
                storageSpan.end();
            }
        }
    }
    
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void storageOperation(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            try (Scope scope = trace.context.makeCurrent()) {
                Span storageSpan = tracer.spanBuilder("FHIR Storage Operation")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                storageSpan.setAttribute("phase", "storage_operation");
                storageSpan.setAttribute("operation", "resource_created");
                storageSpan.end();
            }
        }
    }
    
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void storageUpdate(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            trace.storageEnd = System.nanoTime();
            
            try (Scope scope = trace.context.makeCurrent()) {
                Span storageSpan = tracer.spanBuilder("FHIR Storage Update")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                storageSpan.setAttribute("phase", "storage_operation");
                storageSpan.setAttribute("operation", "resource_updated");
                storageSpan.end();
            }
        }
    }

    /**
     * Response generation
     */
    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void responseStart(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.get(requestDetails);
        if (trace != null) {
            trace.responseStart = System.nanoTime();
            
            try (Scope scope = trace.context.makeCurrent()) {
                Span responseSpan = tracer.spanBuilder("FHIR Response Generation")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                responseSpan.setAttribute("phase", "response_generation");
                responseSpan.end();
            }
        }
    }
    
    /**
     * Request completion - generate report and cleanup
     */
    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
    public void requestCompleted(RequestDetails requestDetails) {
        RequestTrace trace = activeRequests.remove(requestDetails);
        if (trace != null) {
            trace.responseEnd = System.nanoTime();
            completedRequests.incrementAndGet();
            
            String operationName = String.format("FHIR %s %s", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            // Generate performance report
            trace.generateReport(operationName, logger);
            
            // Complete main span
            trace.mainSpan.setStatus(StatusCode.OK);
            trace.mainSpan.end();
            
            if (detailedLogging) {
                logger.debug("Completed tracing request: {} - Total requests: {}", 
                    operationName, totalRequests.get());
            }
        }
    }
    
    /**
     * Error handling
     */
    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
    public void requestFailed(RequestDetails requestDetails, Exception exception) {
        RequestTrace trace = activeRequests.remove(requestDetails);
        if (trace != null) {
            trace.responseEnd = System.nanoTime();
            failedRequests.incrementAndGet();
            
            String operationName = String.format("FHIR %s %s [FAILED]", 
                requestDetails.getRequestType(), 
                requestDetails.getRequestPath());
            
            // Generate performance report for failed request
            trace.generateReport(operationName, logger);
            
            // Complete main span with error
            String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
            trace.mainSpan.setStatus(StatusCode.ERROR, errorMessage);
            if (exception != null) {
                trace.mainSpan.recordException(exception);
            }
            trace.mainSpan.end();
            
            logger.warn("Failed request: {} - Error: {}", operationName, errorMessage);
        }
    }
    
    /**
     * Cleanup expired requests to prevent memory leaks
     */
    private void cleanupExpiredRequests() {
        long maxAge = 5 * 60 * 1000; // 5 minutes
        int cleaned = 0;
        
        activeRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(maxAge)) {
                // Force complete the span to prevent resource leaks
                RequestTrace trace = entry.getValue();
                trace.mainSpan.setStatus(StatusCode.ERROR, "Request timeout - cleaned up");
                trace.mainSpan.end();
                return true;
            }
            return false;
        });
        
        if (cleaned > 0) {
            logger.warn("Cleaned up {} expired request traces", cleaned);
        }
        
        // Log statistics periodically
        if (logger.isInfoEnabled()) {
            long total = totalRequests.get();
            long completed = completedRequests.get();
            long failed = failedRequests.get();
            long active = activeRequests.size();
            
            if (total % 100 == 0 && total > 0) {
                logger.info("FHIR Tracing Stats - Total: {}, Completed: {}, Failed: {}, Active: {}", 
                    total, completed, failed, active);
            }
        }
    }
    
    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Clean up any remaining traces
        activeRequests.values().forEach(trace -> {
            trace.mainSpan.setStatus(StatusCode.ERROR, "Server shutdown");
            trace.mainSpan.end();
        });
        activeRequests.clear();
        
        logger.info("FHIR Tracing Interceptor shutdown completed");
    }
}