package ca.uhn.fhir.jpa.starter.validation;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * CPU 感知的執行緒池，定期監控 CPU 使用率並動態調整 maximumPoolSize。
 *
 * <p>當 CPU 超過設定上限時自動縮小池大小，CPU 回到安全範圍後恢復。
 * 使用移動平均避免瞬時波動觸發誤判。
 */
public class CpuAwareThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CpuAwareThreadPoolExecutor.class);
    private static final int MOVING_AVERAGE_SAMPLES = 5;

    private final int configuredMaxPoolSize;
    private final double cpuLimit;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> monitorFuture;
    private final double[] cpuSamples;
    private int sampleIndex = 0;
    private int sampleCount = 0;

    /**
     * 建立 CPU 感知的執行緒池。
     *
     * @param corePoolSize      核心執行緒數
     * @param maxPoolSize       最大執行緒數
     * @param cpuLimit          CPU 使用率上限（0.0~1.0）
     * @param checkIntervalMs   CPU 檢查間隔（毫秒）
     */
    public CpuAwareThreadPoolExecutor(int corePoolSize, int maxPoolSize,
                                       double cpuLimit, long checkIntervalMs) {
        super(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.configuredMaxPoolSize = maxPoolSize;
        this.cpuLimit = cpuLimit;
        this.cpuSamples = new double[MOVING_AVERAGE_SAMPLES];

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cpu-monitor");
            t.setDaemon(true);
            return t;
        });

        this.monitorFuture = scheduler.scheduleAtFixedRate(
                this::adjustPoolSize, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);

        logger.info("✅ CpuAwareThreadPoolExecutor initialized: core={}, max={}, cpuLimit={:.0f}%, checkInterval={}ms",
                corePoolSize, maxPoolSize, cpuLimit * 100, checkIntervalMs);
    }

    /**
     * 取得當前程序的 CPU 使用率。
     *
     * @return CPU 使用率（0.0~1.0），無法取得時回傳 -1
     */
    public double getProcessCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getProcessCpuLoad();
        }
        return -1;
    }

    /**
     * 取得移動平均 CPU 使用率。
     */
    public double getAverageCpuLoad() {
        if (sampleCount == 0) {
            return getProcessCpuLoad();
        }
        int count = Math.min(sampleCount, MOVING_AVERAGE_SAMPLES);
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += cpuSamples[i];
        }
        return sum / count;
    }

    private void adjustPoolSize() {
        try {
            double cpuLoad = getProcessCpuLoad();
            if (cpuLoad < 0) {
                return; // 無法取得 CPU 資訊
            }

            // 更新移動平均
            cpuSamples[sampleIndex] = cpuLoad;
            sampleIndex = (sampleIndex + 1) % MOVING_AVERAGE_SAMPLES;
            sampleCount++;

            double avgCpu = getAverageCpuLoad();

            if (avgCpu >= cpuLimit) {
                // CPU 過高，縮小池大小（至少保留 corePoolSize）
                int currentMax = getMaximumPoolSize();
                int newMax = Math.max(getCorePoolSize(), currentMax / 2);
                if (newMax < currentMax) {
                    setMaximumPoolSize(newMax);
                    logger.warn("⚠️ CPU avg {:.1f}% >= limit {:.1f}%, reducing pool: {} → {}",
                            avgCpu * 100, cpuLimit * 100, currentMax, newMax);
                }
            } else if (avgCpu < cpuLimit * 0.6) {
                // CPU 低於安全範圍的 60%，恢復池大小
                int currentMax = getMaximumPoolSize();
                if (currentMax < configuredMaxPoolSize) {
                    int newMax = Math.min(configuredMaxPoolSize, currentMax * 2);
                    setMaximumPoolSize(newMax);
                    logger.info("📈 CPU avg {:.1f}%, restoring pool: {} → {}",
                            avgCpu * 100, currentMax, newMax);
                }
            }
        } catch (Exception e) {
            logger.debug("CPU monitoring check failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void gracefulShutdown() {
        logger.info("🛑 Initiating graceful shutdown of CpuAwareThreadPoolExecutor...");
        monitorFuture.cancel(false);
        scheduler.shutdown();
        shutdown();
        try {
            if (!awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("⚠️ Pool did not terminate in 30s, forcing shutdown");
                shutdownNow();
            } else {
                logger.info("✅ CpuAwareThreadPoolExecutor shut down successfully");
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
