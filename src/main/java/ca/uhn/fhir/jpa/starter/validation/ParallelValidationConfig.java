package ca.uhn.fhir.jpa.starter.validation;

import ca.uhn.fhir.jpa.starter.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 平行驗證的配置模型，從 AppProperties.Validation 讀取設定值。
 *
 * <p>提供語義化的存取方法和合理的預設值處理。
 */
@Component
public class ParallelValidationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ParallelValidationConfig.class);

    private final boolean enabled;
    private final int threadPoolSize;
    private final int entryThreshold;
    private final double cpuLimit;
    private final long cpuCheckIntervalMs;

    public ParallelValidationConfig(AppProperties appProperties) {
        AppProperties.Validation validation = appProperties.getValidation();

        this.enabled = Boolean.TRUE.equals(validation.isConcurrent_bundle_validation_enabled());

        // thread pool size: 0 或 null 表示自動偵測 CPU 核心數
        Integer configuredSize = validation.getConcurrent_bundle_validation_thread_pool_size();
        this.threadPoolSize = (configuredSize != null && configuredSize > 0)
                ? configuredSize
                : Runtime.getRuntime().availableProcessors();

        Integer configuredThreshold = validation.getConcurrent_bundle_validation_entry_threshold();
        this.entryThreshold = (configuredThreshold != null && configuredThreshold > 0)
                ? configuredThreshold
                : 10;

        Double configuredCpuLimit = validation.getConcurrent_bundle_validation_cpu_limit();
        this.cpuLimit = (configuredCpuLimit != null && configuredCpuLimit > 0 && configuredCpuLimit <= 1.0)
                ? configuredCpuLimit
                : 0.80;

        Long configuredInterval = validation.getConcurrent_bundle_validation_cpu_check_interval_ms();
        this.cpuCheckIntervalMs = (configuredInterval != null && configuredInterval > 0)
                ? configuredInterval
                : 1000L;

        logger.info("✅ ParallelValidationConfig initialized: enabled={}, threadPoolSize={}, " +
                        "entryThreshold={}, cpuLimit={:.0f}%, cpuCheckInterval={}ms",
                enabled, threadPoolSize, entryThreshold, cpuLimit * 100, cpuCheckIntervalMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getEntryThreshold() {
        return entryThreshold;
    }

    public double getCpuLimit() {
        return cpuLimit;
    }

    public long getCpuCheckIntervalMs() {
        return cpuCheckIntervalMs;
    }
}
