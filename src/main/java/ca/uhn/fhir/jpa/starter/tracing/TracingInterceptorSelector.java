package ca.uhn.fhir.jpa.starter.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Configuration to select which tracing interceptor to use.
 * 
 * This allows choosing between:
 * - EnhancedFhirTracingInterceptor: Detailed layer-by-layer analysis with Micrometer metrics
 * - ImprovedFhirTracingInterceptor: Optimized for production use
 */
@Configuration
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class TracingInterceptorSelector {

    private static final Logger logger = LoggerFactory.getLogger(TracingInterceptorSelector.class);

    @Value("${hapi.fhir.tracing.interceptor.type:enhanced}")
    private String interceptorType;

    /**
     * Create Enhanced FHIR Tracing Interceptor (detailed analysis with Micrometer)
     */
    @Bean
    @ConditionalOnProperty(name = "hapi.fhir.tracing.interceptor.type", havingValue = "enhanced", matchIfMissing = true)
    public EnhancedFhirTracingInterceptor enhancedFhirTracingInterceptor(OpenTelemetry openTelemetry, MeterRegistry meterRegistry) {
        logger.info("🔧 Creating Enhanced FHIR Tracing Interceptor for detailed analysis with metrics");
        return new EnhancedFhirTracingInterceptor(openTelemetry, meterRegistry);
    }

    /**
     * Create Improved FHIR Tracing Interceptor (production optimized)
     */
    @Bean
    @ConditionalOnProperty(name = "hapi.fhir.tracing.interceptor.type", havingValue = "improved", matchIfMissing = false)
    public ImprovedFhirTracingInterceptor improvedFhirTracingInterceptor(OpenTelemetry openTelemetry) {
        logger.info("🚀 Creating Improved FHIR Tracing Interceptor for production use");
        return new ImprovedFhirTracingInterceptor(openTelemetry);
    }
}