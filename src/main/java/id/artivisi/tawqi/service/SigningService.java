package id.artivisi.tawqi.service;

import id.artivisi.tawqi.config.TawqiProperties;
import id.artivisi.tawqi.domain.Document;
import id.artivisi.tawqi.domain.DocumentStatus;
import id.artivisi.tawqi.domain.Signer;
import id.artivisi.tawqi.domain.SignerCertificate;
import id.artivisi.tawqi.domain.SigningRequest;
import id.artivisi.tawqi.domain.SigningRequestStatus;
import id.artivisi.tawqi.domain.SigningWorkflow;
import id.artivisi.tawqi.domain.WorkflowType;
import id.artivisi.tawqi.repository.DocumentRepository;
import id.artivisi.tawqi.repository.SignerCertificateRepository;
import id.artivisi.tawqi.repository.SignerRepository;
import id.artivisi.tawqi.repository.SigningRequestRepository;
import id.artivisi.tawqi.repository.SigningWorkflowRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningService {

    private static final Logger log = LoggerFactory.getLogger(SigningService.class);

    private final DocumentRepository documentRepository;
    private final SignerRepository signerRepository;
    private final SignerCertificateRepository signerCertificateRepository;
    private final SigningWorkflowRepository signingWorkflowRepository;
    private final SigningRequestRepository signingRequestRepository;
    private final PdfSignerService pdfSignerService;
    private final AuditService auditService;
    private final TawqiProperties properties;

    public SigningService(DocumentRepository documentRepository,
                          SignerRepository signerRepository,
                          SignerCertificateRepository signerCertificateRepository,
                          SigningWorkflowRepository signingWorkflowRepository,
                          SigningRequestRepository signingRequestRepository,
                          PdfSignerService pdfSignerService,
                          AuditService auditService,
                          TawqiProperties properties) {
        this.documentRepository = documentRepository;
        this.signerRepository = signerRepository;
        this.signerCertificateRepository = signerCertificateRepository;
        this.signingWorkflowRepository = signingWorkflowRepository;
        this.signingRequestRepository = signingRequestRepository;
        this.pdfSignerService = pdfSignerService;
        this.auditService = auditService;
        this.properties = properties;
    }

    public String generateDocumentCode() {
        long seq = documentRepository.getNextDocumentCodeSequence();
        int year = LocalDate.now().getYear();
        return String.format("%s-%d-%05d", properties.getSigning().getDocumentIdPrefix(), year, seq);
    }

    @Transactional
    public Document submitDocument(String title, String description, String filename,
                                   byte[] pdfBytes, String submittedBy,
                                   List<UUID> signerIds, WorkflowType workflowType) {
        String documentCode = generateDocumentCode();
        String storagePath = storeOriginalPdf(documentCode, pdfBytes);

        Document doc = new Document();
        doc.setDocumentCode(documentCode);
        doc.setTitle(title);
        doc.setDescription(description);
        doc.setOriginalFilename(filename);
        doc.setStoragePath(storagePath);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setSubmittedBy(submittedBy);
        doc.setCreatedBy(submittedBy);
        doc.setUpdatedBy(submittedBy);
        documentRepository.save(doc);

        SigningWorkflow workflow = new SigningWorkflow();
        workflow.setDocument(doc);
        workflow.setWorkflowType(workflowType);
        workflow.setCreatedBy(submittedBy);
        workflow.setUpdatedBy(submittedBy);
        signingWorkflowRepository.save(workflow);

        for (int i = 0; i < signerIds.size(); i++) {
            Signer signer = signerRepository.findById(signerIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Signer not found"));

            SigningRequest request = new SigningRequest();
            request.setWorkflow(workflow);
            request.setSigner(signer);
            request.setSequenceOrder(i + 1);
            request.setStatus(SigningRequestStatus.PENDING);
            request.setCreatedBy(submittedBy);
            request.setUpdatedBy(submittedBy);
            signingRequestRepository.save(request);
        }

        auditService.log("Document", doc.getId(), "SUBMITTED", submittedBy,
                Map.of("documentCode", documentCode, "title", title), null);

        log.info("Document submitted: {} ({})", documentCode, title);
        return doc;
    }

    @Transactional
    public Document signDocument(UUID documentId, UUID signerId, String actor) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        if (doc.getStatus() != DocumentStatus.PENDING) {
            throw new IllegalStateException("Document is not in PENDING status: " + doc.getDocumentCode());
        }

        Signer signer = signerRepository.findById(signerId)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found: " + signerId));

        SigningWorkflow workflow = signingWorkflowRepository.findByDocument(doc)
                .orElseThrow(() -> new IllegalStateException(
                        "No workflow found for document: " + doc.getDocumentCode()));

        SigningRequest request = signingRequestRepository.findByWorkflowAndSigner(workflow, signer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No signing request found for this signer on document: " + doc.getDocumentCode()));

        if (request.getStatus() != SigningRequestStatus.PENDING) {
            throw new IllegalStateException("Signing request is not PENDING for signer: " + signer.getUsername());
        }

        // Validate sequential order
        if (workflow.getWorkflowType() == WorkflowType.SEQUENTIAL) {
            List<SigningRequest> allRequests =
                    signingRequestRepository.findByWorkflowOrderBySequenceOrderAsc(workflow);
            for (SigningRequest r : allRequests) {
                if (r.getSequenceOrder() < request.getSequenceOrder()
                        && r.getStatus() == SigningRequestStatus.PENDING) {
                    throw new IllegalStateException(
                            "Previous signer in sequence has not yet signed: " + r.getSigner().getUsername());
                }
            }
        }

        SignerCertificate cert = signerCertificateRepository.findBySignerAndActiveTrue(signer)
                .orElseThrow(() -> new IllegalStateException(
                        "No active certificate found for signer: " + signer.getUsername()));

        // Read the current PDF (original or last signed version)
        byte[] pdfToSign = readPdfForSigning(doc);

        X509Certificate x509Cert = PdfSignerService.parseCertificatePem(cert.getCertificatePem());

        byte[] signedPdf = pdfSignerService.signPdf(
                pdfToSign, x509Cert, cert.getTransitKeyName(),
                doc.getDocumentCode(), signer.getDisplayName());

        String signedPath = storeSignedPdf(doc.getDocumentCode(), signedPdf);
        doc.setSignedStoragePath(signedPath);

        request.setStatus(SigningRequestStatus.SIGNED);
        request.setSignedAt(Instant.now());
        request.setUpdatedBy(actor);
        signingRequestRepository.save(request);

        // Check if all requests are now signed
        List<SigningRequest> allRequests =
                signingRequestRepository.findByWorkflowOrderBySequenceOrderAsc(workflow);
        boolean allSigned = allRequests.stream()
                .allMatch(r -> r.getStatus() == SigningRequestStatus.SIGNED);

        if (allSigned) {
            doc.setStatus(DocumentStatus.SIGNED);
        }
        doc.setUpdatedBy(actor);
        documentRepository.save(doc);

        auditService.log("Document", doc.getId(), "SIGNED", actor,
                Map.of("signer", signer.getUsername()), null);

        log.info("Document signed by {}: {}", signer.getUsername(), doc.getDocumentCode());
        return doc;
    }

    @Transactional
    public Document rejectDocument(UUID documentId, UUID signerId, String reason, String actor) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        if (doc.getStatus() != DocumentStatus.PENDING) {
            throw new IllegalStateException("Document is not in PENDING status: " + doc.getDocumentCode());
        }

        Signer signer = signerRepository.findById(signerId)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found: " + signerId));

        SigningWorkflow workflow = signingWorkflowRepository.findByDocument(doc)
                .orElseThrow(() -> new IllegalStateException(
                        "No workflow found for document: " + doc.getDocumentCode()));

        SigningRequest request = signingRequestRepository.findByWorkflowAndSigner(workflow, signer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No signing request found for this signer on document: " + doc.getDocumentCode()));

        if (request.getStatus() != SigningRequestStatus.PENDING) {
            throw new IllegalStateException("Signing request is not PENDING for signer: " + signer.getUsername());
        }

        request.setStatus(SigningRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setUpdatedBy(actor);
        signingRequestRepository.save(request);

        doc.setStatus(DocumentStatus.REJECTED);
        doc.setUpdatedBy(actor);
        documentRepository.save(doc);

        auditService.log("Document", doc.getId(), "REJECTED", actor,
                Map.of("signer", signer.getUsername(), "reason", reason), null);

        log.info("Document rejected by {}: {} - {}", signer.getUsername(), doc.getDocumentCode(), reason);
        return doc;
    }

    private String storeOriginalPdf(String documentCode, byte[] pdfBytes) {
        LocalDate now = LocalDate.now();
        Path dir = Path.of(properties.getStorage().getPath(),
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()),
                documentCode);
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve("original.pdf");
            Files.write(filePath, pdfBytes);
            return filePath.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store original PDF: " + documentCode, e);
        }
    }

    private String storeSignedPdf(String documentCode, byte[] signedPdf) {
        LocalDate now = LocalDate.now();
        Path dir = Path.of(properties.getStorage().getPath(),
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()),
                documentCode);
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve("signed.pdf");
            Files.write(filePath, signedPdf);
            return filePath.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store signed PDF: " + documentCode, e);
        }
    }

    private byte[] readPdfForSigning(Document doc) {
        String path = doc.getSignedStoragePath() != null
                ? doc.getSignedStoragePath()
                : doc.getStoragePath();
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read PDF for signing: " + path, e);
        }
    }
}
