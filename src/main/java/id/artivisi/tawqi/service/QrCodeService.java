package id.artivisi.tawqi.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import id.artivisi.tawqi.config.TawqiProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

    private final TawqiProperties properties;

    public QrCodeService(TawqiProperties properties) {
        this.properties = properties;
    }

    public byte[] generateQrCode(String documentCode, int width, int height) {
        BufferedImage image = generateQrCodeImage(documentCode, width, height);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode QR code image to PNG", e);
        }
    }

    public BufferedImage generateQrCodeImage(String documentCode, int width, int height) {
        String url = properties.getSigning().getQrBaseUrl() + documentCode;
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, width, height);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to generate QR code for document: " + documentCode, e);
        }
    }
}
