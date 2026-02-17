# Tawqi (توقيع)

Self-hosted digital document signing platform with PKI-based certificate management. Targets institutions (campuses, government offices, enterprises) that need in-house signing infrastructure without per-signature commercial costs.

## Architecture

Single Spring Boot application serving three logical components:

- **Tawqi Service** — REST API for signing operations
- **Tawqi Portal** — Server-rendered web UI (Thymeleaf + HTMX)
- **Tawqi Verify** — Public signature verification page

External dependency: **HashiCorp Vault OSS** for PKI engine, Transit signing, and secrets management.

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

| Layer | Technology |
|---|---|
| Language | Java 25 (virtual threads) |
| Framework | Spring Boot 4.x (WebMVC) |
| PDF Signing | Apache PDFBox 3.x (PKCS#7/CMS, PAdES-B-B) |
| CMS/Crypto | Bouncy Castle |
| QR Code | ZXing |
| Template Engine | Thymeleaf + HTMX 2.x + Alpine.js |
| Database | PostgreSQL 17 |
| Migrations | Flyway |
| Vault Client | Spring Cloud Vault |
| Build | Maven |
| Testing | JUnit 5 + Testcontainers (real PG + Vault) |

## Prerequisites

- Java 25 (Eclipse Temurin recommended)
- Docker and Docker Compose
- Maven 3.9+ (wrapper included)

## Quick Start

```bash
# Start PostgreSQL + Vault
docker compose -f docker/docker-compose.yml up -d

# Initialize Vault PKI (first time only)
docker compose -f docker/docker-compose.yml exec vault sh /vault/config/init-pki.sh

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test
```

## PDF Signing Flow

1. Document submitted via API → stored as `PENDING`
2. Signer assigned (single or sequential workflow)
3. Service retrieves Transit signing key from Vault
4. PDFBox embeds PKCS#7 detached signature into PDF
5. QR code generated and embedded as visible annotation
6. Signed PDF stored → status becomes `SIGNED`
7. Verification via QR scan or PDF upload extracts and validates CMS signature

Key design: private keys never leave Vault. The service sends a SHA-256 hash to Vault Transit, which returns the raw signature. The certificate is issued via Vault PKI using a CSR built from the Transit key's public key, ensuring the certificate and signing key are aligned.

## Project Structure

```
src/main/java/id/artivisi/tawqi/
├── TawqiApplication.java
├── config/          # TawqiProperties, VaultConfig, SecurityConfig, WebMvcConfig
├── domain/          # JPA entities, enums, base class
├── repository/      # Spring Data JPA repositories
├── service/         # Core services (signing, verification, Vault PKI, QR)
├── api/             # REST controllers (planned)
└── web/             # Thymeleaf controllers (planned)
```

## Configuration

See `src/main/resources/application.yml` for all `tawqi.*` properties. Key settings:

```yaml
tawqi:
  storage:
    path: /var/lib/tawqi/documents
  vault:
    pki-mount: pki-campus
    transit-mount: transit
    transit-key-prefix: signer-
  signing:
    qr-base-url: https://verify.example.ac.id/doc/
    document-id-prefix: TQ
```

## Testing

All tests run against real PostgreSQL and Vault via Testcontainers. No mocks.

```bash
./mvnw test
```

## License

Apache-2.0
