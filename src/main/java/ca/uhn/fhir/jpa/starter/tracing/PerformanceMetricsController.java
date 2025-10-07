package ca.uhn.fhir.jpa.starter.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller to expose FHIR performance metrics.
 */
@RestController
@RequestMapping("/fhir-metrics")
@ConditionalOnProperty(name = "hapi.fhir.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class PerformanceMetricsController {

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Get FHIR layer performance metrics as JSON
     */
    @GetMapping(value = "/layers", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getLayerMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Get timer metrics for each layer
        metrics.put("http_request", getTimerStats("fhir.layer.http_request"));
        metrics.put("servlet", getTimerStats("fhir.layer.servlet"));
        metrics.put("resource_binding", getTimerStats("fhir.layer.resource_binding"));
        metrics.put("method_binding", getTimerStats("fhir.layer.method_binding"));
        metrics.put("provider", getTimerStats("fhir.layer.provider"));
        metrics.put("dao_registry", getTimerStats("fhir.layer.dao_registry"));
        metrics.put("dao", getTimerStats("fhir.layer.dao"));
        metrics.put("jpa", getTimerStats("fhir.layer.jpa"));
        metrics.put("response", getTimerStats("fhir.layer.response"));
        metrics.put("total_request", getTimerStats("fhir.request.total"));
        
        // Get counter metrics
        metrics.put("total_requests", getCounterValue("fhir.requests.total"));
        metrics.put("error_requests", getCounterValue("fhir.requests.errors"));
        
        return metrics;
    }
    
    /**
     * Get Prometheus metrics (if Prometheus registry is configured)
     */
    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getPrometheusMetrics() {
        if (meterRegistry instanceof PrometheusMeterRegistry) {
            return ((PrometheusMeterRegistry) meterRegistry).scrape();
        } else {
            return "# Prometheus metrics not available. Configure 'hapi.fhir.tracing.metrics.type=prometheus'";
        }
    }
    
    /**
     * Get summary of current performance statistics
     */
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        Timer totalTimer = meterRegistry.find("fhir.request.total").timer();
        if (totalTimer != null) {
            summary.put("total_requests_count", totalTimer.count());
            summary.put("average_response_time_ms", totalTimer.mean(TimeUnit.MILLISECONDS));
            summary.put("max_response_time_ms", totalTimer.max(TimeUnit.MILLISECONDS));
            summary.put("95th_percentile_ms", totalTimer.percentile(0.95, TimeUnit.MILLISECONDS));
            summary.put("99th_percentile_ms", totalTimer.percentile(0.99, TimeUnit.MILLISECONDS));
        }
        
        // Layer breakdown (average times)
        Map<String, Double> layerAverages = new HashMap<>();
        layerAverages.put("http_request_ms", getTimerMean("fhir.layer.http_request"));
        layerAverages.put("servlet_ms", getTimerMean("fhir.layer.servlet"));
        layerAverages.put("resource_binding_ms", getTimerMean("fhir.layer.resource_binding"));
        layerAverages.put("method_binding_ms", getTimerMean("fhir.layer.method_binding"));
        layerAverages.put("provider_ms", getTimerMean("fhir.layer.provider"));
        layerAverages.put("dao_registry_ms", getTimerMean("fhir.layer.dao_registry"));
        layerAverages.put("dao_ms", getTimerMean("fhir.layer.dao"));
        layerAverages.put("jpa_ms", getTimerMean("fhir.layer.jpa"));
        layerAverages.put("response_ms", getTimerMean("fhir.layer.response"));
        
        summary.put("layer_averages", layerAverages);
        
        return summary;
    }
    
    private Map<String, Object> getTimerStats(String timerName) {
        Timer timer = meterRegistry.find(timerName).timer();
        Map<String, Object> stats = new HashMap<>();
        
        if (timer != null) {
            stats.put("count", timer.count());
            stats.put("mean_ms", timer.mean(TimeUnit.MILLISECONDS));
            stats.put("max_ms", timer.max(TimeUnit.MILLISECONDS));
            stats.put("total_time_ms", timer.totalTime(TimeUnit.MILLISECONDS));
        } else {
            stats.put("count", 0);
            stats.put("mean_ms", 0.0);
            stats.put("max_ms", 0.0);
            stats.put("total_time_ms", 0.0);
        }
        
        return stats;
    }
    
    private double getTimerMean(String timerName) {
        Timer timer = meterRegistry.find(timerName).timer();
        return timer != null ? timer.mean(TimeUnit.MILLISECONDS) : 0.0;
    }
    
    private double getCounterValue(String counterName) {
        return meterRegistry.find(counterName).counter() != null ? 
            meterRegistry.find(counterName).counter().count() : 0.0;
    }
}