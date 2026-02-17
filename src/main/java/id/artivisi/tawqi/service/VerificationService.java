package id.artivisi.tawqi.service;

import id.artivisi.tawqi.domain.Document;
import id.artivisi.tawqi.repository.DocumentRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final DocumentRepository documentRepository;

    public VerificationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public VerificationResult verifyPdf(byte[] signedPdf) {
        try {
            return doVerifyPdf(signedPdf);
        } catch (Exception e) {
            log.error("PDF verification failed", e);
            return VerificationResult.unsigned();
        }
    }

    public VerificationResult verifyByDocumentCode(String documentCode) {
        Document doc = documentRepository.findByDocumentCodeAndDeletedAtIsNull(documentCode)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentCode));

        if (doc.getSignedStoragePath() == null) {
            return VerificationResult.unsigned();
        }

        try {
            byte[] signedPdf = Files.readAllBytes(Path.of(doc.getSignedStoragePath()));
            return verifyPdf(signedPdf);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read signed PDF: " + doc.getSignedStoragePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private VerificationResult doVerifyPdf(byte[] signedPdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(signedPdf)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            if (signatures.isEmpty()) {
                return VerificationResult.unsigned();
            }

            PDSignature lastSignature = signatures.get(signatures.size() - 1);
            byte[] cmsBytes = lastSignature.getContents(signedPdf);
            byte[] signedContent = lastSignature.getSignedContent(signedPdf);

            CMSSignedData cmsSignedData = new CMSSignedData(
                    new org.bouncycastle.cms.CMSProcessableByteArray(signedContent), cmsBytes);

            SignerInformationStore signerInfoStore = cmsSignedData.getSignerInfos();
            Collection<SignerInformation> signers = signerInfoStore.getSigners();

            if (signers.isEmpty()) {
                return VerificationResult.unsigned();
            }

            SignerInformation signerInfo = signers.iterator().next();

            Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
            Collection<X509CertificateHolder> certMatches =
                    certStore.getMatches(signerInfo.getSID());

            if (certMatches.isEmpty()) {
                return VerificationResult.unsigned();
            }

            X509CertificateHolder certHolder = certMatches.iterator().next();
            X509Certificate signerCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            boolean signatureValid = signerInfo.verify(
                    new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider("BC")
                            .build(signerCert));

            String signerName = signerCert.getSubjectX500Principal().getName();
            Instant signedAt = lastSignature.getSignDate() != null
                    ? lastSignature.getSignDate().toInstant()
                    : null;

            return new VerificationResult(
                    signatureValid,
                    signerName,
                    signedAt,
                    signatureValid,
                    true,  // chain validation deferred to full implementation
                    true); // CRL check deferred to full implementation
        }
    }
}
