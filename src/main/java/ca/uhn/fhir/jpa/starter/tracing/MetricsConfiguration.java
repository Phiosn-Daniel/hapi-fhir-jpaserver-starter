package ca.uhn.fhir.jpa.starter.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Micrometer metrics registry.
 * Supports both simple (in-memory) and Prometheus registries.
 */
@Configuration
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MetricsConfiguration.class);

    @Value("${hapi.fhir.tracing.metrics.type:simple}")
    private String metricsType;

    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        switch (metricsType.toLowerCase()) {
            case "prometheus":
                logger.info("🔧 Configuring Prometheus MeterRegistry");
                return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            
            case "simple":
            default:
                logger.info("🔧 Configuring Simple MeterRegistry");
                return new SimpleMeterRegistry();
        }
    }
}