package ca.uhn.fhir.jpa.starter.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 根據資源類型自動套用對應的 TW Core IG Profile URL。
 *
 * <p>當資源未在 {@code meta.profile} 中宣告任何 Profile 時，
 * 系統會根據資源類型自動套用對應的 TW Core Profile 進行驗證（FR-027）。
 */
public class ProfileAutoApplier {

    private static final Logger logger = LoggerFactory.getLogger(ProfileAutoApplier.class);

    /**
     * TW Core IG 的 Profile URL 前綴
     */
    private static final String TW_CORE_BASE = "https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/";

    /**
     * 資源類型 → TW Core Profile URL 的映射表
     */
    private static final Map<String, String> RESOURCE_PROFILE_MAP = new HashMap<>();

    static {
        // TW Core IG 定義的主要 Profile
        RESOURCE_PROFILE_MAP.put("Patient", TW_CORE_BASE + "Patient-twcore");
        RESOURCE_PROFILE_MAP.put("Observation", TW_CORE_BASE + "Observation-laboratoryResult-twcore");
        RESOURCE_PROFILE_MAP.put("Encounter", TW_CORE_BASE + "Encounter-twcore");
        RESOURCE_PROFILE_MAP.put("Condition", TW_CORE_BASE + "Condition-twcore");
        RESOURCE_PROFILE_MAP.put("MedicationRequest", TW_CORE_BASE + "MedicationRequest-twcore");
        RESOURCE_PROFILE_MAP.put("Medication", TW_CORE_BASE + "Medication-twcore");
        RESOURCE_PROFILE_MAP.put("Organization", TW_CORE_BASE + "Organization-twcore");
        RESOURCE_PROFILE_MAP.put("Practitioner", TW_CORE_BASE + "Practitioner-twcore");
        RESOURCE_PROFILE_MAP.put("PractitionerRole", TW_CORE_BASE + "PractitionerRole-twcore");
        RESOURCE_PROFILE_MAP.put("Procedure", TW_CORE_BASE + "Procedure-twcore");
        RESOURCE_PROFILE_MAP.put("DiagnosticReport", TW_CORE_BASE + "DiagnosticReport-twcore");
        RESOURCE_PROFILE_MAP.put("AllergyIntolerance", TW_CORE_BASE + "AllergyIntolerance-twcore");
        RESOURCE_PROFILE_MAP.put("Immunization", TW_CORE_BASE + "Immunization-twcore");
        RESOURCE_PROFILE_MAP.put("Location", TW_CORE_BASE + "Location-twcore");
        RESOURCE_PROFILE_MAP.put("Specimen", TW_CORE_BASE + "Specimen-twcore");
        RESOURCE_PROFILE_MAP.put("Composition", TW_CORE_BASE + "Composition-twcore");
        RESOURCE_PROFILE_MAP.put("ImagingStudy", TW_CORE_BASE + "ImagingStudy-twcore");
        RESOURCE_PROFILE_MAP.put("Media", TW_CORE_BASE + "Media-twcore");
        RESOURCE_PROFILE_MAP.put("Bundle", TW_CORE_BASE + "Bundle-twcore");
    }

    /**
     * 根據資源類型取得對應的 TW Core Profile URL。
     *
     * @param resourceType FHIR 資源類型名稱（如 "Patient", "Observation"）
     * @return 對應的 TW Core Profile URL，若無對應則回傳 null
     */
    public String getProfileForResourceType(String resourceType) {
        String profileUrl = RESOURCE_PROFILE_MAP.get(resourceType);
        if (profileUrl != null) {
            logger.debug("🔍 Auto-applying TW Core profile for {}: {}", resourceType, profileUrl);
        } else {
            logger.debug("🔍 No TW Core profile found for resource type: {}, using FHIR R4 base only",
                    resourceType);
        }
        return profileUrl;
    }

    /**
     * 檢查是否有指定資源類型的 TW Core Profile。
     */
    public boolean hasProfileFor(String resourceType) {
        return RESOURCE_PROFILE_MAP.containsKey(resourceType);
    }

    /**
     * 取得所有已註冊的 Profile 映射。
     */
    public Map<String, String> getAllProfileMappings() {
        return Map.copyOf(RESOURCE_PROFILE_MAP);
    }
}
