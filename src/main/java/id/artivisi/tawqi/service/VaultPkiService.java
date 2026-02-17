package id.artivisi.tawqi.service;

import id.artivisi.tawqi.config.TawqiProperties;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

@Service
public class VaultPkiService {

    private static final Logger log = LoggerFactory.getLogger(VaultPkiService.class);

    private final VaultTemplate vaultTemplate;
    private final TawqiProperties properties;

    public VaultPkiService(VaultTemplate vaultTemplate, TawqiProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
    }

    public CertificateInfo issueCertificate(String commonName) {
        String path = properties.getVault().getPkiMount() + "/issue/signer-cert";
        VaultResponse response = vaultTemplate.write(path, Map.of(
                "common_name", commonName,
                "ttl", "8760h"));

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault returned empty response for certificate issuance");
        }

        Map<String, Object> data = response.getData();
        String certificatePem = (String) data.get("certificate");
        String serialNumber = (String) data.get("serial_number");
        String issuingCa = (String) data.get("issuing_ca");

        Object expirationObj = data.get("expiration");
        Instant expiresAt = null;
        if (expirationObj instanceof Number num) {
            expiresAt = Instant.ofEpochSecond(num.longValue());
        }

        return new CertificateInfo(certificatePem, serialNumber, issuingCa, expiresAt);
    }

    public CertificateInfo issueCertificateForTransitKey(String commonName, String transitKeyName) {
        PublicKey publicKey = getTransitPublicKey(transitKeyName);

        // Build CSR using the Transit key's public key, signed via Transit
        ContentSigner csrSigner = new VaultTransitContentSigner(this, transitKeyName);
        PKCS10CertificationRequestBuilder csrBuilder =
                new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=" + commonName), publicKey);
        PKCS10CertificationRequest csr = csrBuilder.build(csrSigner);

        String csrPem = toPem(csr);

        // Submit CSR to PKI for signing
        String path = properties.getVault().getPkiMount() + "/sign/signer-cert";
        VaultResponse response = vaultTemplate.write(path, Map.of(
                "csr", csrPem,
                "common_name", commonName,
                "ttl", "8760h"));

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault returned empty response for CSR signing");
        }

        Map<String, Object> data = response.getData();
        String certificatePem = (String) data.get("certificate");
        String serialNumber = (String) data.get("serial_number");
        String issuingCa = (String) data.get("issuing_ca");

        Object expirationObj = data.get("expiration");
        Instant expiresAt = null;
        if (expirationObj instanceof Number num) {
            expiresAt = Instant.ofEpochSecond(num.longValue());
        }

        return new CertificateInfo(certificatePem, serialNumber, issuingCa, expiresAt);
    }

    public void createTransitKey(String keyName) {
        String fullKeyName = properties.getVault().getTransitKeyPrefix() + keyName;
        String path = properties.getVault().getTransitMount() + "/keys/" + fullKeyName;
        vaultTemplate.write(path, Map.of("type", "rsa-2048"));
        log.info("Created transit key: {}", fullKeyName);
    }

    @SuppressWarnings("unchecked")
    public PublicKey getTransitPublicKey(String transitKeyName) {
        String path = properties.getVault().getTransitMount() + "/keys/" + transitKeyName;
        VaultResponse response = vaultTemplate.read(path);

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault returned empty response for transit key: " + transitKeyName);
        }

        Map<String, Object> data = response.getData();
        Map<String, Object> keys = (Map<String, Object>) data.get("keys");
        // Get the latest version's public key
        String latestVersion = String.valueOf(data.get("latest_version"));
        Map<String, Object> keyData = (Map<String, Object>) keys.get(latestVersion);
        String publicKeyPem = (String) keyData.get("public_key");

        return parsePublicKeyPem(publicKeyPem);
    }

    public byte[] signHash(String transitKeyName, byte[] sha256Hash) {
        String path = properties.getVault().getTransitMount() + "/sign/" + transitKeyName;
        String base64Hash = Base64.getEncoder().encodeToString(sha256Hash);

        VaultResponse response = vaultTemplate.write(path, Map.of(
                "input", base64Hash,
                "prehashed", true,
                "hash_algorithm", "sha2-256",
                "signature_algorithm", "pkcs1v15"));

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault returned empty response for signing operation");
        }

        String signature = (String) response.getData().get("signature");
        // Vault returns signatures in format "vault:v1:<base64>"
        String base64Sig = signature.substring(signature.lastIndexOf(':') + 1);
        return Base64.getDecoder().decode(base64Sig);
    }

    public void revokeCertificate(String serialNumber) {
        String path = properties.getVault().getPkiMount() + "/revoke";
        vaultTemplate.write(path, Map.of("serial_number", serialNumber));
        log.info("Revoked certificate: {}", serialNumber);
    }

    public String getCaCertificatePem() {
        String path = properties.getVault().getPkiMount() + "/ca/pem";
        VaultResponse response = vaultTemplate.read(path);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault returned empty response for CA certificate");
        }
        return (String) response.getData().get("data");
    }

    private static PublicKey parsePublicKeyPem(String pem) {
        try {
            String stripped = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(stripped);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse public key PEM", e);
        }
    }

    private static String toPem(PKCS10CertificationRequest csr) {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(csr);
            }
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode CSR to PEM", e);
        }
    }
}
