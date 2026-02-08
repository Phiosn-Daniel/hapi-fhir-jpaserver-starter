package ca.uhn.fhir.jpa.starter.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根據 Bundle entry 數量和當前 CPU 使用率，決定驗證模式（循序或平行）。
 *
 * <p>決策邏輯：
 * <ul>
 *   <li>entryCount == 0 → SKIP（不驗證）</li>
 *   <li>entryCount < threshold → SEQUENTIAL（循序）</li>
 *   <li>entryCount >= threshold 且 CPU < limit → PARALLEL（平行）</li>
 *   <li>entryCount >= threshold 且 CPU >= limit → SEQUENTIAL（降級）</li>
 * </ul>
 */
public class BundleValidationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BundleValidationStrategy.class);

    public enum ValidationMode {
        SKIP,
        SEQUENTIAL,
        PARALLEL
    }

    /**
     * 根據 entry 數量和 CPU 狀態決定驗證模式。
     *
     * @param entryCount     Bundle 中的 entry 數量
     * @param currentCpuLoad 當前 CPU 使用率（0.0~1.0），負值表示無法取得
     * @param entryThreshold 觸發平行驗證的最低 entry 數量
     * @param cpuLimit       CPU 使用率上限（0.0~1.0）
     * @return 選定的驗證模式
     */
    public ValidationMode determineMode(int entryCount, double currentCpuLoad,
                                        int entryThreshold, double cpuLimit) {
        if (entryCount <= 0) {
            logger.debug("🔍 Bundle entry count is 0, skipping validation");
            return ValidationMode.SKIP;
        }

        if (entryCount < entryThreshold) {
            logger.info("📋 Bundle has {} entries (< threshold {}), using SEQUENTIAL mode",
                    entryCount, entryThreshold);
            return ValidationMode.SEQUENTIAL;
        }

        // entryCount >= threshold，檢查 CPU
        if (currentCpuLoad >= 0 && currentCpuLoad >= cpuLimit) {
            logger.warn("⚠️ CPU usage {:.1f}% >= limit {:.1f}%, degrading to SEQUENTIAL mode for {} entries",
                    currentCpuLoad * 100, cpuLimit * 100, entryCount);
            return ValidationMode.SEQUENTIAL;
        }

        logger.info("🚀 Bundle has {} entries (>= threshold {}), CPU {:.1f}%, using PARALLEL mode",
                entryCount, entryThreshold,
                currentCpuLoad >= 0 ? currentCpuLoad * 100 : -1);
        return ValidationMode.PARALLEL;
    }
}
