package id.artivisi.tawqi;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.vault.VaultContainer;

@Configuration
public class TawqiTestConfig {

    private static final String VAULT_TOKEN = "test-root-token";

    private static final VaultContainer<?> VAULT;

    static {
        VAULT = new VaultContainer<>("hashicorp/vault:1.17.6")
                .withVaultToken(VAULT_TOKEN);
        VAULT.start();

        // Initialize PKI and Transit engines
        try {
            // Enable PKI Root CA
            VAULT.execInContainer("vault", "secrets", "enable", "-path=pki-root", "pki");
            VAULT.execInContainer("vault", "secrets", "tune", "-max-lease-ttl=87600h", "pki-root");
            VAULT.execInContainer("vault", "write", "-format=json",
                    "pki-root/root/generate/internal",
                    "common_name=Tawqi Root CA",
                    "ttl=87600h",
                    "key_bits=4096");

            // Enable PKI Intermediate CA
            VAULT.execInContainer("vault", "secrets", "enable", "-path=pki-campus", "pki");
            VAULT.execInContainer("vault", "secrets", "tune", "-max-lease-ttl=43800h", "pki-campus");

            // Generate intermediate CSR
            var csrResult = VAULT.execInContainer("vault", "write", "-field=csr",
                    "pki-campus/intermediate/generate/internal",
                    "common_name=Tawqi Campus Intermediate CA",
                    "key_bits=4096");
            String csr = csrResult.getStdout().trim();

            // Write CSR to temp file inside container and sign with root
            VAULT.execInContainer("sh", "-c",
                    "echo '" + csr + "' > /tmp/intermediate.csr");
            var certResult = VAULT.execInContainer("vault", "write", "-field=certificate",
                    "pki-root/root/sign-intermediate",
                    "csr=@/tmp/intermediate.csr",
                    "format=pem_bundle",
                    "ttl=43800h");
            String signedCert = certResult.getStdout().trim();

            // Set signed intermediate certificate
            VAULT.execInContainer("sh", "-c",
                    "echo '" + signedCert + "' > /tmp/intermediate.pem");
            VAULT.execInContainer("vault", "write",
                    "pki-campus/intermediate/set-signed",
                    "certificate=@/tmp/intermediate.pem");

            // Configure CA URLs
            VAULT.execInContainer("vault", "write", "pki-campus/config/urls",
                    "issuing_certificates=http://127.0.0.1:8200/v1/pki-campus/ca",
                    "crl_distribution_points=http://127.0.0.1:8200/v1/pki-campus/crl");

            // Create signer-cert role
            VAULT.execInContainer("vault", "write", "pki-campus/roles/signer-cert",
                    "allowed_domains=tawqi.local",
                    "allow_subdomains=true",
                    "allow_any_name=true",
                    "max_ttl=8760h",
                    "key_type=rsa",
                    "key_bits=2048",
                    "key_usage=DigitalSignature",
                    "ext_key_usage=CodeSigning",
                    "no_store=false",
                    "generate_lease=true");

            // Enable Transit engine
            VAULT.execInContainer("vault", "secrets", "enable", "transit");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Vault for tests", e);
        }

        // Set system properties for Spring Cloud Vault
        System.setProperty("spring.cloud.vault.uri", "http://" + VAULT.getHost() + ":" + VAULT.getFirstMappedPort());
        System.setProperty("spring.cloud.vault.token", VAULT_TOKEN);
        System.setProperty("spring.cloud.vault.authentication", "TOKEN");
        System.setProperty("spring.cloud.vault.enabled", "true");
    }

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17");
    }
}
