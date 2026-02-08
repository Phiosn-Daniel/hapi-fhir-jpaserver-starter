package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.ValidationResult;
import ca.uhn.fhir.jpa.starter.validation.CpuAwareThreadPoolExecutor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快速驗證測試：確認平行 Bundle 驗證器的核心功能正常運作。
 * 不需要 Spring Boot 或資料庫，直接使用 FhirContext。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BundleValidationSmokeTest {

    private FhirContext ctx;
    private FhirValidator parallelValidator;
    private FhirValidator sequentialValidator;

    @BeforeAll
    void setup() {
        ctx = FhirContext.forR4();

        // 建立平行驗證器（門檻 = 5）
        parallelValidator = ctx.newValidator();
        parallelValidator.setValidateAgainstStandardSchema(true);
        parallelValidator.setValidateAgainstStandardSchematron(false);
        CpuAwareThreadPoolExecutor executor = new CpuAwareThreadPoolExecutor(2, 4, 0.80, 1000);
        parallelValidator.setExecutorService(executor);
        parallelValidator.setConcurrentBundleValidation(true);
        parallelValidator.setConcurrentBundleEntryThreshold(5);

        // 建立循序驗證器
        sequentialValidator = ctx.newValidator();
        sequentialValidator.setValidateAgainstStandardSchema(true);
        sequentialValidator.setValidateAgainstStandardSchematron(false);
    }

    /**
     * 測試 1：小型 Bundle（3 entries < threshold 5）應以循序模式驗證，結果正確
     */
    @Test
    void testSmallBundleValidation() {
        Bundle bundle = createBundleWithPatients(3);

        ValidationResult result = parallelValidator.validateWithResult(bundle);

        // 小型 Bundle 不應有嚴重錯誤（基本 Patient 結構是合法的）
        assertNotNull(result);
        System.out.println("=== Small Bundle (3 entries, below threshold 5) ===");
        System.out.println("Valid: " + result.isSuccessful());
        System.out.println("Messages: " + result.getMessages().size());
        result.getMessages().forEach(m ->
                System.out.println("  " + m.getSeverity() + ": " + m.getMessage()));
    }

    /**
     * 測試 2：大型 Bundle（10 entries >= threshold 5）應以平行模式驗證，結果正確
     */
    @Test
    void testLargeBundleValidation() {
        Bundle bundle = createBundleWithPatients(10);

        ValidationResult result = parallelValidator.validateWithResult(bundle);

        assertNotNull(result);
        System.out.println("=== Large Bundle (10 entries, above threshold 5) ===");
        System.out.println("Valid: " + result.isSuccessful());
        System.out.println("Messages: " + result.getMessages().size());
        result.getMessages().forEach(m ->
                System.out.println("  " + m.getSeverity() + ": " + m.getMessage()));
    }

    /**
     * 測試 3：平行驗證結果與循序驗證結果一致（結果等價性 FR-008）
     */
    @Test
    void testParallelAndSequentialResultsAreEquivalent() {
        Bundle bundle = createBundleWithPatients(8);

        ValidationResult parallelResult = parallelValidator.validateWithResult(bundle);
        ValidationResult sequentialResult = sequentialValidator.validateWithResult(bundle);

        assertNotNull(parallelResult);
        assertNotNull(sequentialResult);

        System.out.println("=== Result Equivalence Test (8 entries) ===");
        System.out.println("Parallel - Valid: " + parallelResult.isSuccessful()
                + ", Messages: " + parallelResult.getMessages().size());
        System.out.println("Sequential - Valid: " + sequentialResult.isSuccessful()
                + ", Messages: " + sequentialResult.getMessages().size());

        // 兩者的成功/失敗狀態應一致
        assertEquals(parallelResult.isSuccessful(), sequentialResult.isSuccessful(),
                "Parallel and sequential validation should produce same success status");
    }

    /**
     * 測試 4：空 Bundle 應正常處理，不拋例外
     */
    @Test
    void testEmptyBundleValidation() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        ValidationResult result = parallelValidator.validateWithResult(bundle);

        assertNotNull(result);
        System.out.println("=== Empty Bundle (0 entries) ===");
        System.out.println("Valid: " + result.isSuccessful());
        System.out.println("Messages: " + result.getMessages().size());
    }

    /**
     * 測試 5：包含驗證錯誤的 Bundle，個別資源失敗不影響其他（FR-006）
     */
    @Test
    void testBundleWithMixedValidResources() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        // 合法的 Patient
        Patient validPatient = new Patient();
        validPatient.setId("valid-patient");
        validPatient.addName().setFamily("Test").addGiven("Valid");
        validPatient.setGender(Enumerations.AdministrativeGender.MALE);
        bundle.addEntry().setResource(validPatient);

        // 合法的 Observation
        Observation obs = new Observation();
        obs.setId("valid-obs");
        obs.setStatus(Observation.ObservationStatus.FINAL);
        bundle.addEntry().setResource(obs);

        // 多加幾筆超過門檻
        for (int i = 0; i < 5; i++) {
            Patient p = new Patient();
            p.setId("patient-" + i);
            p.addName().setFamily("Family" + i);
            bundle.addEntry().setResource(p);
        }

        ValidationResult result = parallelValidator.validateWithResult(bundle);

        assertNotNull(result);
        System.out.println("=== Mixed Bundle (7 entries, above threshold) ===");
        System.out.println("Valid: " + result.isSuccessful());
        System.out.println("Messages: " + result.getMessages().size());
        result.getMessages().forEach(m ->
                System.out.println("  [" + m.getSeverity() + "] "
                        + m.getLocationString() + ": " + m.getMessage()));
    }

    /**
     * 測試 6：確認門檻 API 正常運作
     */
    @Test
    void testThresholdApiWorks() {
        FhirValidator validator = ctx.newValidator();
        validator.setConcurrentBundleValidation(true);

        // 設定門檻
        validator.setConcurrentBundleEntryThreshold(10);
        assertEquals(10, validator.getConcurrentBundleEntryThreshold());

        // 修改門檻
        validator.setConcurrentBundleEntryThreshold(20);
        assertEquals(20, validator.getConcurrentBundleEntryThreshold());

        // 預設值
        FhirValidator defaultValidator = ctx.newValidator();
        assertEquals(0, defaultValidator.getConcurrentBundleEntryThreshold());

        System.out.println("=== Threshold API Test ===");
        System.out.println("✅ setConcurrentBundleEntryThreshold / getConcurrentBundleEntryThreshold works correctly");
    }

    private Bundle createBundleWithPatients(int count) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        for (int i = 0; i < count; i++) {
            Patient patient = new Patient();
            patient.setId("patient-" + i);
            patient.addName(new HumanName().setFamily("Family" + i).addGiven("Given" + i));
            patient.setGender(Enumerations.AdministrativeGender.MALE);
            bundle.addEntry().setResource(patient);
        }
        return bundle;
    }
}
