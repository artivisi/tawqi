# Tawqi Implementation Progress

Tracks planned features from CLAUDE.md against current implementation state.

## Bootstrap Priority (from CLAUDE.md)

| # | Phase | Status |
|---|-------|--------|
| 1 | Maven project scaffold | DONE |
| 2 | Docker compose (PG + Vault) | DONE |
| 3 | Spring Boot application + config | DONE |
| 4 | Database schema (Flyway V001) | DONE |
| 5 | Vault config (init-pki.sh) | DONE |
| 6 | PDF signing service | NOT STARTED |
| 7 | REST API | NOT STARTED |
| 8 | Thymeleaf pages | NOT STARTED |
| 9 | Functional tests | NOT STARTED |
| 10 | Dockerfile | NOT STARTED |

## Detailed Breakdown

### Build & Infrastructure — DONE

| Item | Status | Notes |
|------|--------|-------|
| `pom.xml` | DONE | Boot 4.0.2, Cloud 2025.1.0, all deps |
| Maven wrapper | DONE | v3.9.9 |
| `.gitignore` | DONE | |
| `docker/docker-compose.yml` | DONE | PG 17 + Vault OSS |
| `docker/vault/vault-dev.hcl` | DONE | |
| `docker/vault/init-pki.sh` | DONE | Root CA, intermediate CA, Transit key, KV v2 |
| `TawqiApplication.java` | DONE | |
| `SecurityConfig.java` | DONE | Permit-all stub for dev |
| `application.yml` | DONE | Virtual threads, JPA validate, tawqi.* properties |
| `application-dev.yml` | DONE | Localhost PG/Vault, debug logging |
| `application-prod.yml` | DONE | Env var placeholders, APPROLE auth |
| `V001__initial_schema.sql` | DONE | 8 tables, indexes, constraints, audit columns, sequence |
| Test infrastructure | DONE | TawqiTestConfig (Testcontainers PG), context-load test |

### Configuration Classes — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `VaultConfig.java` | NOT STARTED | Vault auto-config, lease renewal, PKI/Transit client |
| `WebMvcConfig.java` | NOT STARTED | Virtual thread executor for @Async |

### Domain Entities — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `Document.java` | NOT STARTED | JPA entity, document_code (TQ-2026-00001), status enum |
| `DocumentStatus.java` | NOT STARTED | DRAFT, PENDING, SIGNED, REJECTED |
| `SigningRequest.java` | NOT STARTED | Per-signer signing action |
| `SigningWorkflow.java` | NOT STARTED | SINGLE or SEQUENTIAL |
| `Signer.java` | NOT STARTED | Username, display name, email |
| `SignerCertificate.java` | NOT STARTED | Link to Vault Transit key |
| `Template.java` | NOT STARTED | PDF template with JSONB field mapping |
| `Batch.java` | NOT STARTED | Bulk signing job with counters |
| `AuditEntry.java` | NOT STARTED | Immutable append-only log |

### Repositories — NOT STARTED

One Spring Data JPA repository per entity (8 total): DocumentRepository, SignerRepository, SignerCertificateRepository, SigningWorkflowRepository, SigningRequestRepository, TemplateRepository, BatchRepository, AuditEntryRepository.

### Services — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `SigningService.java` | NOT STARTED | Core signing orchestration |
| `PdfSignerService.java` | NOT STARTED | PDFBox PKCS#7/CMS, PAdES-B-B, byte-range |
| `VaultPkiService.java` | NOT STARTED | Vault PKI: issue certs, Transit signing, CRL |
| `QrCodeService.java` | NOT STARTED | ZXing QR generation + embedding |
| `VerificationService.java` | NOT STARTED | Signature verification |
| `BatchService.java` | NOT STARTED | Bulk signing, @Async + virtual threads |
| `TemplateService.java` | NOT STARTED | PDF template + data merge |
| `WebhookService.java` | NOT STARTED | Callback dispatch, 3 retries, 10s timeout |

### REST API Controllers — NOT STARTED

| Endpoint | Method | Controller | Auth | Status |
|----------|--------|------------|------|--------|
| `/api/v1/documents` | POST | DocumentController | API key | NOT STARTED |
| `/api/v1/sign/{docId}` | POST | SigningController | Signer | NOT STARTED |
| `/api/v1/reject/{docId}` | POST | SigningController | Signer | NOT STARTED |
| `/api/v1/pending/{signerId}` | GET | DocumentController | Signer | NOT STARTED |
| `/api/v1/batch/template-sign` | POST | BatchController | Signer | NOT STARTED |
| `/api/v1/batch/sign` | POST | BatchController | Signer | NOT STARTED |
| `/api/v1/batch/{batchId}/status` | GET | BatchController | API key | NOT STARTED |
| `/api/v1/batch/{batchId}/progress` | SSE | BatchController | API key | NOT STARTED |
| `/api/v1/batch/{batchId}/download` | GET | BatchController | API key | NOT STARTED |
| `/api/v1/verify` | POST | VerificationController | Public | NOT STARTED |
| `/api/v1/verify/{docId}` | GET | VerificationController | Public | NOT STARTED |
| `/api/v1/templates` | POST | TemplateController | Admin | NOT STARTED |
| `/api/v1/templates` | GET | TemplateController | API key | NOT STARTED |

### Web Controllers (Thymeleaf) — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `PortalController.java` | NOT STARTED | Signer dashboard, creator UI, HTMX fragments |
| `AdminController.java` | NOT STARTED | PKI admin panel |
| `VerifyController.java` | NOT STARTED | Public verification page |

### Thymeleaf Templates — NOT STARTED

| Template | Status |
|----------|--------|
| `layout/default.html` | NOT STARTED |
| `portal/dashboard.html` | NOT STARTED |
| `portal/pending.html` | NOT STARTED |
| `portal/sign.html` | NOT STARTED |
| `portal/upload.html` | NOT STARTED |
| `portal/history.html` | NOT STARTED |
| `admin/certificates.html` | NOT STARTED |
| `admin/audit.html` | NOT STARTED |
| `admin/health.html` | NOT STARTED |
| `verify/index.html` | NOT STARTED |
| `fragments/header.html` | NOT STARTED |
| `fragments/nav.html` | NOT STARTED |
| `fragments/document-card.html` | NOT STARTED |

### Static Vendor Assets — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `static/vendor/htmx.min.js` | NOT STARTED | HTMX 2.x, vendored (no npm) |
| `static/vendor/htmx-sse.js` | NOT STARTED | SSE extension for batch progress |
| `static/vendor/alpine.csp.min.js` | NOT STARTED | Alpine.js CSP build |
| `static/css/tawqi.css` | DONE | Empty placeholder |

### Functional Tests — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `TawqiApplicationTests.java` | DONE | Context-load test |
| `TawqiTestConfig.java` | DONE | Testcontainers PG (Vault container not yet configured) |
| `signing/SigningFlowTest.java` | NOT STARTED | Submit → sign → verify round trip |
| `signing/BatchSigningTest.java` | NOT STARTED | Template + CSV → bulk sign → ZIP |
| `signing/RejectFlowTest.java` | NOT STARTED | Submit → reject → resubmit |
| `verification/PdfVerificationTest.java` | NOT STARTED | Upload signed PDF → validate |
| `verification/QrVerificationTest.java` | NOT STARTED | QR URL → verification result |
| `pki/VaultPkiTest.java` | NOT STARTED | Issue cert, revoke, CRL check |
| `api/DocumentApiTest.java` | NOT STARTED | REST API contract tests |
| `api/TemplateApiTest.java` | NOT STARTED | Template CRUD |

### Deployment — NOT STARTED

| Item | Status | Notes |
|------|--------|-------|
| `docker/Dockerfile` | NOT STARTED | Multi-stage build, Eclipse Temurin base |
| `README.md` | NOT STARTED | |
| `LICENSE` | NOT STARTED | Apache-2.0 |

## Summary

| Category | Done | Not Started | Total |
|----------|------|-------------|-------|
| Build & Infrastructure | 13 | 0 | 13 |
| Config classes | 1 | 2 | 3 |
| Domain entities | 0 | 9 | 9 |
| Repositories | 0 | 8 | 8 |
| Services | 0 | 8 | 8 |
| REST controllers | 0 | 5 | 5 |
| Web controllers | 0 | 3 | 3 |
| Templates | 0 | 13 | 13 |
| Static assets | 1 | 3 | 4 |
| Tests | 2 | 9 | 11 |
| Deployment | 0 | 3 | 3 |
| **Total** | **17** | **63** | **80** |

## Next Implementation Phase

Per CLAUDE.md bootstrap priority, phase 6 is **PDF signing service** — the core value of the application. This requires:

1. Domain entities + repositories (Document, Signer, SignerCertificate, SigningRequest, SigningWorkflow)
2. VaultPkiService — Vault Transit signing, certificate issuance
3. PdfSignerService — PDFBox PKCS#7 embedding with byte-range dictionary
4. QrCodeService — ZXing QR generation
5. SigningService — orchestration tying it all together
6. VerificationService — signature validation
