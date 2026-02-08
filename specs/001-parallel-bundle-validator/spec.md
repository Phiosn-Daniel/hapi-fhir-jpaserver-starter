# Feature Specification: 平行 Bundle 驗證器 (Parallel Bundle Validator)

**Feature Branch**: `001-parallel-bundle-validator`  
**Created**: 2026-02-06  
**Status**: Draft  
**Input**: User description: "我想要開發一個平行驗證器的功能，再做 bundle json 檔案時，可以自動判斷檔案中entry 下的resource 數量，來決定需要同時啟動多執行續驗證，但要注意CPU 的使用率"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 自動平行驗證 Bundle 中的多筆資源 (Priority: P1)

身為一位系統操作人員，當我提交一個包含大量 entry 的 FHIR Bundle 進行驗證時，系統會自動偵測 entry 下的 resource 數量，並根據數量動態決定啟動多少個並行驗證執行緒，以縮短整體驗證時間。如果 Bundle 只包含少量資源（例如 5 筆以下），系統會採用循序驗證以避免不必要的執行緒開銷。

**Why this priority**: 這是整個功能的核心價值——透過平行處理大幅減少大型 Bundle 的驗證等待時間，直接影響使用者的工作效率與系統吞吐量。

**Independent Test**: 可透過提交不同大小的 Bundle（例如 3 筆、20 筆、100 筆 entry）來獨立驗證系統是否正確選擇循序或平行模式，並確認驗證結果正確完整。

**Acceptance Scenarios**:

1. **Given** 一個包含 50 筆 entry 的 FHIR Bundle, **When** 使用者提交此 Bundle 進行驗證, **Then** 系統自動啟用平行驗證模式，使用多個執行緒同時驗證各筆資源，並在完成後回傳完整的驗證結果（包含每筆資源的驗證狀態）。
2. **Given** 一個包含 3 筆 entry 的 FHIR Bundle, **When** 使用者提交此 Bundle 進行驗證, **Then** 系統採用循序驗證模式處理，避免產生不必要的執行緒管理開銷。
3. **Given** 一個包含 100 筆 entry 的 FHIR Bundle，其中部分資源含有驗證錯誤, **When** 使用者提交此 Bundle 進行驗證, **Then** 系統平行驗證所有資源，個別資源的驗證失敗不影響其他資源的驗證，最終回傳每筆資源各自的驗證結果。

---

### User Story 2 - CPU 使用率自動管控 (Priority: P2)

身為一位系統管理員，我希望平行驗證功能在執行時能自動監控並控制 CPU 使用率，確保驗證過程不會耗盡伺服器資源，影響到其他正在處理的請求或服務。

**Why this priority**: 醫療資訊系統需要維持高可用性，若平行驗證導致 CPU 過載，將影響整體服務品質與其他使用者的操作體驗。

**Independent Test**: 可透過提交超大型 Bundle（例如 500 筆以上 entry）同時監控系統 CPU 使用率，確認系統會自動調節並行數量或排隊處理，CPU 不會長時間超過設定上限。

**Acceptance Scenarios**:

1. **Given** 系統正在平行驗證一個大型 Bundle, **When** CPU 使用率接近設定上限, **Then** 系統自動降低並行執行緒數量或暫停新增執行緒，直到 CPU 使用率回到安全範圍。
2. **Given** 多位使用者同時提交大型 Bundle 進行驗證, **When** 系統 CPU 資源有限, **Then** 系統合理分配並行驗證資源，不會因為單一請求而導致其他請求逾時或失敗。
3. **Given** 系統管理員設定 CPU 使用率上限為 70%, **When** 平行驗證執行中, **Then** 系統 CPU 使用率不會持續超過 70%。

---

### User Story 3 - 平行驗證行為可配置 (Priority: P3)

身為一位系統管理員，我希望能夠透過配置調整平行驗證的行為參數，包括觸發平行驗證的最低資源數量門檻、最大並行執行緒數量、以及 CPU 使用率上限，以便根據不同部署環境的硬體條件進行最佳化。

**Why this priority**: 不同部署環境的硬體資源差異大，提供配置彈性能讓管理員根據實際狀況調整，確保功能在各種環境下都能最佳運作。

**Independent Test**: 可透過修改配置參數後重啟或熱載入，提交相同的 Bundle 驗證請求，確認系統行為確實根據配置改變（例如調低門檻後小型 Bundle 也會啟用平行驗證）。

**Acceptance Scenarios**:

1. **Given** 管理員在配置中將平行驗證門檻設為 10, **When** 提交一個包含 12 筆 entry 的 Bundle, **Then** 系統啟用平行驗證模式。
2. **Given** 管理員在配置中將最大並行執行緒數設為 4, **When** 提交一個包含 200 筆 entry 的 Bundle, **Then** 系統最多同時使用 4 個執行緒進行驗證。
3. **Given** 管理員未修改任何配置（使用預設值）, **When** 提交 Bundle 進行驗證, **Then** 系統使用合理的預設值正常運作。

---

### Edge Cases

- 當 Bundle 的 entry 為空（0 筆資源）時，系統如何處理？應直接回傳空的驗證結果，不啟動任何執行緒。
- 當 Bundle 中的某筆 resource 格式嚴重錯誤（例如無法解析為 FHIR 資源）時，該筆驗證失敗不應導致整個平行驗證流程中斷。
- 當伺服器在驗證進行中被要求關閉（graceful shutdown）時，系統應等待正在執行的驗證完成或在合理時間內取消未完成的任務。
- 當多個大型 Bundle 同時提交時，系統的執行緒池應有上限保護，避免執行緒數量無限增長。
- 當 Bundle 中包含巢狀 Bundle（Bundle of Bundles）時，系統應如何處理？假設僅平行處理第一層 entry，不遞迴處理巢狀 Bundle。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系統 MUST 在收到 FHIR Bundle 驗證請求時，自動解析 Bundle 中 entry 陣列的 resource 數量。
- **FR-002**: 系統 MUST 根據 resource 數量與可用系統資源，動態計算最佳並行驗證執行緒數量。
- **FR-003**: 當 resource 數量低於設定門檻時，系統 MUST 採用循序驗證模式。
- **FR-004**: 當 resource 數量達到或超過門檻時，系統 MUST 啟用平行驗證模式，將資源分配至多個執行緒同時驗證。
- **FR-005**: 系統 MUST 在平行驗證過程中監控 CPU 使用率，當使用率接近設定上限時自動調節並行數量。
- **FR-006**: 單一資源的驗證失敗 MUST NOT 導致其他資源的驗證中斷或失敗。
- **FR-007**: 系統 MUST 將所有平行驗證的結果彙整為單一的 OperationOutcome 回應，保留每筆資源的驗證詳情。
- **FR-008**: 平行驗證的結果 MUST 與循序驗證的結果完全一致（結果等價性）。
- **FR-009**: 系統 MUST 提供配置選項，允許管理員調整：平行驗證門檻、最大並行數、CPU 使用率上限。
- **FR-010**: 系統 MUST 使用有界限的執行緒池管理並行驗證任務，防止執行緒數量無限增長。

### Key Entities

- **FHIR Bundle**: 包含多筆 entry 的容器資源，每個 entry 內含一個待驗證的 FHIR resource。是平行驗證的輸入來源。
- **Bundle Entry**: Bundle 中的單一條目，包含一個 resource 和相關的請求資訊。是平行驗證的最小工作單元。
- **驗證結果 (Validation Result)**: 每筆資源驗證後產生的結果，包含通過/失敗狀態及詳細的問題描述。所有結果最終彙整為一個 OperationOutcome。
- **驗證執行緒池 (Validation Thread Pool)**: 管理並行驗證工作者的資源池，具有可配置的大小上限與 CPU 感知的動態調節能力。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 包含 50 筆以上 entry 的 Bundle 驗證時間，相較於循序驗證至少縮短 40%。
- **SC-002**: 平行驗證執行期間，系統 CPU 使用率不超過管理員設定的上限（預設 80%）。
- **SC-003**: 無論使用平行或循序模式，相同 Bundle 的驗證結果完全一致，正確率 100%。
- **SC-004**: 系統在平行驗證大型 Bundle 的同時，仍能正常回應其他使用者的請求，回應時間增幅不超過 20%。
- **SC-005**: 管理員可在不修改程式碼的情況下，透過配置調整平行驗證的所有行為參數。

## Assumptions

- 系統執行環境至少具備 2 核以上的 CPU，平行驗證才有實質效益。單核環境下將自動降級為循序驗證。
- FHIR Bundle 中的各筆 entry resource 可獨立驗證，彼此之間不存在驗證順序依賴關係。
- CPU 使用率的偵測以作業系統層級的指標為依據，採樣間隔需在合理範圍內（例如每秒一次），避免過度消耗監控資源。
- 預設配置值為：平行驗證門檻 = 10 筆、最大並行執行緒數 = CPU 核心數、CPU 使用率上限 = 80%。
- 巢狀 Bundle（Bundle of Bundles）僅平行處理第一層 entry，不遞迴展開處理。
