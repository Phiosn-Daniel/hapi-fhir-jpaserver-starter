package ca.uhn.fhir.jpa.starter.interceptors;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 驗證效能監控 Interceptor
 * 記錄每次 FHIR 資源驗證的執行時間
 */
@Interceptor
public class ValidationTimerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationTimerInterceptor.class);
    private static final String START_TIME_KEY = "validation.start.time";

    /**
     * 請求進入時記錄開始時間
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void recordStartTime(ServletRequestDetails theRequestDetails) {
        theRequestDetails.getUserData().put(START_TIME_KEY, System.currentTimeMillis());
    }

    /**
     * 資源儲存前計算驗證時間
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void logValidationTimeOnCreate(IBaseResource theResource, RequestDetails theRequestDetails) {
        logValidationTime(theResource, theRequestDetails, "CREATE");
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void logValidationTimeOnUpdate(IBaseResource theResource, RequestDetails theRequestDetails) {
        logValidationTime(theResource, theRequestDetails, "UPDATE");
    }

    private void logValidationTime(IBaseResource theResource, RequestDetails theRequestDetails, String operation) {
        Long startTime = (Long) theRequestDetails.getUserData().get(START_TIME_KEY);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String resourceType = theResource.fhirType();
            String resourceId = theResource.getIdElement().getIdPart();
            
            logger.info("[ValidationTimer] {} {} ({}) - 驗證時間: {} ms",
                operation,
                resourceType,
                resourceId != null ? resourceId : "new",
                duration);
            
            // 超過 1 秒的驗證記錄警告
            if (duration > 1000) {
                logger.warn("[ValidationTimer] ⚠️ 驗證時間過長: {} ms for {} {}",
                    duration, resourceType, resourceId);
            }
        }
    }
}

