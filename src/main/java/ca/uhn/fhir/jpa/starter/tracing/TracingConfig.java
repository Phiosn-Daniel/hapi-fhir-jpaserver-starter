package ca.uhn.fhir.jpa.starter.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenTelemetry tracing in HAPI FHIR server.
 * 
 * This configuration provides a working OpenTelemetry setup with console logging exporter.
 * Can be extended with Jaeger, OTLP, or other exporters as needed.
 */
@Configuration
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class TracingConfig {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfig.class);

    @Value("${hapi.fhir.tracing.service-name:hapi-fhir-jpa-server}")
    private String serviceName;

    @Value("${hapi.fhir.tracing.service-version:1.0.0}")
    private String serviceVersion;

    @Value("${hapi.fhir.tracing.exporter.type:console}")
    private String exporterType;

    @Bean
    public OpenTelemetry openTelemetry() {
        logger.info("🔧 Configuring OpenTelemetry for service: {} with exporter: {}", serviceName, exporterType);
        
        // Create resource with service information
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName,
                AttributeKey.stringKey("service.version"), serviceVersion,
                AttributeKey.stringKey("service.instance.id"), java.util.UUID.randomUUID().toString()
            )));

        // Configure span exporter
        SpanExporter spanExporter = createSpanExporter();
        
        // Build tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();

        // Build OpenTelemetry SDK
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();

        logger.info("✅ OpenTelemetry configured successfully for service: {}", serviceName);
        return openTelemetry;
    }
    
    private SpanExporter createSpanExporter() {
        switch (exporterType.toLowerCase()) {
            case "console":
            case "logging":
                logger.info("🔍 Using console/logging span exporter");
                return LoggingSpanExporter.create();
            
            case "jaeger":
                logger.warn("⚠️ Jaeger exporter requested but not fully configured. Using console exporter instead.");
                logger.info("💡 To use Jaeger: add jaeger exporter dependency and configure endpoint");
                return LoggingSpanExporter.create();
                
            case "otlp":
                logger.warn("⚠️ OTLP exporter requested but not fully configured. Using console exporter instead.");
                logger.info("💡 To use OTLP: add otlp exporter dependency and configure endpoint");
                return LoggingSpanExporter.create();
                
            default:
                logger.warn("⚠️ Unknown exporter type: {}. Using console exporter.", exporterType);
                return LoggingSpanExporter.create();
        }
    }

}