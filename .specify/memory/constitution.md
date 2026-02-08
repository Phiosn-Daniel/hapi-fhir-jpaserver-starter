<!-- Sync Impact Report
  Version change: 0.0.0 → 1.0.0
  Modified principles: N/A (initial creation)
  Added sections:
    - Core Principles (6 principles)
    - Technology Stack & Constraints
    - Development Workflow & Quality Gates
    - Governance
  Removed sections: N/A
  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ aligned (Constitution Check section compatible)
    - .specify/templates/spec-template.md ✅ aligned (Requirements/Success Criteria compatible)
    - .specify/templates/checklist-template.md ⚠ pending review on next checklist use
    - .specify/templates/tasks-template.md ⚠ pending review on next task generation
  Follow-up TODOs: None
-->

# HAPI FHIR JPA Server Constitution

## Core Principles

### I. FHIR 標準合規 (Standards Compliance)

所有資料處理 MUST 遵守 HL7 FHIR 規範（目前以 R4 為主）。
- 資源結構、搜尋參數、操作定義 MUST 符合 FHIR 規範，不得自行發明非標準欄位或端點。
- 啟用 Implementation Guide 時 MUST 使用官方發布的套件（`package.tgz`），並透過 `application.yaml` 的 `implementationguides` 區段進行宣告式安裝。
- 自訂擴展（Extension）MUST 定義 StructureDefinition，且 MUST 通過 `$validate` 操作驗證。
- 回應格式 MUST 支援 JSON 和 XML，預設為 JSON。

### II. 安全優先 (Security First)

此專案處理的是醫療健康資料，安全性為不可妥協的要求。
- 生產環境 MUST 實作身份驗證與授權機制（參考 HAPI FHIR Security 文件）。
- 敏感配置（資料庫密碼、API 金鑰）MUST NOT 提交至版本控制；MUST 使用環境變數或外部配置檔。
- 所有對外端點 MUST 經過安全審查後才能啟用。
- Interceptor 中處理的安全事件 MUST 有結構化日誌記錄。

### III. 組態驅動 (Configuration-Driven)

功能啟用與行為調整 MUST 優先透過 `application.yaml` 配置實現，而非修改原始碼。
- 模組啟停（CR、CDS Hooks、MDM、MCP）MUST 透過 YAML 屬性控制。
- 自訂 Interceptor 和 Provider MUST 透過 `hapi.fhir.custom-interceptor-classes` 和 `hapi.fhir.custom-provider-classes` 註冊。
- 資料庫切換（H2 → PostgreSQL → MS SQL）MUST 僅需修改 YAML 配置，不涉及程式碼變更。
- 環境特定配置 MUST 使用 Spring Profile 或外部掛載的 YAML 檔案管理。

### IV. 測試紀律 (Test Discipline)

所有功能變更 MUST 有對應的測試覆蓋，確保醫療資料處理的可靠性。
- 單元測試以 JUnit 5 撰寫，存放於 `src/test/java`，以 Surefire 執行。
- 整合測試以 `*IT.java` 命名，以 Failsafe 執行，預設使用 H2 資料庫。
- 新增 Interceptor 或 Provider MUST 包含至少一項正向與一項負向測試案例。
- 資料庫切換後若整合測試不相容，MUST 在 PR 中說明並提供替代驗證方式。

### V. 可觀測性 (Observability)

系統執行狀態 MUST 可被外部監控與追蹤。
- Actuator 端點（`/actuator/health`, `/actuator/prometheus`）MUST 維持啟用狀態。
- 效能關鍵配置（連線池、執行緒池、搜尋協調器）MUST 有對應的指標暴露。
- 支援 OpenTelemetry Java Agent 進行分散式追蹤，部署時 SHOULD 啟用。
- 錯誤與異常 MUST 透過結構化日誌輸出，包含足夠的上下文資訊以利排查。

### VI. 簡約原則 (Simplicity)

避免過早優化與不必要的複雜度。
- 新功能 MUST 先評估是否可透過現有 HAPI FHIR 機制（Interceptor、Provider、配置）實現，再考慮自訂開發。
- 依賴引入 MUST 有明確理由，不得引入功能重疊的套件。
- 程式碼結構 MUST 遵循 `ca.uhn.fhir.jpa.starter` 套件慣例，使用描述性後綴（`*Provider`、`*Service`、`*Config`）。
- YAGNI：不為「未來可能需要」而預先實作功能。

## Technology Stack & Constraints

| 項目 | 規格 |
|------|------|
| 語言 | Java 17 (四空格縮排、無萬用字元 import) |
| 框架 | Spring Boot + HAPI FHIR 8.4.0 |
| FHIR 版本 | R4 (可透過配置切換) |
| 建置工具 | Apache Maven |
| 開發資料庫 | H2 (記憶體模式) |
| 生產資料庫 | PostgreSQL (推薦) 或 MS SQL Server |
| 容器化 | Docker + Docker Compose |
| 測試框架 | JUnit 5 + Surefire/Failsafe + Testcontainers |
| 監控 | Spring Actuator + Prometheus + OpenTelemetry |
| 預設端口 | 8080 |

**硬性限制：**
- MySQL 不被支援（已棄用）。
- 多實例部署時，訂閱與訊息通道預設為記憶體模式，MUST 另行配置外部訊息代理。
- 不含內建身份驗證，生產部署前 MUST 自行實作或整合外部方案。

## Development Workflow & Quality Gates

### 建置流程
1. `mvn clean install`：編譯 + 單元測試 + 整合測試 → 產出 `target/ROOT.war`
2. `mvn spring-boot:run -Pboot`：本地開發啟動（熱重載）
3. `docker-compose up -d --build`：完整容器化環境（含 PostgreSQL）

### 品質門檻
- PR 提交前 MUST 執行 `mvn verify` 通過。
- 所有新增的配置選項 MUST 在 `application.yaml` 中有註解說明。
- Dockerfile 或部署相關變更 MUST 在 PR 描述中說明執行時影響（profiles、ports、env vars）。
- 提交訊息 MUST 使用祈使句式，可選 scope（如 `Feature/mcp`），關聯 issue 以 `(#123)` 標注。

### 程式碼審查
- 變更 MUST 範圍精確，配置與程式碼一同提交。
- 跳過的測試 MUST 在 PR 中說明理由與手動驗證方式。
- UI 行為變更 MUST 附截圖。

## Governance

- 本 Constitution 為專案最高指導原則，所有開發實踐 MUST 與之一致。
- 修訂流程：提出修訂 → 文件化變更理由 → 更新版本號 → 同步更新受影響的模板與文件。
- 版本遵循語意化版本規則：
  - **MAJOR**：原則刪除或根本性重新定義
  - **MINOR**：新增原則或實質性擴充指引
  - **PATCH**：措辭修正、錯字修復、非語意性調整
- 所有 PR 審查 MUST 驗證是否符合本 Constitution 的原則。
- 複雜度引入 MUST 有書面理由，並記錄於實作計畫的 Complexity Tracking 區段。

**Version**: 1.0.0 | **Ratified**: 2026-02-06 | **Last Amended**: 2026-02-06
