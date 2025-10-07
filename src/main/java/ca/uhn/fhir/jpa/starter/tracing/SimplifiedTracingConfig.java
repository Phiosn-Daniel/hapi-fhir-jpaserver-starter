package ca.uhn.fhir.jpa.starter.tracing;

import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simplified tracing configuration that only uses the ImprovedFhirTracingInterceptor
 * without Micrometer dependencies.
 */
@Configuration
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class SimplifiedTracingConfig {

    private static final Logger logger = LoggerFactory.getLogger(SimplifiedTracingConfig.class);

    @Value("${hapi.fhir.tracing.service-name:hapi-fhir-jpa-server}")
    private String serviceName;

    @Value("${hapi.fhir.tracing.service-version:1.0.0}")
    private String serviceVersion;

    @Bean
    public OpenTelemetry openTelemetry() {
        logger.info("🔧 Configuring OpenTelemetry for service: {}", serviceName);
        
        // Return noop OpenTelemetry for simplicity
        OpenTelemetry openTelemetry = OpenTelemetry.noop();

        logger.info("✅ OpenTelemetry configured successfully for service: {}", serviceName);
        return openTelemetry;
    }
    
    /**
     * Create Improved FHIR Tracing Interceptor (simplified version)
     */
    @Bean
    public ImprovedFhirTracingInterceptor improvedFhirTracingInterceptor(OpenTelemetry openTelemetry) {
        logger.info("🚀 Creating Improved FHIR Tracing Interceptor (simplified)");
        return new ImprovedFhirTracingInterceptor(openTelemetry);
    }
}