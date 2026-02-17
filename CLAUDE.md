# CLAUDE.md — Tawqi

Tawqi (توقيع) is an open-source, self-hosted digital document signing platform with PKI-based certificate management. It targets institutions (campuses, government offices, enterprises) that need in-house signing infrastructure without per-signature commercial costs.

Repository: `github.com/artivisi/tawqi`
License: Apache-2.0

## Architecture Overview

Tawqi is a single Spring Boot application that serves three logical components:

1. **Tawqi Service** — REST API backend for all signing operations
2. **Tawqi Portal** — Server-rendered web UI (signer dashboard, admin panel, document creator)
3. **Tawqi Verify** — Public verification page (QR scan, PDF upload)

All three are packaged in one deployable JAR. There is no separate frontend build.

External dependency: **HashiCorp Vault OSS** for PKI engine, secrets management, and certificate lifecycle.

```
┌─────────────────┐     REST API      ┌──────────────────────────────────────┐     Vault API    ┌────────────────┐
│  Campus Apps     │ ──────────────►   │          Tawqi (Spring Boot)         │ ──────────────►  │ HashiCorp Vault│
│  (SMILE, Finance,│  ◄── Webhook ──   │                                      │                  │ PKI + Transit  │
│   HRIS, etc.)    │                   │  Service │ Portal │ Verify            │                  │ KV + Database  │
└─────────────────┘                   └──────────┬───────────────────────────┘                  └────────────────┘
                                                  │
                                        ┌─────────┴─────────┐
                                        │  PostgreSQL 17     │
                                        │  + File Storage    │
                                        └───────────────────┘
```

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Language | Java 25 | Virtual threads enabled by default |
| Framework | Spring Boot 4.x | WebMVC (not WebFlux) |
| PDF Signing | Apache PDFBox 3.x | PKCS#7/CMS signatures, PAdES-B-B level |
| QR Code | ZXing | Embedded in signed PDFs |
| Template Engine | Thymeleaf | Server-side HTML rendering |
| Frontend Interactivity | HTMX 2.x + Alpine.js (CSP build) | No npm, no build step. Vendored JS files in `src/main/resources/static/vendor/` |
| Database | PostgreSQL 17 | Metadata, workflow state, audit logs |
| Migrations | Flyway | SQL-based, in `src/main/resources/db/migration/` |
| Vault Client | Spring Cloud Vault | Auto-config, lease renewal |
| Build | Maven | Single module, `pom.xml` |
| Testing | JUnit 5 + Testcontainers + Spring Boot Test | Full-stack functional tests against real PostgreSQL + Vault containers |
| Container | Docker | Multi-stage build, Eclipse Temurin base |

## Project Structure

```
tawqi/
├── CLAUDE.md
├── README.md
├── LICENSE
├── pom.xml
├── .mvn/
│   └── wrapper/
├── docker/
│   ├── Dockerfile
│   ├── docker-compose.yml              # Tawqi + PostgreSQL + Vault (dev)
│   └── vault/
│       ├── vault-dev.hcl               # Dev server config
│       └── init-pki.sh                 # Script to bootstrap PKI engine
├── src/
│   ├── main/
│   │   ├── java/id/artivisi/tawqi/
│   │   │   ├── TawqiApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── VaultConfig.java
│   │   │   │   └── WebMvcConfig.java
│   │   │   ├── domain/
│   │   │   │   ├── Document.java
│   │   │   │   ├── DocumentStatus.java     # DRAFT, PENDING, SIGNED, REJECTED
│   │   │   │   ├── SigningRequest.java
│   │   │   │   ├── SigningWorkflow.java     # Single, sequential multi-signer
│   │   │   │   ├── Signer.java
│   │   │   │   ├── SignerCertificate.java
│   │   │   │   ├── Template.java
│   │   │   │   ├── Batch.java
│   │   │   │   └── AuditEntry.java
│   │   │   ├── repository/                 # Spring Data JPA repositories
│   │   │   ├── service/
│   │   │   │   ├── SigningService.java      # Core signing orchestration
│   │   │   │   ├── PdfSignerService.java    # PDFBox signing logic
│   │   │   │   ├── VaultPkiService.java     # Vault PKI interactions
│   │   │   │   ├── QrCodeService.java       # ZXing QR generation
│   │   │   │   ├── VerificationService.java # Signature verification
│   │   │   │   ├── BatchService.java        # Bulk signing with worker pool
│   │   │   │   ├── TemplateService.java     # PDF template + data merge
│   │   │   │   └── WebhookService.java      # Callback dispatcher
│   │   │   ├── api/                         # REST controllers
│   │   │   │   ├── DocumentController.java
│   │   │   │   ├── SigningController.java
│   │   │   │   ├── BatchController.java
│   │   │   │   ├── TemplateController.java
│   │   │   │   └── VerificationController.java
│   │   │   └── web/                         # Thymeleaf page controllers
│   │   │       ├── PortalController.java    # Signer dashboard, creator UI
│   │   │       ├── AdminController.java     # PKI admin panel
│   │   │       └── VerifyController.java    # Public verification page
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   ├── application-dev.yml
│   │   │   ├── application-prod.yml
│   │   │   ├── db/migration/
│   │   │   │   └── V001__initial_schema.sql
│   │   │   ├── templates/                   # Thymeleaf templates
│   │   │   │   ├── layout/
│   │   │   │   │   └── default.html         # Base layout with nav
│   │   │   │   ├── portal/
│   │   │   │   │   ├── dashboard.html       # Signer dashboard
│   │   │   │   │   ├── pending.html         # Pending documents list
│   │   │   │   │   ├── sign.html            # Document signing page
│   │   │   │   │   ├── upload.html          # Document creator upload
│   │   │   │   │   └── history.html         # Signing history
│   │   │   │   ├── admin/
│   │   │   │   │   ├── certificates.html    # Certificate management
│   │   │   │   │   ├── audit.html           # Audit log viewer
│   │   │   │   │   └── health.html          # System health
│   │   │   │   ├── verify/
│   │   │   │   │   └── index.html           # Public verification
│   │   │   │   └── fragments/
│   │   │   │       ├── header.html
│   │   │   │       ├── nav.html
│   │   │   │       └── document-card.html
│   │   │   └── static/
│   │   │       ├── vendor/
│   │   │       │   ├── htmx.min.js          # HTMX 2.x
│   │   │       │   ├── htmx-sse.js          # SSE extension
│   │   │       │   └── alpine.csp.min.js    # Alpine.js CSP build
│   │   │       └── css/
│   │   │           └── tawqi.css
│   │   └── ...
│   └── test/
│       └── java/id/artivisi/tawqi/
│           ├── TawqiApplicationTests.java
│           ├── TawqiTestConfig.java          # Shared Testcontainers config (PostgreSQL + Vault)
│           ├── signing/
│           │   ├── SigningFlowTest.java       # Submit → sign → verify round trip
│           │   ├── BatchSigningTest.java      # Template + CSV → bulk sign → download ZIP
│           │   └── RejectFlowTest.java        # Submit → reject → resubmit
│           ├── verification/
│           │   ├── PdfVerificationTest.java   # Upload signed PDF → validate signature
│           │   └── QrVerificationTest.java    # QR code URL → verification result
│           ├── pki/
│           │   └── VaultPkiTest.java          # Issue cert, revoke, check CRL
│           └── api/
│               ├── DocumentApiTest.java       # REST API contract tests
│               └── TemplateApiTest.java
```

## Package Naming

- Group: `id.artivisi`
- Artifact: `tawqi`
- Base package: `id.artivisi.tawqi`

## Key Design Decisions

### PDF Signing Flow

1. Document submitted via API or Portal upload → stored as `DRAFT`
2. Signer assigned (single or sequential) → status becomes `PENDING`
3. Signer authenticates with passphrase → Service retrieves signing key from Vault
4. PDFBox embeds PKCS#7 signature with byte-range dictionary into PDF
5. QR code generated and embedded as visible signature annotation
6. Signed PDF stored → status becomes `SIGNED`
7. Webhook dispatched to originating application

### Vault PKI Integration

- Root CA mounted at `pki-root/` — offline, issues only the intermediate
- Intermediate CA at `pki-campus/` — issues signer certificates
- Signer private keys stored in Vault Transit engine (Vault never exports raw keys)
- Signing operation: Service sends hash to Vault, Vault signs with Transit, Service embeds signature in PDF
- CRL published at a public endpoint served through Tawqi Verify

### Authentication Model

| User | Auth Method |
|---|---|
| API consumers (campus apps) | API key in `X-API-Key` header |
| Signers | SSO (campus credentials) + passphrase per signing action |
| Document creators | SSO (campus credentials) |
| PKI admins | Vault token + MFA |
| Verifiers (public) | None required |

SSO integration is pluggable — start with simple form login backed by Spring Security, with clear interface for future Keycloak/LDAP integration.

### Database Schema Principles

- All timestamps in UTC, stored as `TIMESTAMP WITH TIME ZONE`
- Soft deletes using `deleted_at` column where needed
- Audit columns on every table: `created_at`, `created_by`, `updated_at`, `updated_by`
- Document IDs are human-readable prefixed codes: `TQ-2026-00001` (not UUIDs for user-facing references)
- UUIDs used as primary keys internally

### File Storage

- Signed PDFs stored on local filesystem by default at `${tawqi.storage.path:/var/lib/tawqi/documents}`
- Directory structure: `/{year}/{month}/{document-id}/signed.pdf`
- Templates stored similarly under `/{templates}/{template-id}/`
- Configuration property to switch to S3-compatible storage (MinIO) later

## Coding Conventions

### Java Style

- Records for DTOs and value objects
- Sealed interfaces for domain enums where appropriate
- Constructor injection (no `@Autowired` on fields)
- Virtual threads: use `@Async` with virtual thread executor, no manual thread pool management
- No Lombok — use Java records and IDE generation instead
- Prefer `Optional` returns from repository methods, never return `null` from service methods

### API Conventions

- Base path: `/api/v1/`
- Request/response bodies in JSON
- Error responses follow RFC 7807 Problem Details format
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- API versioning in URL path (v1)

### Thymeleaf Conventions

- Layout dialect for consistent page structure
- Fragment-based HTMX responses: controllers return fragment names for HTMX requests, full pages for normal requests
- Detect HTMX requests via `HX-Request` header
- Use `th:fragment` for reusable components
- CSS: single stylesheet, no CSS framework initially (add Tailwind later if needed)

### Testing Strategy

**Philosophy: functional tests first, no mocks.**

All tests run against real infrastructure via Testcontainers (PostgreSQL + Vault) and `@SpringBootTest`. No mock objects — if a test needs a database or Vault, it gets a real one. Unit tests are avoided unless the logic is purely computational with no dependencies (e.g., a date formatter or ID generator).

- **Functional tests (primary):** Test complete user-visible workflows end-to-end through the Spring Boot stack. Example: submit a PDF via REST API → signer signs it → download signed PDF → verify signature programmatically → confirm QR code resolves. These tests use `TestRestTemplate` or `WebTestClient` hitting the actual running application.
- **Integration tests (fallback):** For components that are hard to exercise through the API alone (e.g., Vault PKI certificate issuance, CRL generation, Transit key operations). These inject the service bean directly but still run against real Testcontainers — no mocking.
- **Shared Testcontainers config:** A single `TawqiTestConfig` class defines `@ServiceConnection` containers for PostgreSQL and Vault, reused across all test classes via `@Import`. Containers are started once per test suite using `@Testcontainers` with singleton pattern.
- **No `@MockBean`, no Mockito, no MockMvc.** All HTTP tests go through a real embedded server (`webEnvironment = RANDOM_PORT`).
- No minimum coverage target, but all signing, verification, and PKI logic must have functional tests.

## Development Setup

### Prerequisites

- Java 25 (Eclipse Temurin recommended)
- Docker and Docker Compose (for PostgreSQL + Vault dev instances)
- Maven 3.9+ (wrapper included)

### Quick Start

```bash
# Start dependencies
docker compose -f docker/docker-compose.yml up -d

# Initialize Vault PKI (first time only)
docker compose -f docker/docker-compose.yml exec vault sh /vault/config/init-pki.sh

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test
```

### Dev Profile (`application-dev.yml`)

- PostgreSQL at `localhost:5432/tawqi`
- Vault at `http://localhost:8200` with dev root token
- File storage at `./data/documents`
- Debug logging for `id.artivisi.tawqi` and `org.springframework.vault`

## Docker Compose (Dev)

The dev compose file runs:

1. **PostgreSQL 17** — port 5432, database `tawqi`, user `tawqi`
2. **Vault OSS** (dev mode) — port 8200, root token `dev-root-token`
   - PKI engine pre-configured with root + intermediate CA
   - Transit engine enabled for signing operations
   - KV v2 for application secrets

## REST API Endpoints

| Endpoint | Method | Description | Auth |
|---|---|---|---|
| `POST /api/v1/documents` | POST | Submit document for signing | API key |
| `POST /api/v1/sign/{docId}` | POST | Sign a pending document | Signer |
| `POST /api/v1/reject/{docId}` | POST | Reject with reason | Signer |
| `GET /api/v1/pending/{signerId}` | GET | List pending docs for signer | Signer |
| `POST /api/v1/batch/template-sign` | POST | Bulk sign from template + CSV data | Signer |
| `POST /api/v1/batch/sign` | POST | Bulk sign pre-generated PDFs | Signer |
| `GET /api/v1/batch/{batchId}/status` | GET | Batch progress | API key |
| `GET /api/v1/batch/{batchId}/progress` | SSE | Real-time progress stream | API key |
| `GET /api/v1/batch/{batchId}/download` | GET | Download batch as ZIP | API key |
| `POST /api/v1/verify` | POST | Verify uploaded PDF signature | Public |
| `GET /api/v1/verify/{docId}` | GET | Verify by document ID (QR) | Public |
| `POST /api/v1/templates` | POST | Upload document template | Admin |
| `GET /api/v1/templates` | GET | List templates | API key |

## Bootstrap Priority

When setting up the project from scratch, follow this order:

1. **Maven project scaffold** — `pom.xml` with all dependencies, Spring Boot parent
2. **Docker compose** — PostgreSQL + Vault dev environment
3. **Spring Boot application** — main class, configuration, profiles
4. **Database schema** — Flyway V001 with core tables (documents, signers, signing_requests, audit_entries)
5. **Vault config** — dev init script for PKI + Transit engines
6. **PDF signing service** — PDFBox-based signing and verification (the core value)
7. **REST API** — document submission and signing endpoints
8. **Thymeleaf pages** — portal layout, signer dashboard, verification page
9. **Functional tests** — sign-then-verify round trip with Testcontainers (real PostgreSQL + Vault)
10. **Dockerfile** — multi-stage production build

## Configuration Properties

```yaml
tawqi:
  storage:
    path: /var/lib/tawqi/documents    # Local file storage root
    type: local                        # local | s3
  vault:
    pki-mount: pki-campus              # Vault PKI engine mount path
    transit-mount: transit             # Vault Transit engine mount path
    transit-key-prefix: signer-        # Key name prefix in Transit
  signing:
    batch-pool-size: 4                 # Parallel signing threads for batch
    qr-base-url: https://verify.example.ac.id/doc/  # Public verification URL prefix
    document-id-prefix: TQ             # Prefix for human-readable document IDs
  webhook:
    timeout-seconds: 10                # HTTP timeout for webhook callbacks
    retry-count: 3                     # Retry failed webhooks
```

## Notes for Claude Code

- This is a greenfield project. Start from an empty directory.
- The first deliverable is a working `./mvnw spring-boot:run` with the dev Docker environment.
- Do NOT add Lombok. Use Java records and explicit constructors.
- Do NOT add a frontend build pipeline (no npm, no webpack). HTMX and Alpine.js are vendored static files.
- Do NOT use Spring WebFlux. This is a WebMVC application using virtual threads.
- Do NOT create mocks. No `@MockBean`, no Mockito. All tests run against real services via Testcontainers.
- Do NOT write unit tests unless the logic is purely computational with zero dependencies. Default to `@SpringBootTest(webEnvironment = RANDOM_PORT)` functional tests.
- Prefer `application.yml` over `application.properties`.
- The Vault dev init script should create a complete PKI chain (root CA → intermediate CA) and a test signer key in Transit.
