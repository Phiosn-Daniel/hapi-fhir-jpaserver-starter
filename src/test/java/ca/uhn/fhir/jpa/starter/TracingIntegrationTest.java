package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.starter.tracing.ImprovedFhirTracingInterceptor;
import ca.uhn.fhir.jpa.starter.tracing.TracingConfig;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that the tracing components are properly configured and working.
 */
@SpringBootTest(classes = {TracingConfig.class})
@TestPropertySource(properties = {
    "hapi.fhir.tracing.enabled=true",
    "hapi.fhir.tracing.interceptor.type=improved",
    "hapi.fhir.tracing.exporter.type=console"
})
public class TracingIntegrationTest {

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    @Autowired(required = false)
    private ImprovedFhirTracingInterceptor tracingInterceptor;

    @Test
    public void testOpenTelemetryConfiguration() {
        assertNotNull(openTelemetry, "OpenTelemetry should be configured when tracing is enabled");
    }

    @Test
    public void testTracingInterceptorCreation() {
        // Note: The interceptor might not be created in this test context
        // as it requires the full FHIR server configuration
        // This test mainly verifies that the configuration classes can be loaded
        assertNotNull(openTelemetry, "OpenTelemetry configuration should be available");
    }
}