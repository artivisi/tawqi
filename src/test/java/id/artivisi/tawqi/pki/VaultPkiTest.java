package id.artivisi.tawqi.pki;

import id.artivisi.tawqi.TawqiTestConfig;
import id.artivisi.tawqi.service.CertificateInfo;
import id.artivisi.tawqi.service.VaultPkiService;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TawqiTestConfig.class)
@ActiveProfiles("test")
class VaultPkiTest {

    @Autowired
    private VaultPkiService vaultPkiService;

    @Test
    void shouldCreateTransitKeyAndSignHash() throws Exception {
        String keyName = "test-sign-" + System.currentTimeMillis();
        vaultPkiService.createTransitKey(keyName);

        String fullKeyName = "signer-" + keyName;

        byte[] data = "test data to sign".getBytes();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);

        byte[] signature = vaultPkiService.signHash(fullKeyName, hash);

        assertNotNull(signature);
        assertTrue(signature.length > 0);
    }

    @Test
    void shouldIssueCertificate() {
        CertificateInfo certInfo = vaultPkiService.issueCertificate("test-signer.tawqi.local");

        assertNotNull(certInfo);
        assertNotNull(certInfo.certificatePem());
        assertTrue(certInfo.certificatePem().contains("BEGIN CERTIFICATE"));
        assertNotNull(certInfo.serialNumber());
        assertNotNull(certInfo.issuingCaPem());
        assertNotNull(certInfo.expiresAt());
    }
}
