package ca.uhn.fhir.jpa.starter.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import io.opentelemetry.api.OpenTelemetry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Health check component to verify tracing configuration is working
 */
@Component
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class TracingHealthCheck {

    private static final Logger logger = LoggerFactory.getLogger(TracingHealthCheck.class);

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private EnhancedFhirTracingInterceptor enhancedInterceptor;

    @Autowired(required = false)
    private ImprovedFhirTracingInterceptor improvedInterceptor;

    @EventListener(ApplicationReadyEvent.class)
    public void checkTracingHealth() {
        logger.info("🔍 === FHIR Tracing Health Check ===");
        
        if (openTelemetry != null) {
            logger.info("✅ OpenTelemetry: CONFIGURED");
        } else {
            logger.warn("❌ OpenTelemetry: NOT FOUND");
        }
        
        if (meterRegistry != null) {
            logger.info("✅ MeterRegistry: CONFIGURED ({})", meterRegistry.getClass().getSimpleName());
        } else {
            logger.warn("❌ MeterRegistry: NOT FOUND");
        }
        
        if (enhancedInterceptor != null) {
            logger.info("✅ Enhanced FHIR Tracing Interceptor: ACTIVE");
        } else {
            logger.info("ℹ️ Enhanced FHIR Tracing Interceptor: NOT ACTIVE");
        }
        
        if (improvedInterceptor != null) {
            logger.info("✅ Improved FHIR Tracing Interceptor: ACTIVE");
        } else {
            logger.info("ℹ️ Improved FHIR Tracing Interceptor: NOT ACTIVE");
        }
        
        if (enhancedInterceptor == null && improvedInterceptor == null) {
            logger.error("❌ NO TRACING INTERCEPTORS ACTIVE! Check configuration.");
        }
        
        logger.info("🔍 === End Health Check ===");
        logger.info("📋 To test tracing, send a request to: http://localhost:8080/fhir/Patient");
        logger.info("📊 To view metrics, visit: http://localhost:8080/fhir-metrics/summary");
    }
}