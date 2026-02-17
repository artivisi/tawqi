package id.artivisi.tawqi.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PdfSignerService {

    private static final Logger log = LoggerFactory.getLogger(PdfSignerService.class);

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    private final VaultPkiService vaultPkiService;
    private final QrCodeService qrCodeService;

    public PdfSignerService(VaultPkiService vaultPkiService, QrCodeService qrCodeService) {
        this.vaultPkiService = vaultPkiService;
        this.qrCodeService = qrCodeService;
    }

    public byte[] signPdf(byte[] inputPdf, X509Certificate cert, String transitKeyName,
                          String documentCode, String signerName) {
        try {
            return doSignPdf(inputPdf, cert, transitKeyName, documentCode, signerName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign PDF document: " + documentCode, e);
        }
    }

    private byte[] doSignPdf(byte[] inputPdf, X509Certificate cert, String transitKeyName,
                             String documentCode, String signerName) throws Exception {
        ByteArrayOutputStream signedOutput = new ByteArrayOutputStream();

        try (PDDocument document = Loader.loadPDF(inputPdf)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(signerName);
            signature.setReason("Document signing via Tawqi");
            signature.setSignDate(java.util.Calendar.getInstance());

            addVisibleSignature(document, documentCode, signerName);

            document.addSignature(signature);

            ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(signedOutput);

            InputStream content = externalSigning.getContent();
            byte[] cmsSignature = buildCmsSignature(content, cert, transitKeyName);
            externalSigning.setSignature(cmsSignature);
        }

        return signedOutput.toByteArray();
    }

    private void addVisibleSignature(PDDocument document, String documentCode, String signerName) throws IOException {
        PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
        PDRectangle pageRect = lastPage.getMediaBox();

        float qrSize = 80;
        float margin = 20;
        float sigWidth = 150;
        float sigHeight = qrSize;

        float x = pageRect.getWidth() - sigWidth - margin;
        float y = margin;

        BufferedImage qrImage = qrCodeService.generateQrCodeImage(documentCode, 200, 200);
        PDImageXObject qrPdImage = LosslessFactory.createFromImage(document, qrImage);

        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
        }

        PDSignatureField sigField = new PDSignatureField(acroForm);
        sigField.setPartialName("TawqiSignature_" + documentCode);
        acroForm.getFields().add(sigField);

        PDAnnotationWidget widget = sigField.getWidgets().get(0);
        PDRectangle rect = new PDRectangle(x, y, sigWidth, sigHeight);
        widget.setRectangle(rect);
        widget.setPage(lastPage);

        PDAppearanceStream appearanceStream = new PDAppearanceStream(document);
        appearanceStream.setBBox(new PDRectangle(sigWidth, sigHeight));
        appearanceStream.setResources(new org.apache.pdfbox.pdmodel.PDResources());

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (var cs = new org.apache.pdfbox.pdmodel.PDFormContentStream(appearanceStream)) {
            cs.drawImage(qrPdImage, 0, 0, qrSize, qrSize);
            cs.beginText();
            cs.setFont(font, 6);
            cs.newLineAtOffset(qrSize + 4, qrSize - 12);
            cs.showText("Signed by:");
            cs.newLineAtOffset(0, -10);
            cs.showText(truncate(signerName, 15));
            cs.newLineAtOffset(0, -10);
            cs.showText("Tawqi Digital");
            cs.newLineAtOffset(0, -10);
            cs.showText("Signature");
            cs.endText();
        }

        PDAppearanceDictionary appearance = new PDAppearanceDictionary();
        appearance.setNormalAppearance(appearanceStream);
        widget.setAppearance(appearance);

        lastPage.getAnnotations().add(widget);
    }

    private byte[] buildCmsSignature(InputStream content, X509Certificate cert,
                                     String transitKeyName) throws Exception {
        byte[] contentBytes = content.readAllBytes();

        ContentSigner contentSigner = new VaultTransitContentSigner(vaultPkiService, transitKeyName);

        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder()
                .setProvider("BC")
                .build();

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(digestProvider)
                        .build(contentSigner, cert));
        generator.addCertificates(new JcaCertStore(List.of(cert)));

        CMSProcessableByteArray cmsData = new CMSProcessableByteArray(contentBytes);
        CMSSignedData signedData = generator.generate(cmsData, false);

        return signedData.getEncoded();
    }

    public static X509Certificate parseCertificatePem(String pem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes()));
        } catch (CertificateException e) {
            throw new IllegalArgumentException("Failed to parse X.509 certificate PEM", e);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 2) + "..";
    }
}
