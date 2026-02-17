package id.artivisi.tawqi.signing;

import id.artivisi.tawqi.TawqiTestConfig;
import id.artivisi.tawqi.domain.Document;
import id.artivisi.tawqi.domain.DocumentStatus;
import id.artivisi.tawqi.domain.Signer;
import id.artivisi.tawqi.domain.SignerCertificate;
import id.artivisi.tawqi.domain.WorkflowType;
import id.artivisi.tawqi.repository.SignerCertificateRepository;
import id.artivisi.tawqi.repository.SignerRepository;
import id.artivisi.tawqi.service.CertificateInfo;
import id.artivisi.tawqi.service.SigningService;
import id.artivisi.tawqi.service.VaultPkiService;
import id.artivisi.tawqi.service.VerificationResult;
import id.artivisi.tawqi.service.VerificationService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TawqiTestConfig.class)
@ActiveProfiles("test")
class SigningFlowTest {

    @Autowired
    private SigningService signingService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private VaultPkiService vaultPkiService;

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private SignerCertificateRepository signerCertificateRepository;

    @Test
    void shouldSignAndVerifyDocument() throws Exception {
        // 1. Create a signer
        Signer signer = new Signer();
        signer.setUsername("test.signer." + System.currentTimeMillis());
        signer.setDisplayName("Test Signer");
        signer.setEmail("test@tawqi.local");
        signer.setCreatedBy("test");
        signer.setUpdatedBy("test");
        signer = signerRepository.save(signer);

        // 2. Create transit key in Vault
        String keyName = "test-" + System.currentTimeMillis();
        vaultPkiService.createTransitKey(keyName);
        String fullKeyName = "signer-" + keyName;

        // 3. Issue certificate via Vault PKI using the Transit key's public key
        CertificateInfo certInfo = vaultPkiService.issueCertificateForTransitKey(
                signer.getUsername() + ".tawqi.local", fullKeyName);

        // 4. Save SignerCertificate with PEM
        SignerCertificate signerCert = new SignerCertificate();
        signerCert.setSigner(signer);
        signerCert.setTransitKeyName(fullKeyName);
        signerCert.setCertificateSerial(certInfo.serialNumber());
        signerCert.setCertificatePem(certInfo.certificatePem());
        signerCert.setIssuingCaPem(certInfo.issuingCaPem());
        signerCert.setIssuedAt(java.time.Instant.now());
        signerCert.setExpiresAt(certInfo.expiresAt());
        signerCert.setActive(true);
        signerCert.setCreatedBy("test");
        signerCert.setUpdatedBy("test");
        signerCertificateRepository.save(signerCert);

        // 5. Create a test PDF
        byte[] testPdf = createTestPdf();

        // 6. Submit document
        Document doc = signingService.submitDocument(
                "Test Document",
                "A test document for signing",
                "test.pdf",
                testPdf,
                "test",
                List.of(signer.getId()),
                WorkflowType.SINGLE);

        assertNotNull(doc.getDocumentCode());
        assertTrue(doc.getDocumentCode().startsWith("TQ-"));
        assertEquals(DocumentStatus.PENDING, doc.getStatus());

        // 7. Sign the document
        Document signedDoc = signingService.signDocument(doc.getId(), signer.getId(), "test");

        assertEquals(DocumentStatus.SIGNED, signedDoc.getStatus());
        assertNotNull(signedDoc.getSignedStoragePath());

        // 8. Verify the signed PDF
        byte[] signedPdfBytes = java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of(signedDoc.getSignedStoragePath()));
        VerificationResult result = verificationService.verifyPdf(signedPdfBytes);

        assertTrue(result.valid());
        assertTrue(result.signatureValid());
    }

    private byte[] createTestPdf() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Test document for Tawqi digital signing");
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }
}
