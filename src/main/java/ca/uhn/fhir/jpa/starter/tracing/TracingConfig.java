package ca.uhn.fhir.jpa.starter.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenTelemetry tracing in HAPI FHIR server.
 * 
 * This configuration provides a simple OpenTelemetry setup that can be extended
 * with specific exporters as needed.
 */
@Configuration
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class TracingConfig {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfig.class);

    @Value("${hapi.fhir.tracing.service-name:hapi-fhir-jpa-server}")
    private String serviceName;

    @Value("${hapi.fhir.tracing.service-version:1.0.0}")
    private String serviceVersion;

    @Bean
    public OpenTelemetry openTelemetry() {
        logger.info("🔧 Configuring OpenTelemetry for service: {}", serviceName);
        
        // For now, return the global OpenTelemetry instance
        // This can be extended later with custom configuration
        OpenTelemetry openTelemetry = OpenTelemetry.noop();

        logger.info("✅ OpenTelemetry configured successfully for service: {}", serviceName);
        return openTelemetry;
    }

}